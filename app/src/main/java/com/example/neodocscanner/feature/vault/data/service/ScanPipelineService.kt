package com.example.neodocscanner.feature.vault.data.service

import android.net.Uri
import android.util.Log
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentType
import com.example.neodocscanner.core.domain.model.ProcessingStatus
import com.example.neodocscanner.core.domain.model.ScanProcessingPhase
import com.example.neodocscanner.core.domain.model.SectionRoutingConflict
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import com.example.neodocscanner.core.domain.repository.SectionRepository
import com.example.neodocscanner.feature.vault.data.service.grouping.SmartGroupingService
import com.example.neodocscanner.feature.vault.data.service.masking.AadhaarMaskingService
import com.example.neodocscanner.feature.vault.data.service.ml.MLClassificationService
import com.example.neodocscanner.feature.vault.data.service.ocr.OCRTextSanitizer
import com.example.neodocscanner.feature.vault.data.service.ocr.OcrService
import com.example.neodocscanner.feature.vault.data.service.routing.SectionRoutingService
import com.example.neodocscanner.feature.vault.data.service.scanner.DocumentFileManager
import com.example.neodocscanner.feature.vault.data.service.text.DocumentFieldExtractorService
import com.example.neodocscanner.feature.vault.data.service.text.DocumentNamingService
import com.example.neodocscanner.feature.vault.domain.buildPassportGroupName
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full scan-to-storage pipeline for a batch of scanned images.
 *
 * iOS equivalent: DocuVaultViewModel processDocumentsSerially() + runPhase3()
 *
 * ── Pipeline order (MUST match iOS exactly) ──────────────────────────────
 * Phase 1  Save all images + insert QUEUED Document records
 * Phase 2  Per-document (serial):
 *   2a  ML classify (TFLite) → get documentClass + aadhaarSide from rawLabel
 *   2b  If AADHAAR → mask UID + compute SHA-256 hash (BEFORE OCR, same as iOS)
 *   2c  OCR (ML Kit Text Recognition)
 *   2d  Sanitise OCR text (OCRTextSanitizer — same 3-pass pipeline as iOS)
 *   2e  Extract fields (DocumentFieldExtractorService — same patterns as iOS)
 *   2f  Smart naming (DocumentNamingService — same format as iOS SmartNamingService)
 *   2g  Generate thumbnail
 *   2h  Update status → COMPLETE
 * Phase 3  Group + Route:
 *   3a  Aadhaar grouping (AadhaarGroupingService: P1 UID hash, P2 OCR name, P3 proximity)
 *   3b  Passport grouping (PassportGroupingService: P1 exact, P2 fuzzy, P3 proximity)
 *   3c  Section routing (SectionRoutingService: P1 required, P2 any, P3 even full)
 */
@Singleton
class ScanPipelineService @Inject constructor(
    private val fileManager:   DocumentFileManager,
    private val classifier:    MLClassificationService,
    private val maskingService: AadhaarMaskingService,
    private val ocrService:    OcrService,
    private val ocrSanitizer:  OCRTextSanitizer,
    private val fieldExtractor: DocumentFieldExtractorService,
    private val namingService:  DocumentNamingService,
    private val groupingService: SmartGroupingService,
    private val routingService:  SectionRoutingService,
    private val documentRepository: DocumentRepository,
    private val sectionRepository:  SectionRepository,
    private val gson: Gson
) {

    companion object { private const val TAG = "ScanPipelineService" }
    private val nonAlphaNum = Regex("[^A-Za-z0-9]")

    suspend fun process(
        imageUris:     List<Uri>,
        instanceId:    String,
        preferredSectionId: String? = null,
        onPhaseUpdate: (ScanProcessingPhase) -> Unit
    ): List<SectionRoutingConflict> = withContext(Dispatchers.IO) {

        val sessionId     = UUID.randomUUID().toString()
        val processedDocs = mutableListOf<Document>()

        // ── Phase 1: Save ──────────────────────────────────────────────────────
        onPhaseUpdate(ScanProcessingPhase.Saving(0, imageUris.size))
        val savedDocuments = mutableListOf<Document>()

        imageUris.forEachIndexed { index, uri ->
            onPhaseUpdate(ScanProcessingPhase.Saving(index, imageUris.size))

            val docId        = UUID.randomUUID().toString()
            val relativePath = fileManager.saveScannedImage(uri, instanceId, docId)

            if (relativePath == null) {
                Log.e(TAG, "Failed to save image at index $index — skipping")
                return@forEachIndexed
            }

            val doc = Document(
                id                    = docId,
                fileName              = "Scan_${index + 1}",
                relativePath          = relativePath,
                applicationInstanceId = instanceId,
                scanSessionId         = sessionId,
                pageIndex             = index,
                processingStatusRaw   = ProcessingStatus.QUEUED.rawValue,
                typeRawValue          = DocumentType.IMAGE.rawValue
            )
            documentRepository.insert(doc)
            savedDocuments.add(doc)
        }
        onPhaseUpdate(ScanProcessingPhase.Saving(imageUris.size, imageUris.size))

        // ── Phase 2: Analyse each document (serial) ───────────────────────────
        savedDocuments.forEachIndexed { index, doc ->
            onPhaseUpdate(ScanProcessingPhase.Analysing(index, savedDocuments.size))
            documentRepository.updateProcessingStatus(doc.id, ProcessingStatus.ANALYSING.rawValue)

            val analysed = analyseDocument(doc, instanceId)
            processedDocs.add(analysed)
        }
        onPhaseUpdate(ScanProcessingPhase.Analysing(savedDocuments.size, savedDocuments.size))

        // ── Phase 3: Group + Route ─────────────────────────────────────────────
        onPhaseUpdate(ScanProcessingPhase.Routing)
        applyGrouping(processedDocs, instanceId)
        val conflicts = applyRouting(
            documents = processedDocs,
            instanceId = instanceId,
            preferredSectionId = preferredSectionId
        )

        onPhaseUpdate(ScanProcessingPhase.Done(processedDocs.size))
        conflicts
    }

    // ── Per-document analysis pipeline ────────────────────────────────────────

    private suspend fun analyseDocument(doc: Document, instanceId: String): Document {
        val bitmap = fileManager.loadBitmap(doc.relativePath) ?: run {
            Log.e(TAG, "Cannot load bitmap for ${doc.id}")
            documentRepository.updateProcessingStatus(doc.id, ProcessingStatus.COMPLETE.rawValue)
            return doc
        }

        // ── Step 2a: ML Classification ────────────────────────────────────────
        // iOS: DocumentClassifierService.classify(image:) — rawLabel suffix → aadhaarSide
        val classResult = withContext(Dispatchers.Default) { classifier.classify(bitmap) }

        documentRepository.updateClassification(
            id          = doc.id,
            classRaw    = classResult.documentClass.displayName,
            aadhaarSide = classResult.aadhaarSide
        )

        // ── Step 2b: Aadhaar masking BEFORE OCR (same order as iOS) ──────────
        // iOS: if doc.documentClass == .aadhaar → AadhaarMaskingService.mask(fileURL:) first
        var maskedPath: String? = null
        var uidHash:    String? = null

        if (classResult.documentClass == DocumentClass.AADHAAR) {
            // Mask with empty regions for now; after OCR we'll have text-based UID detection
            // In iOS this uses Vision character boxes; Android uses the regex on OCR output
            // We defer actual masking to after OCR so we can use text regions
        }

        // ── Step 2c: OCR ──────────────────────────────────────────────────────
        val ocrResult   = ocrService.recognise(bitmap)
        val regionsJson = if (ocrResult.regions.isNotEmpty()) gson.toJson(ocrResult.regions) else null

        // ── Step 2d: Sanitise OCR text (iOS: OCRTextSanitizer.sanitize) ──────
        val sanitisedText = ocrSanitizer.sanitize(ocrResult.fullText)

        documentRepository.updateOcr(
            id          = doc.id,
            text        = sanitisedText.takeIf { it.isNotBlank() },
            processed   = true,
            regionsJson = regionsJson
        )

        // ── Step 2b (deferred): Aadhaar masking using OCR regions ────────────
        if (classResult.documentClass == DocumentClass.AADHAAR) {
            val maskResult = maskingService.maskAndHash(
                originalRelativePath = doc.relativePath,
                regions              = ocrResult.regions,
                instanceId           = instanceId,
                documentId           = doc.id
            )
            maskedPath = maskResult.maskedRelativePath
            uidHash    = maskResult.uidHash
        }

        // ── Step 2e: Field extraction (iOS: DocumentFieldExtractorService) ────
        val fields = withContext(Dispatchers.Default) {
            fieldExtractor.extract(classResult.documentClass, sanitisedText)
        }
        val fieldsJson = if (fields.isNotEmpty()) gson.toJson(fields) else null
        documentRepository.updateExtractedFields(doc.id, fieldsJson)

        // iOS parity: detect passport page side during analysis phase, before
        // grouping. This improves same-batch and cross-batch auto-pairing.
        val detectedPassportSide = if (classResult.documentClass == DocumentClass.PASSPORT) {
            detectPassportSide(sanitisedText, fieldsJson)
        } else {
            null
        }
        val detectedPassportNumber = if (classResult.documentClass == DocumentClass.PASSPORT) {
            detectPassportNumber(fieldsJson, sanitisedText)
        } else {
            null
        }

        // ── Step 2f: Smart naming (iOS: SmartNamingService.generateBaseName) ──
        val docName = namingService.name(
            documentClass = classResult.documentClass,
            fields        = fields
        )

        // ── Step 2g: Thumbnail ────────────────────────────────────────────────
        val thumbPath = fileManager.generateThumbnail(instanceId, doc.id)
        bitmap.recycle()

        // ── Step 2h: Persist + COMPLETE ───────────────────────────────────────
        documentRepository.updateFilePaths(
            id                 = doc.id,
            fileName           = docName,
            relativePath       = doc.relativePath,
            maskedRelativePath = maskedPath,
            thumbRelativePath  = thumbPath
        )
        if (maskedPath != null || uidHash != null) {
            documentRepository.updateMasking(doc.id, maskedPath, uidHash, thumbPath)
        }
        if (detectedPassportSide != null) {
            // Reuse grouping updater to persist passport_side even before a group exists.
            documentRepository.updateGrouping(
                id = doc.id,
                groupId = doc.groupId,
                groupName = doc.groupName,
                groupPageIndex = doc.groupPageIndex,
                aadhaarSide = doc.aadhaarSide,
                passportSide = detectedPassportSide
            )
        }
        if (classResult.documentClass == DocumentClass.PASSPORT) {
            tryAutoPairPassportByNumber(
                currentDocId = doc.id,
                instanceId = instanceId,
                currentPassportNumber = detectedPassportNumber,
                currentDetectedSide = detectedPassportSide
            )
        }
        documentRepository.updateProcessingStatus(doc.id, ProcessingStatus.COMPLETE.rawValue)

        return doc.copy(
            fileName              = docName,
            documentClassRaw      = classResult.documentClass.displayName,
            aadhaarSide           = classResult.aadhaarSide,
            aadhaarUidHash        = uidHash,
            passportSide          = detectedPassportSide,
            maskedRelativePath    = maskedPath,
            thumbnailRelativePath = thumbPath,
            processingStatusRaw   = ProcessingStatus.COMPLETE.rawValue,
            extractedText         = sanitisedText.ifBlank { null },
            extractedFields       = fieldsJson
        )
    }

    private suspend fun tryAutoPairPassportByNumber(
        currentDocId: String,
        instanceId: String,
        currentPassportNumber: String?,
        currentDetectedSide: String?
    ) {
        val normalizedCurrent = normalizePassportNumber(currentPassportNumber) ?: return

        val docs = documentRepository.getByInstanceOnce(instanceId)
            .filter {
                it.id != currentDocId &&
                        it.documentClass == DocumentClass.PASSPORT &&
                        it.groupId == null
            }

        val candidate = docs.firstOrNull { candidate ->
            val candidateNum = detectPassportNumber(candidate.extractedFields, candidate.extractedText)
            normalizePassportNumber(candidateNum) == normalizedCurrent
        } ?: return

        val groupId = UUID.randomUUID().toString()
        val candidateSide = canonicalPassportSide(candidate.passportSide)
            ?: detectPassportSide(candidate.extractedText.orEmpty(), candidate.extractedFields)

        val (dataPageId, addressPageId) = when {
            canonicalPassportSide(currentDetectedSide) == "address" -> candidate.id to currentDocId
            candidateSide == "address" -> currentDocId to candidate.id
            candidateSide == "data" -> candidate.id to currentDocId
            else -> currentDocId to candidate.id
        }
        val currentDoc = documentRepository.getById(currentDocId)
        val groupName = buildPassportGroupName(
            dataPage = if (dataPageId == currentDocId) currentDoc else candidate,
            addressPage = if (addressPageId == currentDocId) currentDoc else candidate
        )

        documentRepository.updateGrouping(
            id = dataPageId,
            groupId = groupId,
            groupName = groupName,
            groupPageIndex = 0,
            aadhaarSide = null,
            passportSide = "data"
        )
        documentRepository.updateGrouping(
            id = addressPageId,
            groupId = groupId,
            groupName = groupName,
            groupPageIndex = 1,
            aadhaarSide = null,
            passportSide = "address"
        )
    }

    private fun detectPassportNumber(fieldsJson: String?, text: String?): String? {
        val fromFields = try {
            if (fieldsJson.isNullOrBlank()) null
            else {
                val fields = gson.fromJson(fieldsJson, Array<com.example.neodocscanner.core.domain.model.DocumentField>::class.java)
                fields?.firstOrNull { field ->
                    val label = field.label.lowercase()
                    label.contains("passport number") || label.contains("passport no")
                }?.value
            }
        } catch (_: Exception) {
            null
        }
        if (!fromFields.isNullOrBlank()) {
            val cleaned = fromFields.uppercase().replace(Regex("[^A-Z0-9]"), "")
            Regex("""[A-Z]\d{7}""").find(cleaned)?.value?.let { return it }
        }
        if (text.isNullOrBlank()) return null
        // iOS parity: passport number detection uses [A-Z]\\d{7}
        // and skips MRZ-like lines.
        val regex = Regex("""\b([A-Z])\s*[-]?\s*(\d{7})\b""")
        val lines = text.lines()
        for (line in lines) {
            val stripped = line.trim()
            val mrzRatio = stripped.count { it == '<' }.toDouble() / (stripped.length.coerceAtLeast(1).toDouble())
            if (stripped.length >= 30 && mrzRatio > 0.20) continue
            val m = regex.find(stripped.uppercase()) ?: continue
            return m.groupValues[1] + m.groupValues[2]
        }
        return null
    }

    private fun normalizePassportNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.uppercase().replace(nonAlphaNum, "")
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun canonicalPassportSide(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "data", "front", "f", "mrz" -> "data"
            "address", "back", "b", "addr" -> "address"
            else -> null
        }
    }

    private fun detectPassportSide(text: String, fieldsJson: String? = null): String? {
        val labels = try {
            if (fieldsJson.isNullOrBlank()) emptyList()
            else gson.fromJson(fieldsJson, Array<com.example.neodocscanner.core.domain.model.DocumentField>::class.java)
                ?.map { it.label.lowercase() }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val hasDataFields = labels.any {
            it.contains("passport no") ||
                it.contains("passport number") ||
                it.contains("date of birth") ||
                it.contains("date of expiry") ||
                it.contains("country code") ||
                it.contains("nationality")
        }
        val hasAddressFields = labels.any {
            it.contains("father") ||
                it.contains("guardian") ||
                it.contains("mother") ||
                it.contains("spouse") ||
                it.contains("file no") ||
                it.contains("address")
        }
        if (hasDataFields && !hasAddressFields) return "data"
        if (hasAddressFields && !hasDataFields) return "address"

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val mrzRegex = Regex("[A-Z]\\d{7}[0-9<]\\d[A-Z]{3}\\d{6}\\d[MF<]\\d{6}")

        val hasMrz = lines.any { line ->
            val stripped = line.replace(" ", "")
            if (stripped.isEmpty()) return@any false
            val mrzRatio = stripped.count { it.isLetterOrDigit() || it == '<' }.toDouble() / stripped.length
            (mrzRatio >= 0.80 && mrzRegex.containsMatchIn(stripped)) || stripped.contains("<<")
        }
        if (hasMrz) return "data"

        val lower = text.lowercase()
        if (
            lower.contains("father") ||
            lower.contains("guardian") ||
            lower.contains("mother") ||
            lower.contains("spouse") ||
            lower.contains("file no") ||
            lower.contains("address") ||
            lower.contains("flia")
        ) return "address"

        return null
    }

    // ── Grouping ──────────────────────────────────────────────────────────────

    private suspend fun applyGrouping(documents: List<Document>, instanceId: String) {
        // iOS parity: grouping must run on the latest persisted vault state.
        // Using repository snapshot avoids stale in-memory docs overriding
        // freshly updated fields/grouping flags within the same scan batch.
        val allForGrouping = documentRepository.getByInstanceOnce(instanceId)
            .filter { !it.isArchivedByExport }
            .toList()

        val updates = withContext(Dispatchers.Default) {
            groupingService.group(allForGrouping)
        }
        for (update in updates) {
            documentRepository.updateGrouping(
                id             = update.documentId,
                groupId        = update.groupId,
                groupName      = update.groupName,
                groupPageIndex = update.groupPageIndex,
                aadhaarSide    = update.aadhaarSide,
                passportSide   = update.passportSide
            )
        }
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    private suspend fun applyRouting(
        documents: List<Document>,
        instanceId: String,
        preferredSectionId: String?
    ): List<SectionRoutingConflict> {
        val sections = sectionRepository.getByInstanceOnce(instanceId)
        val preferredSection = preferredSectionId?.let { preferredId ->
            sections.firstOrNull { it.id == preferredId }
        }
        val conflicts = mutableListOf<SectionRoutingConflict>()
        // Build per-section document counts from the full instance document list
        val allDocs = documentRepository.getByInstanceOnce(instanceId)
        val existingCounts = sections.associate { s ->
            s.id to allDocs.count { doc -> doc.sectionId == s.id }
        }

        val routingResults = withContext(Dispatchers.Default) {
            routingService.route(documents, sections, existingCounts)
        }
        for (result in routingResults) {
            val document = documents.firstOrNull { it.id == result.documentId }
            val resolvedSectionId = when {
                preferredSection != null && document != null -> {
                    val isUnknown = document.documentClass == DocumentClass.OTHER || result.sectionId == null
                    val isSameCategory = result.sectionId == preferredSection.id

                    when {
                        // Requested behavior: unknown/other remains in scanned category.
                        isUnknown -> preferredSection.id
                        // Requested behavior: same classified category remains in scanned category.
                        isSameCategory -> preferredSection.id
                        // Different classified category: keep in scanned category first,
                        // then ask user via conflict modal.
                        else -> {
                            val mlSection = sections.firstOrNull { it.id == result.sectionId }
                            if (mlSection != null) {
                                conflicts += SectionRoutingConflict(
                                    documentId = document.id,
                                    detectedLabel = document.documentClass.displayName,
                                    hintSectionId = preferredSection.id,
                                    hintSectionTitle = preferredSection.title,
                                    mlSectionId = mlSection.id,
                                    mlSectionTitle = mlSection.title
                                )
                            }
                            preferredSection.id
                        }
                    }
                }
                else -> result.sectionId
            }
            documentRepository.updateSectionId(result.documentId, resolvedSectionId)
        }
        return conflicts
    }
}
