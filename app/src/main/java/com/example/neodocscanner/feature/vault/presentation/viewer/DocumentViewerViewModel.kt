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
import com.example.neodocscanner.feature.vault.data.service.routing.SectionRoutingService
import com.example.neodocscanner.feature.vault.data.service.scanner.DocumentFileManager
import com.example.neodocscanner.feature.vault.data.service.text.DocumentFieldExtractorService
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
    val showRemoveFromGroupDialog: Boolean = false
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
    private val routingService: SectionRoutingService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle[Screen.DocumentViewer.ARG_DOCUMENT_ID])
    private val gson = Gson()

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    init {
        loadDocument()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private fun loadDocument() {
        viewModelScope.launch {
            val doc = documentRepository.getById(documentId) ?: return@launch
            _state.update { it.copy(document = doc, isLoading = false) }

            // Load group members if doc belongs to a group
            if (doc.groupId != null) {
                val allDocs = documentRepository.getByInstanceOnce(doc.applicationInstanceId ?: "")
                val groupMembers = allDocs.filter { it.groupId == doc.groupId && it.id != doc.id }
                _state.update { it.copy(groupPages = groupMembers) }
            }

            // Auto-show OCR highlights if already processed
            if (doc.isOcrProcessed && doc.decodedRegions.isNotEmpty()) {
                _state.update { it.copy(showHighlights = true) }
            }

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

    fun routeToSection(sectionId: String) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            documentRepository.updateSectionId(doc.id, sectionId)
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
}
