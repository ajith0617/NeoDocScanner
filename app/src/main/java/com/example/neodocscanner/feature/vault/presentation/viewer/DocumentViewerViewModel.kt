package com.example.neodocscanner.feature.vault.presentation.viewer

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentField
import com.example.neodocscanner.core.domain.model.TextRegion
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import com.example.neodocscanner.core.domain.repository.SectionRepository
import com.example.neodocscanner.feature.vault.data.service.ocr.OcrService
import com.example.neodocscanner.feature.vault.data.service.ocr.OCRTextSanitizer
import com.example.neodocscanner.feature.vault.data.service.masking.AadhaarMaskingService
import com.example.neodocscanner.feature.vault.data.service.masking.PanMaskingService
import com.example.neodocscanner.feature.vault.data.service.routing.SectionRoutingService
import com.example.neodocscanner.feature.vault.data.service.scanner.DocumentFileManager
import com.example.neodocscanner.feature.vault.data.service.text.DocumentFieldExtractorService
import com.example.neodocscanner.feature.vault.domain.syncDocumentClassForSectionMove
import com.example.neodocscanner.feature.vault.domain.buildAadhaarGroupName
import com.example.neodocscanner.feature.vault.domain.buildPassportGroupName
import com.example.neodocscanner.feature.vault.data.service.text.DocumentNamingService
import com.example.neodocscanner.navigation.Screen
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class ViewerUiState(
    val document: Document? = null,
    val groupPages: List<Document> = emptyList(),   // other pages in same group
    val currentPageIndex: Int = 0,
    val imageBitmaps: Map<String, Bitmap> = emptyMap(),
    val showingMasked: Boolean = true,
    val showHighlights: Boolean = false,
    val isOcrRunning: Boolean = false,
    val ocrError: String? = null,
    val isLoading: Boolean = true,
    // Detail sheet state
    val showDetailSheet: Boolean = false,
    // Move-to-category sheet
    val showMoveSheet: Boolean = false,
    // Remove-from-group dialog
    val showRemoveFromGroupDialog: Boolean = false,
    // Group reorder preview flag (requires explicit Save)
    val hasPendingReorder: Boolean = false
) {
    /** The document currently displayed in the viewer. */
    val activeDocument: Document?
        get() = if (groupPages.isEmpty()) document
        else allPages.getOrNull(currentPageIndex) ?: document

    /** Ordered page list matching iOS allPages logic. */
    val allPages: List<Document>
        get() {
            val all = listOfNotNull(document) + groupPages
            // Sort by groupPageIndex when available (set at pairing time)
            return if (all.any { it.groupPageIndex != null }) {
                all.sortedBy { it.groupPageIndex ?: Int.MAX_VALUE }
            } else {
                // Aadhaar front before back
                val fronts = all.filter { it.aadhaarSide == "front" }
                val backs  = all.filter { it.aadhaarSide == "back" }
                val others = all.filter { it.aadhaarSide == null }
                fronts + backs + others
            }
        }

    val isGrouped: Boolean get() = groupPages.isNotEmpty()
    val isUncategorised: Boolean get() = document?.sectionId == null
    val pageCount: Int get() = if (isGrouped) allPages.size else 1

    fun pageLabel(index: Int): String {
        val doc = allPages.getOrNull(index) ?: return ""
        return when (doc.aadhaarSide) {
            "front" -> "Front"
            "back"  -> "Back"
            else    -> "Doc ${index + 1}"
        }
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DocumentViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val sectionRepository: SectionRepository,
    private val ocrService: OcrService,
    private val ocrSanitizer: OCRTextSanitizer,
    private val fieldExtractor: DocumentFieldExtractorService,
    private val namingService: DocumentNamingService,
    private val fileManager: DocumentFileManager,
    private val aadhaarMaskingService: AadhaarMaskingService,
    private val panMaskingService: PanMaskingService,
    private val routingService: SectionRoutingService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle[Screen.DocumentViewer.ARG_DOCUMENT_ID])
    private val gson = Gson()
    private var persistedGroupOrderIds: List<String> = emptyList()

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    init {
        loadDocument()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private fun loadDocument() {
        viewModelScope.launch {
            val doc = documentRepository.getById(documentId) ?: return@launch
            _state.update { it.copy(document = doc, isLoading = false, hasPendingReorder = false) }

            // Load group members if doc belongs to a group
            if (doc.groupId != null) {
                val allDocs = documentRepository.getByInstanceOnce(doc.applicationInstanceId ?: "")
                val groupMembers = allDocs.filter { it.groupId == doc.groupId && it.id != doc.id }
                _state.update { it.copy(groupPages = groupMembers) }
                persistedGroupOrderIds = _state.value.allPages.map { it.id }
            } else {
                persistedGroupOrderIds = emptyList()
            }

            // Auto-show OCR highlights on first open (disabled for now).
            // if (doc.isOcrProcessed && doc.decodedRegions.isNotEmpty()) {
            //     _state.update { it.copy(showHighlights = true) }
            // }

            loadImages()
        }
    }

    private fun loadImages() {
        viewModelScope.launch {
            val doc = _state.value.document ?: return@launch
            val allPages = _state.value.allPages.ifEmpty { listOf(doc) }
            val bitmaps = mutableMapOf<String, Bitmap>()

            for (page in allPages) {
                val path = if (_state.value.showingMasked && page.maskedRelativePath != null) {
                    page.maskedRelativePath
                } else {
                    page.relativePath
                }
                fileManager.loadBitmap(path)?.let { bitmaps[page.id] = it }
            }
            _state.update { it.copy(imageBitmaps = bitmaps) }
        }
    }

    // ── Page navigation ───────────────────────────────────────────────────────

    fun goToPage(index: Int) {
        val clamped = index.coerceIn(0, _state.value.pageCount - 1)
        _state.update { it.copy(currentPageIndex = clamped, showHighlights = false) }
    }

    fun goToPageByDocumentId(documentId: String) {
        val idx = _state.value.allPages.indexOfFirst { it.id == documentId }
        if (idx >= 0) {
            goToPage(idx)
        }
    }

    fun previewGroupReorder(orderedIds: List<String>) {
        val current = _state.value
        if (!current.isGrouped) return
        if (orderedIds.isEmpty()) return

        val existing = current.allPages
        val existingIds = existing.map { it.id }
        if (orderedIds.toSet() != existingIds.toSet()) return

        val currentActiveId = current.activeDocument?.id
        val byId = existing.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
        if (reordered.size != existing.size) return

        val withUpdatedIndexes = reordered.mapIndexed { idx, doc ->
            doc.copy(
                pageIndex = idx,
                groupPageIndex = idx
            )
        }

        val rootId = current.document?.id
        val updatedRoot = withUpdatedIndexes.firstOrNull { it.id == rootId } ?: current.document
        val updatedGroup = withUpdatedIndexes.filterNot { it.id == rootId }
        val newPageIndex = currentActiveId
            ?.let { activeId -> withUpdatedIndexes.indexOfFirst { it.id == activeId } }
            ?.takeIf { it >= 0 }
            ?: 0

        _state.update {
            it.copy(
                document = updatedRoot,
                groupPages = updatedGroup,
                currentPageIndex = newPageIndex,
                hasPendingReorder = withUpdatedIndexes.map { d -> d.id } != persistedGroupOrderIds
            )
        }
    }

    fun saveGroupReorder() {
        val current = _state.value
        if (!current.isGrouped || !current.hasPendingReorder) return

        val orderedPages = current.allPages
        viewModelScope.launch {
            orderedPages.forEachIndexed { idx, doc ->
                documentRepository.updatePageIndex(doc.id, idx)
                documentRepository.updateGrouping(
                    id = doc.id,
                    groupId = doc.groupId,
                    groupName = doc.groupName,
                    groupPageIndex = idx,
                    aadhaarSide = doc.aadhaarSide,
                    passportSide = doc.passportSide
                )
            }
            persistedGroupOrderIds = orderedPages.map { it.id }
            _state.update { it.copy(hasPendingReorder = false) }
        }
    }

    // ── OCR toggle ────────────────────────────────────────────────────────────

    fun toggleHighlights() {
        _state.update { it.copy(showHighlights = !it.showHighlights) }
    }

    // ── OCR extraction (matches iOS extractText(for:)) ────────────────────────

    fun extractOcr() {
        val doc = _state.value.activeDocument ?: return
        if (_state.value.isOcrRunning) return

        viewModelScope.launch {
            _state.update { it.copy(isOcrRunning = true, ocrError = null) }
            try {
                val bitmap = fileManager.loadBitmap(
                    if (_state.value.showingMasked && doc.maskedRelativePath != null)
                        doc.maskedRelativePath!! else doc.relativePath
                ) ?: throw Exception("Could not load image")

                val result = ocrService.recognise(bitmap)
                val sanitized = ocrSanitizer.sanitize(result.fullText)
                    .ifBlank { "(No text found)" }
                val regionsJson = gson.toJson(result.regions)

                documentRepository.updateOcr(doc.id, sanitized, true, regionsJson)

                // Re-run field extraction now we have text
                val partner = _state.value.allPages.firstOrNull { it.id != doc.id }
                val extraction = fieldExtractor.extract(
                    documentClass = doc.documentClass,
                    text          = sanitized,
                    backText      = partner?.extractedText ?: ""
                )
                if (extraction.isNotEmpty()) {
                    documentRepository.updateExtractedFields(doc.id, gson.toJson(extraction))
                }

                // Refresh doc in state + re-enable highlights
                val updated = documentRepository.getById(doc.id)
                _state.update { s ->
                    val newGroupPages = if (s.isGrouped) {
                        s.groupPages.map { if (it.id == updated?.id) updated else it }
                    } else s.groupPages
                    val newDoc = if (s.document?.id == updated?.id) updated else s.document
                    s.copy(
                        document      = newDoc,
                        groupPages    = newGroupPages,
                        isOcrRunning  = false,
                        showHighlights = true
                    )
                }
                loadImages()
            } catch (e: Exception) {
                _state.update { it.copy(isOcrRunning = false, ocrError = e.message ?: "OCR failed") }
            }
        }
    }

    // ── Move to category ──────────────────────────────────────────────────────

    fun showMoveSheet()   { _state.update { it.copy(showMoveSheet = true) } }
    fun dismissMoveSheet(){ _state.update { it.copy(showMoveSheet = false) } }

    fun routeToSection(sectionId: String?) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            val instanceId = doc.applicationInstanceId
            val sections = instanceId?.let { sectionRepository.getByInstanceOnce(it) }.orEmpty()
            if (sections.isNotEmpty()) {
                documentRepository.syncDocumentClassForSectionMove(doc, sectionId, sections)
            }
            val reloaded = documentRepository.getById(doc.id)
            val text = reloaded?.extractedText?.takeIf { it.isNotBlank() }
            if (reloaded != null && text != null) {
                val allDocs = documentRepository.getByInstanceOnce(reloaded.applicationInstanceId ?: "")
                val backText = if (reloaded.documentClass == DocumentClass.PASSPORT && reloaded.groupId != null) {
                    allDocs.firstOrNull { it.groupId == reloaded.groupId && it.id != reloaded.id }
                        ?.extractedText
                        .orEmpty()
                } else ""
                val extraction = fieldExtractor.extract(
                    documentClass = reloaded.documentClass,
                    text = text,
                    backText = backText
                )
                documentRepository.updateExtractedFields(reloaded.id, gson.toJson(extraction))
            }
            documentRepository.updateSectionId(doc.id, sectionId)
            rerunMaskingAfterMove(doc.id)
            tryAutoPairAadhaarAfterMove(doc.id, doc.applicationInstanceId)
            tryAutoPairPassportAfterMove(doc.id, doc.applicationInstanceId)
            val updated = documentRepository.getById(doc.id)
            _state.update { it.copy(document = updated, showMoveSheet = false) }
        }
    }

    // ── Remove from group ─────────────────────────────────────────────────────

    fun showRemoveFromGroupDialog()    { _state.update { it.copy(showRemoveFromGroupDialog = true) } }
    fun dismissRemoveFromGroupDialog() { _state.update { it.copy(showRemoveFromGroupDialog = false) } }

    fun removeFromGroup(sendToUncategorised: Boolean) {
        val doc = _state.value.activeDocument ?: return
        viewModelScope.launch {
            val newSectionId = if (sendToUncategorised) null else doc.sectionId
            val allGroupMembers = (_state.value.allPages)

            // Remove group from this doc
            documentRepository.updateGrouping(
                id             = doc.id,
                groupId        = null,
                groupName      = null,
                groupPageIndex = null,
                aadhaarSide    = null,
                passportSide   = null
            )
            documentRepository.updateSectionId(doc.id, newSectionId)

            // If only one member remains dissolve the group entirely
            val remaining = allGroupMembers.filter { it.id != doc.id }
            if (remaining.size == 1) {
                val last = remaining.first()
                documentRepository.updateGrouping(
                    id             = last.id,
                    groupId        = null,
                    groupName      = null,
                    groupPageIndex = null,
                    aadhaarSide    = null,
                    passportSide   = null
                )
            }

            _state.update { it.copy(showRemoveFromGroupDialog = false) }
        }
    }

    // ── Detail sheet ──────────────────────────────────────────────────────────

    fun showDetailSheet()    { _state.update { it.copy(showDetailSheet = true) } }
    fun dismissDetailSheet() { _state.update { it.copy(showDetailSheet = false) } }

    // ── Refresh (called after detail sheet closes) ────────────────────────────

    fun refreshActiveDocument() {
        val doc = _state.value.activeDocument ?: return
        viewModelScope.launch {
            val updated = documentRepository.getById(doc.id) ?: return@launch
            _state.update { s ->
                if (s.document?.id == updated.id) s.copy(document = updated)
                else {
                    val newGroup = s.groupPages.map { if (it.id == updated.id) updated else it }
                    s.copy(groupPages = newGroup)
                }
            }
        }
    }

    private suspend fun tryAutoPairPassportAfterMove(documentId: String, instanceId: String?) {
        if (instanceId.isNullOrBlank()) return
        val moved = documentRepository.getById(documentId) ?: return
        if (moved.documentClass != DocumentClass.PASSPORT || moved.groupId != null) return

        val movedNumber = detectPassportNumber(moved)
        val normalizedMoved = normalizePassportNumber(movedNumber) ?: return

        val candidate = documentRepository.getByInstanceOnce(instanceId)
            .asSequence()
            .filter {
                it.id != moved.id &&
                    it.documentClass == DocumentClass.PASSPORT &&
                    it.groupId == null
            }
            .firstOrNull { normalizePassportNumber(detectPassportNumber(it)) == normalizedMoved }
            ?: return

        val movedSide = canonicalPassportSide(moved.passportSide)
        val candidateSide = canonicalPassportSide(candidate.passportSide)
        val (dataPageId, addressPageId) = when {
            movedSide == "address" -> candidate.id to moved.id
            candidateSide == "address" -> moved.id to candidate.id
            candidateSide == "data" -> candidate.id to moved.id
            else -> moved.id to candidate.id
        }

        val gid = java.util.UUID.randomUUID().toString()
        val groupName = buildPassportGroupName(
            dataPage = if (dataPageId == moved.id) moved else candidate,
            addressPage = if (addressPageId == moved.id) moved else candidate
        )
        documentRepository.updateGrouping(dataPageId, gid, groupName, 0, null, "data")
        documentRepository.updateGrouping(addressPageId, gid, groupName, 1, null, "address")
    }

    private fun detectPassportNumber(doc: Document): String? {
        val fromFields = doc.decodedFields.firstOrNull { field ->
            val label = field.label.lowercase()
            label.contains("passport number") || label.contains("passport no")
        }?.value
        if (!fromFields.isNullOrBlank()) {
            val cleaned = fromFields.uppercase().replace(Regex("[^A-Z0-9]"), "")
            Regex("""[A-Z]\d{7}""").find(cleaned)?.value?.let { return it }
        }
        val text = doc.extractedText ?: return null
        val regex = Regex("""\b([A-Z])\s*[-]?\s*(\d{7})\b""")
        for (line in text.lines()) {
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
        val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun canonicalPassportSide(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "data", "front", "f", "mrz" -> "data"
            "address", "back", "b", "addr" -> "address"
            else -> null
        }
    }

    private suspend fun rerunMaskingAfterMove(documentId: String) {
        val moved = documentRepository.getById(documentId) ?: return
        val instanceId = moved.applicationInstanceId ?: return
        val regions = moved.decodedRegions
        if (regions.isEmpty()) return
        when (moved.documentClass) {
            DocumentClass.AADHAAR -> {
                val maskResult = aadhaarMaskingService.maskAndHash(
                    originalRelativePath = moved.relativePath,
                    regions = regions,
                    instanceId = instanceId,
                    documentId = moved.id
                )
                if (maskResult.maskedRelativePath != null || maskResult.uidHash != null) {
                    documentRepository.updateMasking(
                        moved.id,
                        maskResult.maskedRelativePath,
                        maskResult.uidHash,
                        moved.thumbnailRelativePath
                    )
                }
            }
            DocumentClass.PAN -> {
                val maskedPath = panMaskingService.maskAndSave(
                    originalRelativePath = moved.relativePath,
                    regions = regions,
                    instanceId = instanceId,
                    documentId = moved.id
                )
                if (maskedPath != null) {
                    documentRepository.updateMasking(
                        moved.id,
                        maskedPath,
                        moved.aadhaarUidHash,
                        moved.thumbnailRelativePath
                    )
                }
            }
            else -> Unit
        }
    }

    private suspend fun tryAutoPairAadhaarAfterMove(documentId: String, instanceId: String?) {
        if (instanceId.isNullOrBlank()) return
        val moved = documentRepository.getById(documentId) ?: return
        if (moved.documentClass != DocumentClass.AADHAAR || moved.groupId != null) return

        val movedUid = detectAadhaarNumber(moved) ?: return
        val movedUidHash = sha256(movedUid)
        documentRepository.updateMasking(
            moved.id,
            moved.maskedRelativePath,
            movedUidHash,
            moved.thumbnailRelativePath
        )

        val movedSide = canonicalAadhaarSide(moved.aadhaarSide) ?: inferAadhaarSide(moved)
        if (movedSide != null) {
            documentRepository.updateClassification(moved.id, moved.documentClass.displayName, movedSide)
        }

        val candidate = documentRepository.getByInstanceOnce(instanceId)
            .asSequence()
            .filter {
                it.id != moved.id &&
                    it.documentClass == DocumentClass.AADHAAR &&
                    it.groupId == null
            }
            .firstOrNull { candidate ->
                val candidateHash = candidate.aadhaarUidHash ?: detectAadhaarNumber(candidate)?.let { sha256(it) }
                candidateHash == movedUidHash
            } ?: return

        val candidateSide = canonicalAadhaarSide(candidate.aadhaarSide) ?: inferAadhaarSide(candidate)
        val (frontDoc, backDoc) = when {
            movedSide == "back" -> candidate to moved
            candidateSide == "back" -> moved to candidate
            candidateSide == "front" -> candidate to moved
            else -> moved to candidate
        }

        val gid = java.util.UUID.randomUUID().toString()
        val groupName = buildAadhaarGroupName(frontDoc, backDoc)
        documentRepository.updateGrouping(frontDoc.id, gid, groupName, 0, "front", null)
        documentRepository.updateGrouping(backDoc.id, gid, groupName, 1, "back", null)
    }

    private fun detectAadhaarNumber(doc: Document): String? {
        val fromFields = doc.decodedFields.firstOrNull { field ->
            field.label.equals("Aadhaar Number", ignoreCase = true)
        }?.value
        val fromFieldClean = fromFields
            ?.replace(Regex("[^0-9]"), "")
            ?.takeIf { it.length == 12 }
        if (fromFieldClean != null) return fromFieldClean
        val text = doc.extractedText ?: return null
        return fieldExtractor.detectAadhaarUID(fieldExtractor.normaliseDigits(text))
    }

    private fun inferAadhaarSide(doc: Document): String? {
        val labels = doc.decodedFields.map { it.label.lowercase() }
        if (labels.any { it.contains("address") }) return "back"
        if (labels.any { it.contains("aadhaar number") || it == "name" || it.contains("date of birth") || it == "gender" }) {
            return "front"
        }
        val lower = doc.extractedText.orEmpty().lowercase()
        if (lower.contains("address")) return "back"
        if (lower.contains("dob") || lower.contains("date of birth")) return "front"
        return null
    }

    private fun canonicalAadhaarSide(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "front", "f" -> "front"
            "back", "b", "address" -> "back"
            else -> null
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
