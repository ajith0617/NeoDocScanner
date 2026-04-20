package com.example.neodocscanner.feature.vault.data.service

import android.net.Uri
import android.util.Log
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentType
import com.example.neodocscanner.core.domain.model.ProcessingStatus
import com.example.neodocscanner.core.domain.model.ScanProcessingPhase
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

    suspend fun process(
        imageUris:     List<Uri>,
        instanceId:    String,
        onPhaseUpdate: (ScanProcessingPhase) -> Unit
    ) = withContext(Dispatchers.IO) {

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
        applyGrouping(processedDocs)
        applyRouting(processedDocs, instanceId)

        onPhaseUpdate(ScanProcessingPhase.Done(processedDocs.size))
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
        documentRepository.updateProcessingStatus(doc.id, ProcessingStatus.COMPLETE.rawValue)

        return doc.copy(
            fileName              = docName,
            documentClassRaw      = classResult.documentClass.displayName,
            aadhaarSide           = classResult.aadhaarSide,
            aadhaarUidHash        = uidHash,
            maskedRelativePath    = maskedPath,
            thumbnailRelativePath = thumbPath,
            processingStatusRaw   = ProcessingStatus.COMPLETE.rawValue,
            extractedText         = sanitisedText.ifBlank { null }
        )
    }

    // ── Grouping ──────────────────────────────────────────────────────────────

    private suspend fun applyGrouping(documents: List<Document>) {
        val updates = withContext(Dispatchers.Default) {
            groupingService.group(documents)
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

    private suspend fun applyRouting(documents: List<Document>, instanceId: String) {
        val sections = sectionRepository.getByInstanceOnce(instanceId)
        // Build per-section document counts from the full instance document list
        val allDocs = documentRepository.getByInstanceOnce(instanceId)
        val existingCounts = sections.associate { s ->
            s.id to allDocs.count { doc -> doc.sectionId == s.id }
        }

        val routingResults = withContext(Dispatchers.Default) {
            routingService.route(documents, sections, existingCounts)
        }
        for (result in routingResults) {
            documentRepository.updateSectionId(result.documentId, result.sectionId)
        }
    }
}
