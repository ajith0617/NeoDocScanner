package com.example.neodocscanner.feature.vault.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neodocscanner.core.data.file.FileManagerRepository
import com.example.neodocscanner.core.domain.model.ApplicationInstance
import com.example.neodocscanner.core.domain.model.ApplicationSection
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.SectionRoutingConflict
import com.example.neodocscanner.core.domain.model.DocumentType
import com.example.neodocscanner.core.domain.model.ScanProcessingPhase
import com.example.neodocscanner.core.domain.repository.ApplicationRepository
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import com.example.neodocscanner.core.domain.repository.SectionRepository
import com.example.neodocscanner.feature.vault.data.service.ScanPipelineService
import com.example.neodocscanner.feature.vault.data.service.masking.AadhaarMaskingService
import com.example.neodocscanner.feature.vault.data.service.masking.PanMaskingService
import com.example.neodocscanner.feature.vault.data.service.text.DocumentFieldExtractorService
import com.example.neodocscanner.feature.vault.data.service.pdf.PdfExportService
import com.example.neodocscanner.feature.vault.data.service.routing.SectionRoutingService
import com.example.neodocscanner.feature.vault.domain.buildAadhaarGroupName
import com.example.neodocscanner.feature.vault.domain.buildPassportGroupName
import com.example.neodocscanner.feature.vault.domain.syncDocumentClassForSectionMove
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.security.MessageDigest
import javax.inject.Inject
import com.google.gson.Gson

private const val KEY_VAULT_GALLERY_COLUMNS = "vault_gallery_columns"

// ── UI Models ─────────────────────────────────────────────────────────────────

data class SectionWithDocs(
    val section: ApplicationSection,
    val documents: List<Document>,
    val allDocumentsInSection: List<Document>,
    val totalDocumentCount: Int
) {
    val isFull: Boolean
        get() = section.maxDocuments > 0 && totalDocumentCount >= section.maxDocuments
    val isComplete: Boolean
        get() {
            if (totalDocumentCount == 0) return false
            return section.maxDocuments == 0 || totalDocumentCount >= section.maxDocuments
        }
}

data class VaultUiState(
    val instanceId: String                       = "",
    val instanceName: String                     = "",
    val sectionsWithDocs: List<SectionWithDocs>  = emptyList(),
    val inboxDocuments: List<Document>           = emptyList(),
    val allDocuments: List<Document>             = emptyList(),
    val selectedTabIndex: Int                    = 0,
    val collapsedSectionIds: Set<String>         = emptySet(),
    val pulsingSectionIds: Set<String>           = emptySet(),
    val isLoading: Boolean                       = true,

    // Scan pipeline progress
    val scanPhase: ScanProcessingPhase           = ScanProcessingPhase.Idle,

    // ── Selection mode ────────────────────────────────────────────────────────
    val isSelectionMode: Boolean                 = false,
    val selectedDocumentIds: List<String>        = emptyList(),   // ordered list (for group numbering)
    val selectionSectionId: String?              = null,          // null = all, "__inbox__" = inbox
    /** True after section long-press / Select all / title "select all" — bottom bar shows Delete only. */
    val suppressSelectionGroupActions: Boolean   = false,

    // ── Grouping / pairing modes (ordered ids — toggle/reindex like Select) ──
    val isAadhaarPairingMode: Boolean            = false,
    val aadhaarPairingOrderedIds: List<String>   = emptyList(),
    val isPassportPairingMode: Boolean           = false,
    val passportPairingOrderedIds: List<String>  = emptyList(),
    val isGenericGroupingMode: Boolean           = false,
    val genericGroupingOrderedIds: List<String>  = emptyList(),

    // ── Group naming sheet ────────────────────────────────────────────────────
    val showGroupNameSheet: Boolean              = false,
    val pendingGroupNameText: String             = "",
    val pendingGroupIsAadhaar: Boolean           = false,
    val pendingGroupIsPassport: Boolean          = false,
    val pendingGroupIsSelection: Boolean         = false,
    val pendingGroupAndMove: Boolean             = false,
    val pendingAadhaarFrontId: String?           = null,
    val pendingAadhaarBackId: String?            = null,
    val pendingPassportDataId: String?           = null,
    val pendingPassportAddressId: String?        = null,

    // ── Page reorder sheet ────────────────────────────────────────────────────
    val showPageReorderSheet: Boolean            = false,
    val pageReorderTargetGroupId: String?        = null,

    // ── Group rename dialog ───────────────────────────────────────────────────
    val showRenameGroupDialog: Boolean           = false,
    val renameGroupTargetDocId: String?          = null,
    val renameGroupText: String                  = "",

    // ── Move to section sheet ─────────────────────────────────────────────────
    val showMoveSheet: Boolean                   = false,
    val moveTargetDocId: String?                 = null,

    // ── Group & Move target ───────────────────────────────────────────────────
    val groupAndMoveTargetDocId: String?         = null,

    // ── PDF viewer / share ────────────────────────────────────────────────────
    val showPdfViewerDocId: String?              = null,
    val sharePdfDocId: String?                   = null,
    val pendingRoutingConflicts: List<SectionRoutingConflict> = emptyList(),

    /** Gallery grid columns (2–4) for category sections and Uncategorised tab. */
    val galleryGridColumns: Int                  = 2,

    val snackbarMessage: String?                 = null
) {
    val activeDocumentCount: Int  get() = allDocuments.size
    val categorizedDocumentCount: Int get() = sectionsWithDocs.sumOf { it.totalDocumentCount }
    val inboxCount: Int           get() = inboxDocuments.size
    val completedSectionCount: Int get() = sectionsWithDocs.count { it.isComplete }

    // Computed from selection state
    val selectedCount: Int get() = selectedDocumentIds.size

    val canGroupSelected: Boolean get() {
        if (selectedCount < 2) return false
        val selectedDocs = allDocuments.filter { it.id in selectedDocumentIds }
        val sameSection = selectedDocs
            .map { it.sectionId ?: "__inbox__" }
            .toSet()
            .size == 1
        if (!sameSection) return false
        val allAadhaar = selectedDocs.all { it.documentClass == DocumentClass.AADHAAR }
        if (allAadhaar) return selectedDocs.size == 2
        if (selectedDocs.any { it.documentClass == DocumentClass.AADHAAR }) return false
        val allPassport = selectedDocs.all { it.documentClass == DocumentClass.PASSPORT }
        if (allPassport) return selectedDocs.size == 2
        if (selectedDocs.any { it.documentClass == DocumentClass.PASSPORT }) return false
        return true
    }

    /** If any selected card is already in a group, hide Group / Group & Move (only Delete). */
    val selectionIncludesGroupedDocument: Boolean
        get() = allDocuments.any { it.id in selectedDocumentIds && it.groupId != null }

    val canConfirmGenericGroup: Boolean
        get() = isGenericGroupingMode && genericGroupingOrderedIds.size >= 2

    val canGroupAndMoveSelected: Boolean
        get() = canShowSelectionGroupActions && selectionSectionId == "__inbox__"

    val canShowSelectionGroupActions: Boolean
        get() = canGroupSelected && !selectionIncludesGroupedDocument && !suppressSelectionGroupActions

    val canConfirmAadhaarPair: Boolean
        get() = isAadhaarPairingMode && aadhaarPairingOrderedIds.size == 2

    val canConfirmPassportPair: Boolean
        get() = isPassportPairingMode && passportPairingOrderedIds.size == 2
}

// ── Overlay state (separate MutableStateFlow to avoid re-building sections on every tick) ────────
private data class VaultOverlay(
    val scanPhase: ScanProcessingPhase           = ScanProcessingPhase.Idle,
    val isSelectionMode: Boolean                 = false,
    val selectedDocumentIds: List<String>        = emptyList(),
    val selectionSectionId: String?              = null,
    val suppressSelectionGroupActions: Boolean = false,
    val isAadhaarPairingMode: Boolean            = false,
    val aadhaarPairingOrderedIds: List<String>   = emptyList(),
    val isPassportPairingMode: Boolean           = false,
    val passportPairingOrderedIds: List<String>  = emptyList(),
    val isGenericGroupingMode: Boolean           = false,
    val genericGroupingOrderedIds: List<String>  = emptyList(),
    val showGroupNameSheet: Boolean              = false,
    val pendingGroupNameText: String             = "",
    val pendingGroupIsAadhaar: Boolean           = false,
    val pendingGroupIsPassport: Boolean          = false,
    val pendingGroupIsSelection: Boolean         = false,
    val pendingGroupAndMove: Boolean             = false,
    val pendingAadhaarFrontId: String?           = null,
    val pendingAadhaarBackId: String?            = null,
    val pendingPassportDataId: String?           = null,
    val pendingPassportAddressId: String?        = null,
    val showPageReorderSheet: Boolean            = false,
    val pageReorderTargetGroupId: String?        = null,
    val showRenameGroupDialog: Boolean           = false,
    val renameGroupTargetDocId: String?          = null,
    val renameGroupText: String                  = "",
    val showMoveSheet: Boolean                   = false,
    val moveTargetDocId: String?                 = null,
    val groupAndMoveTargetDocId: String?         = null,
    val pendingCaptureSectionId: String?         = null,
    val pendingRoutingConflicts: List<SectionRoutingConflict> = emptyList(),
    val showPdfViewerDocId: String?              = null,
    val sharePdfDocId: String?                   = null,
    val collapsedSectionIds: Set<String>         = emptySet(),
    val pulsingSectionIds: Set<String>           = emptySet(),
    val snackbarMessage: String?                 = null,
    val galleryGridColumns: Int                  = 2
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocuVaultViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val applicationRepository: ApplicationRepository,
    private val documentRepository: DocumentRepository,
    private val sectionRepository: SectionRepository,
    private val scanPipeline: ScanPipelineService,
    private val fileManager: FileManagerRepository,
    private val aadhaarMaskingService: AadhaarMaskingService,
    private val panMaskingService: PanMaskingService,
    private val fieldExtractor: DocumentFieldExtractorService,
    private val pdfExportService: PdfExportService,
    private val sectionRoutingService: SectionRoutingService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private var lastSectionDocCounts: Map<String, Int>? = null
    private val gson = Gson()

    val instanceId: String = savedState["instanceId"] ?: ""

    private val _overlay = MutableStateFlow(VaultOverlay())

    init {
        savedState.get<Int>(KEY_VAULT_GALLERY_COLUMNS)?.takeIf { it in 2..4 }?.let { cols ->
            _overlay.update { it.copy(galleryGridColumns = cols) }
        }
    }

    private val instanceFlow = applicationRepository.observeAll()
        .flatMapLatest { list -> flowOf(list.firstOrNull { it.id == instanceId }) }

    val uiState: StateFlow<VaultUiState> = combine(
        documentRepository.observeByInstance(instanceId),
        sectionRepository.observeByInstance(instanceId),
        instanceFlow,
        _overlay
    ) { docs: List<Document>, sections: List<ApplicationSection>, inst: ApplicationInstance?, ov: VaultOverlay ->
        val nonArchivedDocs = docs.filter { !it.isArchivedByExport }
        val inboxDocs = computeVisibleDocuments(
            nonArchivedDocs.filter { it.sectionId == null }
        )
        val sectionsWithDocs = sections.map { sec ->
            val sectionDocs = nonArchivedDocs.filter { it.sectionId == sec.id }
            SectionWithDocs(
                section   = sec,
                documents = computeVisibleDocuments(
                    sectionDocs
                ),
                allDocumentsInSection = sectionDocs,
                totalDocumentCount = sectionDocs.size
            )
        }
        handleSectionCountChanges(
            sectionsWithDocs.associate { it.section.id to it.totalDocumentCount }
        )
        VaultUiState(
            instanceId                   = instanceId,
            instanceName                 = inst?.customName ?: "",
            sectionsWithDocs             = sectionsWithDocs,
            inboxDocuments               = inboxDocs,
            allDocuments                 = nonArchivedDocs,
            collapsedSectionIds          = ov.collapsedSectionIds,
            pulsingSectionIds            = ov.pulsingSectionIds,
            isLoading                    = false,
            scanPhase                    = ov.scanPhase,
            isSelectionMode              = ov.isSelectionMode,
            selectedDocumentIds          = ov.selectedDocumentIds,
            selectionSectionId           = ov.selectionSectionId,
            suppressSelectionGroupActions = ov.suppressSelectionGroupActions,
            isAadhaarPairingMode         = ov.isAadhaarPairingMode,
            aadhaarPairingOrderedIds     = ov.aadhaarPairingOrderedIds,
            isPassportPairingMode        = ov.isPassportPairingMode,
            passportPairingOrderedIds    = ov.passportPairingOrderedIds,
            isGenericGroupingMode        = ov.isGenericGroupingMode,
            genericGroupingOrderedIds    = ov.genericGroupingOrderedIds,
            showGroupNameSheet           = ov.showGroupNameSheet,
            pendingGroupNameText         = ov.pendingGroupNameText,
            pendingGroupIsAadhaar        = ov.pendingGroupIsAadhaar,
            pendingGroupIsPassport       = ov.pendingGroupIsPassport,
            pendingGroupIsSelection      = ov.pendingGroupIsSelection,
            pendingGroupAndMove          = ov.pendingGroupAndMove,
            pendingAadhaarFrontId        = ov.pendingAadhaarFrontId,
            pendingAadhaarBackId         = ov.pendingAadhaarBackId,
            pendingPassportDataId        = ov.pendingPassportDataId,
            pendingPassportAddressId     = ov.pendingPassportAddressId,
            showPageReorderSheet         = ov.showPageReorderSheet,
            pageReorderTargetGroupId     = ov.pageReorderTargetGroupId,
            showRenameGroupDialog        = ov.showRenameGroupDialog,
            renameGroupTargetDocId       = ov.renameGroupTargetDocId,
            renameGroupText              = ov.renameGroupText,
            showMoveSheet                = ov.showMoveSheet,
            moveTargetDocId              = ov.moveTargetDocId,
            groupAndMoveTargetDocId      = ov.groupAndMoveTargetDocId,
            pendingRoutingConflicts      = ov.pendingRoutingConflicts,
            showPdfViewerDocId           = ov.showPdfViewerDocId,
            sharePdfDocId                = ov.sharePdfDocId,
            snackbarMessage              = ov.snackbarMessage,
            galleryGridColumns           = ov.galleryGridColumns
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState(isLoading = true))

    fun setGalleryGridColumns(columns: Int) {
        val c = columns.coerceIn(2, 4)
        savedState[KEY_VAULT_GALLERY_COLUMNS] = c
        _overlay.update { it.copy(galleryGridColumns = c) }
    }

    /**
     * iOS parity projection for vault grids:
     * - keep ungrouped documents
     * - for grouped documents, keep one representative card per group
     *   (prefer Aadhaar front, else Passport data page, else first by order)
     */
    private fun computeVisibleDocuments(documents: List<Document>): List<Document> {
        if (documents.isEmpty()) return emptyList()

        val ungrouped = documents
            .filter { it.groupId == null }
            .sortedWith(compareBy({ it.groupPageIndex ?: Int.MAX_VALUE }, { it.sortOrder }))

        val groupedRepresentatives = documents
            .filter { it.groupId != null }
            .groupBy { it.groupId!! }
            .values
            .map { groupDocs ->
                groupDocs.firstOrNull { it.aadhaarSide == "front" }
                    ?: groupDocs.firstOrNull { canonicalPassportSide(it.passportSide) == "data" }
                    ?: groupDocs.minWithOrNull(
                        compareBy<Document>({ it.groupPageIndex ?: Int.MAX_VALUE }, { it.sortOrder })
                    )
            }
            .filterNotNull()

        return (ungrouped + groupedRepresentatives)
            .sortedWith(compareBy({ it.groupPageIndex ?: Int.MAX_VALUE }, { it.sortOrder }))
    }

    private fun handleSectionCountChanges(currentCounts: Map<String, Int>) {
        val previousCounts = lastSectionDocCounts
        lastSectionDocCounts = currentCounts
        if (previousCounts == null) return

        val newlyFilledSectionIds = currentCounts
            .filter { (sectionId, count) -> count > (previousCounts[sectionId] ?: 0) }
            .keys

        if (newlyFilledSectionIds.isEmpty()) return

        _overlay.update { ov ->
            val expandedCollapsed = ov.collapsedSectionIds.toMutableSet().apply {
                removeAll(newlyFilledSectionIds)
            }
            ov.copy(
                collapsedSectionIds = expandedCollapsed,
                pulsingSectionIds = ov.pulsingSectionIds + newlyFilledSectionIds
            )
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(1_800)
            _overlay.update { ov ->
                ov.copy(pulsingSectionIds = ov.pulsingSectionIds - newlyFilledSectionIds.toSet())
            }
        }
    }

    // ── Tab / Section collapse ─────────────────────────────────────────────────

    fun selectTab(index: Int) { /* tabs are pager-driven; kept for future */ }

    fun toggleSection(sectionId: String) {
        _overlay.update { ov ->
            val collapsed = ov.collapsedSectionIds.toMutableSet()
            if (!collapsed.remove(sectionId)) collapsed.add(sectionId)
            ov.copy(collapsedSectionIds = collapsed)
        }
    }

    // ── Scan pipeline ─────────────────────────────────────────────────────────

    fun onScanResult(imageUris: List<Uri>, preferredSectionId: String? = null) {
        if (imageUris.isEmpty()) return
        viewModelScope.launch {
            try {
                _overlay.update { it.copy(pendingCaptureSectionId = preferredSectionId) }
                val conflicts = scanPipeline.process(
                    imageUris     = imageUris,
                    instanceId    = instanceId,
                    preferredSectionId = preferredSectionId,
                    onPhaseUpdate = { phase ->
                        _overlay.update { it.copy(scanPhase = phase) }
                    }
                )
                if (conflicts.isNotEmpty()) {
                    _overlay.update { it.copy(pendingRoutingConflicts = conflicts) }
                }
            } catch (e: Exception) {
                _overlay.update {
                    it.copy(
                        scanPhase       = ScanProcessingPhase.Idle,
                        snackbarMessage = "Scan failed: ${e.localizedMessage}"
                    )
                }
            } finally {
                kotlinx.coroutines.delay(2_000)
                _overlay.update {
                    it.copy(
                        scanPhase = ScanProcessingPhase.Idle,
                        pendingCaptureSectionId = null
                    )
                }
            }
        }
    }

    fun updateScanPhase(phase: ScanProcessingPhase) {
        _overlay.update { it.copy(scanPhase = phase) }
    }

    // ── Selection mode ─────────────────────────────────────────────────────────

    /** iOS: enterSelectionMode(selecting:) */
    fun enterSelectionMode(doc: Document? = null, sectionId: String? = null) {
        _overlay.update { ov ->
            val scopeId = sectionId ?: doc?.sectionId ?: run {
                if (doc?.sectionId == null) "__inbox__" else null
            }
            val initial = if (doc != null) listOf(doc.id) else emptyList()
            ov.copy(
                isSelectionMode      = true,
                selectedDocumentIds  = initial,
                selectionSectionId   = scopeId,
                suppressSelectionGroupActions = false
            )
        }
    }

    /** Long-press section header: select mode scoped to section with all visible docs selected (Delete-only bar). */
    fun enterSectionSelectionAll(sectionId: String) {
        val state = uiState.value
        val swd = state.sectionsWithDocs.firstOrNull { it.section.id == sectionId } ?: return
        val ids = swd.documents.map { it.id }
        if (ids.isEmpty()) return
        _overlay.update { ov ->
            ov.copy(
                isSelectionMode               = true,
                selectionSectionId            = sectionId,
                selectedDocumentIds           = ids,
                suppressSelectionGroupActions = true
            )
        }
    }

    /** Top bar "Select all": every categorized doc plus inbox when on Categories; all inbox on Uncategorised. */
    fun selectAllInCurrentVaultTab(
        categoriesTab: Boolean,
        sectionsWithDocs: List<SectionWithDocs>,
        inboxDocs: List<Document>
    ) {
        val ids = if (categoriesTab) {
            buildList {
                sectionsWithDocs.forEach { swd ->
                    swd.documents.forEach { d -> if (!contains(d.id)) add(d.id) }
                }
                inboxDocs.forEach { d -> if (!contains(d.id)) add(d.id) }
            }
        } else {
            inboxDocs.map { it.id }
        }
        _overlay.update { ov ->
            ov.copy(
                isSelectionMode               = true,
                selectionSectionId            = if (categoriesTab) null else "__inbox__",
                selectedDocumentIds           = ids,
                suppressSelectionGroupActions = true
            )
        }
    }

    /**
     * Top bar Select all / Deselect all: if every doc in scope (categories+inbox, or inbox only) is
     * selected, exit selection; otherwise select that full set.
     */
    fun toggleSelectAllInCurrentVaultTab(
        categoriesTab: Boolean,
        sectionsWithDocs: List<SectionWithDocs>,
        inboxDocs: List<Document>
    ) {
        val targetIds: List<String> = if (categoriesTab) {
            buildList {
                sectionsWithDocs.forEach { swd ->
                    swd.documents.forEach { d -> if (!contains(d.id)) add(d.id) }
                }
                inboxDocs.forEach { d -> if (!contains(d.id)) add(d.id) }
            }
        } else {
            inboxDocs.map { it.id }
        }
        if (targetIds.isEmpty()) {
            exitSelectionMode()
            return
        }
        val selected = _overlay.value.selectedDocumentIds.toSet()
        val allSelected = targetIds.toSet() == selected
        if (allSelected) {
            exitSelectionMode()
        } else {
            selectAllInCurrentVaultTab(categoriesTab, sectionsWithDocs, inboxDocs)
        }
    }

    /** Tap section header circle: add/remove all docs in that section from selection (clears bulk-only bar). */
    fun toggleSectionSelection(sectionId: String) {
        val state = uiState.value
        val swd = state.sectionsWithDocs.firstOrNull { it.section.id == sectionId } ?: return
        val sectionDocIds = swd.documents.map { it.id }
        if (sectionDocIds.isEmpty()) return
        _overlay.update { ov ->
            val cur = ov.selectedDocumentIds.toMutableList()
            val allIn = sectionDocIds.all { it in cur }
            if (allIn) {
                cur.removeAll { it in sectionDocIds }
            } else {
                sectionDocIds.forEach { id -> if (!cur.contains(id)) cur.add(id) }
            }
            val exactlySectionScoped = ov.selectionSectionId == sectionId &&
                cur.size == sectionDocIds.size &&
                sectionDocIds.all { it in cur }
            ov.copy(
                selectedDocumentIds           = cur,
                suppressSelectionGroupActions = exactlySectionScoped
            )
        }
    }

    fun exitSelectionMode() {
        _overlay.update { ov ->
            ov.copy(
                isSelectionMode     = false,
                selectedDocumentIds = emptyList(),
                selectionSectionId  = null,
                suppressSelectionGroupActions = false
            )
        }
    }

    /** iOS: toggleSelection(_:) */
    fun toggleSelection(doc: Document) {
        _overlay.update { ov ->
            val ids = ov.selectedDocumentIds.toMutableList()
            if (ids.remove(doc.id)) {
                ov.copy(selectedDocumentIds = ids, suppressSelectionGroupActions = false)
            } else {
                ov.copy(selectedDocumentIds = ids + doc.id, suppressSelectionGroupActions = false)
            }
        }
    }

    /** iOS: selectAll() */
    fun selectAll(allDocs: List<Document>, inboxDocs: List<Document>, sectionsWithDocs: List<SectionWithDocs>) {
        val scopeId = _overlay.value.selectionSectionId
        val ids: List<String> = when {
            scopeId == "__inbox__" -> inboxDocs.map { it.id }
            scopeId != null -> sectionsWithDocs.find { it.section.id == scopeId }
                ?.documents?.map { it.id } ?: emptyList()
            else -> allDocs.map { it.id }
        }
        _overlay.update { it.copy(selectedDocumentIds = ids, suppressSelectionGroupActions = true) }
    }

    /** iOS: deleteSelected() — expands groups so no orphans remain */
    fun deleteSelected() {
        viewModelScope.launch {
            val state = uiState.value
            val directlySelected = state.allDocuments.filter { it.id in state.selectedDocumentIds }

            val allToDelete = mutableListOf<Document>()
            val expandedGroupIds = mutableSetOf<String>()

            for (doc in directlySelected) {
                val gid = doc.groupId
                if (gid != null) {
                    if (expandedGroupIds.add(gid)) {
                        val members = state.allDocuments.filter { it.groupId == gid }
                        allToDelete.addAll(members)
                    }
                } else {
                    allToDelete.add(doc)
                }
            }

            for (doc in allToDelete) {
                doc.relativePath.takeIf { it.isNotBlank() }?.let { fileManager.deleteFile(it) }
                doc.maskedRelativePath?.let { fileManager.deleteFile(it) }
                doc.thumbnailRelativePath?.let { fileManager.deleteFile(it) }
                documentRepository.deleteById(doc.id)
            }
            exitSelectionMode()
            _overlay.update { it.copy(snackbarMessage = "${allToDelete.size} document(s) deleted") }
        }
    }

    /** iOS: requestSelectionGroupName(andMove:) */
    fun requestSelectionGroupName(andMove: Boolean = false) {
        val state = uiState.value
        if (state.suppressSelectionGroupActions) return
        if (state.selectionIncludesGroupedDocument) return
        if (!state.canGroupSelected) return
        val selected = state.allDocuments.filter { it.id in state.selectedDocumentIds }
        if (selected.size < 2) return
        val name = buildGroupName(selected, state.allDocuments)
        _overlay.update { ov ->
            ov.copy(
                pendingGroupIsAadhaar   = false,
                pendingGroupIsPassport  = false,
                pendingGroupIsSelection = true,
                pendingGroupAndMove     = andMove,
                pendingGroupNameText    = name,
                showGroupNameSheet      = true
            )
        }
    }

    // ── Move to section ────────────────────────────────────────────────────────

    fun showMoveSheet(docId: String) {
        _overlay.update { it.copy(showMoveSheet = true, moveTargetDocId = docId) }
    }

    fun dismissMoveSheet() {
        _overlay.update { it.copy(showMoveSheet = false, moveTargetDocId = null, groupAndMoveTargetDocId = null) }
    }

    fun dismissGroupAndMoveSheet() {
        _overlay.update { it.copy(groupAndMoveTargetDocId = null) }
    }

    /** Routes a document (and its group if any) to a section. iOS: routeDocument to section. */
    fun routeToSection(docId: String, sectionId: String?) {
        viewModelScope.launch {
            val state = uiState.value
            val doc = state.allDocuments.firstOrNull { it.id == docId } ?: return@launch
            // If doc belongs to a group, move all members
            val toMove: List<Document> = if (doc.groupId != null) {
                state.allDocuments.filter { it.groupId == doc.groupId }
            } else {
                listOf(doc)
            }
            val instanceId = doc.applicationInstanceId
            val sections = instanceId?.let { sectionRepository.getByInstanceOnce(it) }.orEmpty()
            for (d in toMove) {
                if (sections.isNotEmpty()) {
                    documentRepository.syncDocumentClassForSectionMove(d, sectionId, sections)
                }
                reExtractFieldsForCurrentClass(d.id, state.allDocuments)
                documentRepository.updateSectionId(d.id, sectionId)
                rerunMaskingAfterMove(d.id)
                tryAutoPairAadhaarAfterMove(
                    documentId = d.id,
                    instanceId = d.applicationInstanceId ?: instanceId
                )
                tryAutoPairPassportAfterMove(
                    documentId = d.id,
                    instanceId = d.applicationInstanceId ?: instanceId
                )
            }
            dismissMoveSheet()
        }
    }

    private suspend fun reExtractFieldsForCurrentClass(documentId: String, allDocs: List<Document>) {
        val updated = documentRepository.getById(documentId) ?: return
        val text = updated.extractedText?.takeIf { it.isNotBlank() } ?: return
        val backText = if (updated.documentClass == DocumentClass.PASSPORT && updated.groupId != null) {
            allDocs.firstOrNull { it.groupId == updated.groupId && it.id != updated.id }
                ?.extractedText
                .orEmpty()
        } else ""
        val fields = fieldExtractor.extract(
            documentClass = updated.documentClass,
            text = text,
            backText = backText
        )
        documentRepository.updateExtractedFields(updated.id, gson.toJson(fields))
    }

    // ── Aadhaar Pairing Mode ───────────────────────────────────────────────────

    /** iOS: startAadhaarPairing(from:) */
    fun startAadhaarPairing(from: Document) {
        if (from.groupId != null || from.documentClass != DocumentClass.AADHAAR) return
        cancelAllGroupingModes()
        _overlay.update {
            it.copy(
                isAadhaarPairingMode = true,
                aadhaarPairingOrderedIds = listOf(from.id)
            )
        }
    }

    /** Toggle selection for Aadhaar pair mode (max 2 docs, same section, ungrouped only). */
    fun toggleAadhaarPairingSelection(doc: Document, allDocs: List<Document>) {
        if (doc.groupId != null || doc.documentClass != DocumentClass.AADHAAR) return
        _overlay.update { ov ->
            if (!ov.isAadhaarPairingMode || ov.aadhaarPairingOrderedIds.isEmpty()) return@update ov
            val cur = ov.aadhaarPairingOrderedIds.toMutableList()
            when {
                doc.id in cur -> {
                    cur.remove(doc.id)
                    if (cur.isEmpty()) {
                        ov.copy(isAadhaarPairingMode = false, aadhaarPairingOrderedIds = emptyList())
                    } else {
                        ov.copy(aadhaarPairingOrderedIds = cur)
                    }
                }
                cur.size >= 2 -> ov
                else -> {
                    val first = allDocs.firstOrNull { it.id == cur.first() } ?: return@update ov
                    if (!sameSectionForPairing(first, doc)) return@update ov
                    ov.copy(aadhaarPairingOrderedIds = cur + doc.id)
                }
            }
        }
    }

    fun confirmAadhaarPairSelection(allDocs: List<Document>) {
        val ov = _overlay.value
        val ids = ov.aadhaarPairingOrderedIds
        if (ids.size != 2) return
        val anchor = allDocs.firstOrNull { it.id == ids[0] } ?: run { cancelAllGroupingModes(); return }
        val partner = allDocs.firstOrNull { it.id == ids[1] } ?: run { cancelAllGroupingModes(); return }
        if ((partner.sectionId ?: "__inbox__") != (anchor.sectionId ?: "__inbox__")) return

        var front = anchor
        var back = partner
        if (anchor.aadhaarSide == "back" || partner.aadhaarSide == "front") {
            front = partner; back = anchor
        }
        val name = buildAadhaarGroupName(front, back)
        val gid = UUID.randomUUID().toString()
        viewModelScope.launch {
            documentRepository.updateGrouping(front.id, gid, name, 0, "front", null)
            documentRepository.updateGrouping(back.id, gid, name, 1, "back", null)
            cancelAllGroupingModes()
        }
    }

    // ── Passport Pairing Mode ──────────────────────────────────────────────────

    fun startPassportPairing(from: Document) {
        if (from.groupId != null || from.documentClass != DocumentClass.PASSPORT) return
        cancelAllGroupingModes()
        _overlay.update {
            it.copy(
                isPassportPairingMode = true,
                passportPairingOrderedIds = listOf(from.id)
            )
        }
    }

    /** Toggle selection for Passport pair mode (max 2 docs, same section, ungrouped only). */
    fun togglePassportPairingSelection(doc: Document, allDocs: List<Document>) {
        if (doc.groupId != null || doc.documentClass != DocumentClass.PASSPORT) return
        _overlay.update { ov ->
            if (!ov.isPassportPairingMode || ov.passportPairingOrderedIds.isEmpty()) return@update ov
            val cur = ov.passportPairingOrderedIds.toMutableList()
            when {
                doc.id in cur -> {
                    cur.remove(doc.id)
                    if (cur.isEmpty()) {
                        ov.copy(isPassportPairingMode = false, passportPairingOrderedIds = emptyList())
                    } else {
                        ov.copy(passportPairingOrderedIds = cur)
                    }
                }
                cur.size >= 2 -> ov
                else -> {
                    val first = allDocs.firstOrNull { it.id == cur.first() } ?: return@update ov
                    if (!sameSectionForPairing(first, doc)) return@update ov
                    ov.copy(passportPairingOrderedIds = cur + doc.id)
                }
            }
        }
    }

    fun confirmPassportPairSelection(allDocs: List<Document>) {
        val ov = _overlay.value
        val ids = ov.passportPairingOrderedIds
        if (ids.size != 2) return
        val anchor = allDocs.firstOrNull { it.id == ids[0] } ?: run { cancelAllGroupingModes(); return }
        val partner = allDocs.firstOrNull { it.id == ids[1] } ?: run { cancelAllGroupingModes(); return }
        if ((partner.sectionId ?: "__inbox__") != (anchor.sectionId ?: "__inbox__")) return

        var dataPage = anchor
        var addressPage = partner
        if (canonicalPassportSide(anchor.passportSide) == "address" ||
            canonicalPassportSide(partner.passportSide) == "data"
        ) {
            dataPage = partner; addressPage = anchor
        }
        val name = buildPassportGroupName(dataPage, addressPage)
        val gid = UUID.randomUUID().toString()
        viewModelScope.launch {
            documentRepository.updateGrouping(dataPage.id, gid, name, 0, null, "data")
            documentRepository.updateGrouping(addressPage.id, gid, name, 1, null, "address")
            cancelAllGroupingModes()
        }
    }

    // ── Generic N-way Grouping Mode ────────────────────────────────────────────

    fun startGenericGrouping(from: Document) {
        if (from.groupId != null) return
        if (from.documentClass == DocumentClass.AADHAAR || from.documentClass == DocumentClass.PASSPORT) return
        cancelAllGroupingModes()
        _overlay.update { it.copy(
            isGenericGroupingMode       = true,
            genericGroupingOrderedIds   = listOf(from.id)
        ) }
    }

    /** Toggle generic group selection (unlimited count; same section; ungrouped; not Aadhaar/Passport). */
    fun toggleGenericGroupingSelection(doc: Document, allDocs: List<Document>) {
        if (doc.groupId != null) return
        if (doc.documentClass == DocumentClass.AADHAAR || doc.documentClass == DocumentClass.PASSPORT) return
        _overlay.update { ov ->
            if (!ov.isGenericGroupingMode || ov.genericGroupingOrderedIds.isEmpty()) return@update ov
            val cur = ov.genericGroupingOrderedIds.toMutableList()
            when {
                doc.id in cur -> {
                    cur.remove(doc.id)
                    if (cur.isEmpty()) {
                        ov.copy(isGenericGroupingMode = false, genericGroupingOrderedIds = emptyList())
                    } else {
                        ov.copy(genericGroupingOrderedIds = cur)
                    }
                }
                else -> {
                    val first = allDocs.firstOrNull { it.id == cur.first() } ?: return@update ov
                    if (!sameSectionForPairing(first, doc)) return@update ov
                    ov.copy(genericGroupingOrderedIds = cur + doc.id)
                }
            }
        }
    }

    /** Show group name sheet for current generic grouping candidates */
    fun requestGenericGroupName() {
        val ov = _overlay.value
        val ordered = ov.genericGroupingOrderedIds
        if (ordered.size < 2) return
        val state = uiState.value
        val docs = state.allDocuments.filter { it.id in ordered }
        val name = buildGroupName(docs, state.allDocuments)
        _overlay.update { it.copy(
            pendingGroupIsAadhaar   = false,
            pendingGroupIsPassport  = false,
            pendingGroupIsSelection = false,
            pendingGroupNameText    = name,
            showGroupNameSheet      = true
        ) }
    }

    fun cancelAllGroupingModes() {
        _overlay.update { it.copy(
            isAadhaarPairingMode         = false,
            aadhaarPairingOrderedIds     = emptyList(),
            isPassportPairingMode        = false,
            passportPairingOrderedIds    = emptyList(),
            isGenericGroupingMode        = false,
            genericGroupingOrderedIds   = emptyList()
        ) }
    }

    private fun sameSectionForPairing(a: Document, b: Document): Boolean =
        (a.sectionId ?: "__inbox__") == (b.sectionId ?: "__inbox__")

    // ── Pending group name text ────────────────────────────────────────────────

    fun onGroupNameTextChange(text: String) {
        _overlay.update { it.copy(pendingGroupNameText = text) }
    }

    fun dismissGroupNameSheet() {
        _overlay.update { it.copy(showGroupNameSheet = false, pendingGroupNameText = "") }
        clearPendingGroupState()
    }

    /** iOS: finalizeGroup(name:) */
    fun finalizeGroup(name: String?) {
        val ov = _overlay.value
        val groupName = name?.trim()?.takeIf { it.isNotEmpty() }

        when {
            ov.pendingGroupIsSelection -> commitSelectionGroup(name)
            ov.pendingGroupIsAadhaar   -> commitAadhaarPair(groupName)
            ov.pendingGroupIsPassport  -> commitPassportPair(groupName)
            else                       -> commitGenericGroup(groupName)
        }
    }

    private fun commitAadhaarPair(groupName: String?) {
        val ov = _overlay.value
        val frontId = ov.pendingAadhaarFrontId ?: run { clearPendingGroupState(); cancelAllGroupingModes(); return }
        val backId  = ov.pendingAadhaarBackId  ?: run { clearPendingGroupState(); cancelAllGroupingModes(); return }
        val gid = UUID.randomUUID().toString()
        viewModelScope.launch {
            documentRepository.updateGrouping(frontId, gid, groupName, 0, "front", null)
            documentRepository.updateGrouping(backId,  gid, groupName, 1, "back",  null)
            clearPendingGroupState()
            cancelAllGroupingModes()
        }
    }

    private fun commitPassportPair(groupName: String?) {
        val ov = _overlay.value
        val dataId    = ov.pendingPassportDataId    ?: run { clearPendingGroupState(); cancelAllGroupingModes(); return }
        val addressId = ov.pendingPassportAddressId ?: run { clearPendingGroupState(); cancelAllGroupingModes(); return }
        val gid = UUID.randomUUID().toString()
        viewModelScope.launch {
            val dataDoc = documentRepository.getById(dataId)
            val addressDoc = documentRepository.getById(addressId)
            val resolvedName = groupName?.trim()?.takeIf { it.isNotEmpty() }
                ?: buildPassportGroupName(dataDoc, addressDoc)
            documentRepository.updateGrouping(dataId,    gid, resolvedName, 0, null, "data")
            documentRepository.updateGrouping(addressId, gid, resolvedName, 1, null, "address")
            clearPendingGroupState()
            cancelAllGroupingModes()
        }
    }

    private fun commitGenericGroup(groupName: String?) {
        val ov = _overlay.value
        val orderedIds = ov.genericGroupingOrderedIds
        if (orderedIds.size < 2) {
            clearPendingGroupState()
            cancelAllGroupingModes()
            return
        }
        val gid = UUID.randomUUID().toString()
        viewModelScope.launch {
            orderedIds.forEachIndexed { idx, id ->
                documentRepository.updateGrouping(id, gid, groupName, idx, null, null)
            }
            clearPendingGroupState()
            cancelAllGroupingModes()
        }
    }

    /** iOS: commitSelectionGroup(name:) */
    private fun commitSelectionGroup(name: String?) {
        val state = uiState.value
        val groupName = name?.trim()?.takeIf { it.isNotEmpty() }
        val directlySelected = state.allDocuments.filter { it.id in state.selectedDocumentIds }
        if (directlySelected.size < 2) { exitSelectionMode(); clearPendingGroupState(); return }

        // Expand selection to include hidden group members
        val expanded = mutableListOf<Document>()
        val seen = mutableSetOf<String>()
        for (doc in directlySelected) {
            if (seen.add(doc.id)) expanded.add(doc)
            state.allDocuments.filter { it.groupId == doc.groupId && it.id != doc.id && doc.groupId != null }
                .forEach { if (seen.add(it.id)) expanded.add(it) }
        }

        fun abortSelectionGroup(msg: String) {
            _overlay.update { it.copy(snackbarMessage = msg) }
            clearPendingGroupState()
            exitSelectionMode()
        }

        val aadhaarOnly = expanded.all { it.documentClass == DocumentClass.AADHAAR }
        if (aadhaarOnly) {
            if (expanded.size != 2) {
                abortSelectionGroup("Aadhaar can only be grouped as front and back (2 documents).")
                return
            }
            var front = expanded[0]
            var back = expanded[1]
            if (front.aadhaarSide == "back" || back.aadhaarSide == "front") {
                front = expanded[1]; back = expanded[0]
            }
            val gid = UUID.randomUUID().toString()
            viewModelScope.launch {
                documentRepository.updateGrouping(front.id, gid, groupName, 0, "front", null)
                documentRepository.updateGrouping(back.id, gid, groupName, 1, "back", null)
                val ov = _overlay.value
                if (ov.pendingGroupAndMove) {
                    _overlay.update { it.copy(groupAndMoveTargetDocId = front.id) }
                }
                exitSelectionMode()
                clearPendingGroupState()
            }
            return
        }

        val passportOnly = expanded.all { it.documentClass == DocumentClass.PASSPORT }
        if (passportOnly) {
            if (expanded.size != 2) {
                abortSelectionGroup("Passport can only be grouped as data and address pages (2 documents).")
                return
            }
            var dataPage = expanded[0]
            var addressPage = expanded[1]
            if (canonicalPassportSide(dataPage.passportSide) == "address" ||
                canonicalPassportSide(addressPage.passportSide) == "data"
            ) {
                dataPage = expanded[1]; addressPage = expanded[0]
            }
            val gid = UUID.randomUUID().toString()
            viewModelScope.launch {
                documentRepository.updateGrouping(dataPage.id, gid, groupName, 0, null, "data")
                documentRepository.updateGrouping(addressPage.id, gid, groupName, 1, null, "address")
                val ov = _overlay.value
                if (ov.pendingGroupAndMove) {
                    _overlay.update { it.copy(groupAndMoveTargetDocId = dataPage.id) }
                }
                exitSelectionMode()
                clearPendingGroupState()
            }
            return
        }

        if (expanded.any { it.documentClass == DocumentClass.AADHAAR || it.documentClass == DocumentClass.PASSPORT }) {
            abortSelectionGroup("Cannot mix Aadhaar or Passport with other document types in one group.")
            return
        }

        val gid = UUID.randomUUID().toString()
        viewModelScope.launch {
            expanded.forEachIndexed { idx, doc ->
                documentRepository.updateGrouping(doc.id, gid, groupName, idx, null, null)
            }
            val ov = _overlay.value
            if (ov.pendingGroupAndMove) {
                val primaryId = expanded.firstOrNull()?.id
                _overlay.update { it.copy(groupAndMoveTargetDocId = primaryId) }
            }
            exitSelectionMode()
            clearPendingGroupState()
        }
    }

    private fun clearPendingGroupState() {
        _overlay.update { it.copy(
            showGroupNameSheet       = false,
            pendingGroupNameText     = "",
            pendingGroupIsAadhaar    = false,
            pendingGroupIsPassport   = false,
            pendingGroupIsSelection  = false,
            pendingGroupAndMove      = false,
            pendingAadhaarFrontId    = null,
            pendingAadhaarBackId     = null,
            pendingPassportDataId    = null,
            pendingPassportAddressId = null
        ) }
    }

    // ── Group operations ───────────────────────────────────────────────────────

    /** iOS: removeDocumentFromGroup(_:sendToReview:) */
    fun removeFromGroup(docId: String, sendToReview: Boolean = false) {
        viewModelScope.launch {
            val state = uiState.value
            val doc = state.allDocuments.firstOrNull { it.id == docId } ?: return@launch
            val gid = doc.groupId ?: return@launch
            val allMembers = state.allDocuments.filter { it.groupId == gid }
            val remaining = allMembers.filter { it.id != docId }

            documentRepository.updateGrouping(docId, null, null, null, null, null)
            if (sendToReview) documentRepository.updateSectionId(docId, null)

            // Dissolve if only one remains
            if (remaining.size == 1) {
                val last = remaining.first()
                documentRepository.updateGrouping(last.id, null, null, null, null, null)
            }
        }
    }

    /** iOS: renameGroup(_:to:) */
    fun renameGroup(docId: String, newName: String) {
        viewModelScope.launch {
            val state = uiState.value
            val doc = state.allDocuments.firstOrNull { it.id == docId } ?: return@launch
            val gid = doc.groupId ?: return@launch
            val trimmed = newName.trim().takeIf { it.isNotEmpty() }
            val allMembers = state.allDocuments.filter { it.groupId == gid }
            for (m in allMembers) {
                documentRepository.updateGrouping(m.id, gid, trimmed, m.groupPageIndex, m.aadhaarSide, m.passportSide)
            }
        }
    }

    /** iOS: ungroupDocuments(_:) */
    fun ungroupDocuments(docId: String) {
        viewModelScope.launch {
            val state = uiState.value
            val doc = state.allDocuments.firstOrNull { it.id == docId } ?: return@launch
            val gid = doc.groupId ?: return@launch
            val allMembers = state.allDocuments.filter { it.groupId == gid }
            for (m in allMembers) {
                documentRepository.updateGrouping(m.id, null, null, null, null, null)
            }
        }
    }

    /** iOS: applyGroupPageReorder(orderedIDs:) */
    fun applyGroupPageReorder(orderedIds: List<String>) {
        viewModelScope.launch {
            orderedIds.forEachIndexed { idx, id ->
                documentRepository.updatePageIndex(id, idx)
            }
            // Also update groupPageIndex
            orderedIds.forEachIndexed { idx, id ->
                val state = uiState.value
                val doc = state.allDocuments.firstOrNull { it.id == id }
                if (doc != null) {
                    documentRepository.updateGrouping(id, doc.groupId, doc.groupName, idx, doc.aadhaarSide, doc.passportSide)
                }
            }
            dismissPageReorderSheet()
        }
    }

    fun showPageReorderSheet(doc: Document) {
        _overlay.update { it.copy(
            showPageReorderSheet      = true,
            pageReorderTargetGroupId  = doc.groupId
        ) }
    }

    fun dismissPageReorderSheet() {
        _overlay.update { it.copy(showPageReorderSheet = false, pageReorderTargetGroupId = null) }
    }

    fun showRenameGroupDialog(doc: Document) {
        _overlay.update { it.copy(
            showRenameGroupDialog   = true,
            renameGroupTargetDocId  = doc.id,
            renameGroupText         = doc.groupName ?: ""
        ) }
    }

    fun onRenameGroupTextChange(text: String) {
        _overlay.update { it.copy(renameGroupText = text) }
    }

    fun confirmRenameGroup() {
        val ov = _overlay.value
        val docId = ov.renameGroupTargetDocId ?: run { dismissRenameGroupDialog(); return }
        renameGroup(docId, ov.renameGroupText)
        dismissRenameGroupDialog()
    }

    fun dismissRenameGroupDialog() {
        _overlay.update { it.copy(showRenameGroupDialog = false, renameGroupTargetDocId = null, renameGroupText = "") }
    }

    // ── PDF Export / Unmerge / Share ───────────────────────────────────────────

    /** iOS: exportGroupAsPDF(primary:) */
    fun exportGroupAsPDF(primaryDocId: String) {
        viewModelScope.launch {
            val state = uiState.value
            val primary = state.allDocuments.firstOrNull { it.id == primaryDocId } ?: return@launch
            val gid = primary.groupId ?: return@launch
            val allMembers = state.allDocuments.filter { it.groupId == gid }
            val ordered = allMembers.sortedWith(compareBy { it.groupPageIndex ?: (it.pageIndex ?: Int.MAX_VALUE) })
            val name = primary.groupName ?: primary.fileName.substringBeforeLast(".")

            try {
                val (pdfFile, relativePath) = pdfExportService.generatePersistent(ordered, name)
                val fileSize = pdfFile.length()

                val pdfDoc = Document(
                    id                    = UUID.randomUUID().toString(),
                    fileName              = pdfFile.name,
                    relativePath          = relativePath,
                    fileSize              = fileSize,
                    typeRawValue          = DocumentType.PDF.rawValue,
                    sectionId             = primary.sectionId,
                    groupId               = gid,
                    groupName             = name,
                    exportedFromGroupId   = gid,
                    applicationInstanceId = instanceId,
                    sortOrder             = primary.sortOrder
                )
                documentRepository.insert(pdfDoc)

                // Archive originals — they disappear from grid but stay on disk for Unmerge
                for (m in allMembers) {
                    documentRepository.updateArchiveState(m.id, true, gid)
                }
                _overlay.update { it.copy(snackbarMessage = "Exported as PDF") }
            } catch (e: Exception) {
                _overlay.update { it.copy(snackbarMessage = "PDF export failed: ${e.localizedMessage}") }
            }
        }
    }

    /** iOS: unmergePDF(_:) */
    fun unmergePDF(docId: String) {
        viewModelScope.launch {
            val state = uiState.value
            val doc = state.allDocuments.firstOrNull { it.id == docId } ?: return@launch
            val groupId = doc.exportedFromGroupId ?: return@launch

            // Restore archived originals — use observeByInstanceAll to get archived docs
            val archivedDocs = documentRepository.getArchivedByGroupId(groupId)
            for (archived in archivedDocs) {
                documentRepository.updateArchiveState(archived.id, false, null)
            }

            // Delete the PDF file and record
            doc.relativePath.takeIf { it.isNotBlank() }?.let { fileManager.deleteFile(it) }
            documentRepository.deleteById(docId)
            _overlay.update { it.copy(snackbarMessage = "Unmerged successfully") }
        }
    }

    /** iOS: sharePDF(_:) — sets state so screen can trigger share intent */
    fun sharePDF(docId: String) {
        _overlay.update { it.copy(sharePdfDocId = docId) }
    }

    fun clearSharePdf() {
        _overlay.update { it.copy(sharePdfDocId = null) }
    }

    fun openPdfViewer(docId: String) {
        _overlay.update { it.copy(showPdfViewerDocId = docId) }
    }

    fun closePdfViewer() {
        _overlay.update { it.copy(showPdfViewerDocId = null) }
    }

    // ── Single document delete ─────────────────────────────────────────────────

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            val state = uiState.value
            val doc = state.allDocuments.firstOrNull { it.id == documentId }
                ?: documentRepository.getById(documentId)
                ?: return@launch

            // If it's a merged PDF, also delete archived originals
            val groupId = doc.exportedFromGroupId
            if (groupId != null) {
                val archived = documentRepository.getArchivedByGroupId(groupId)
                for (a in archived) {
                    a.relativePath.takeIf { it.isNotBlank() }?.let { fileManager.deleteFile(it) }
                    a.maskedRelativePath?.let { fileManager.deleteFile(it) }
                    a.thumbnailRelativePath?.let { fileManager.deleteFile(it) }
                    documentRepository.deleteById(a.id)
                }
            }

            doc.relativePath.takeIf { it.isNotBlank() }?.let { fileManager.deleteFile(it) }
            doc.maskedRelativePath?.let { fileManager.deleteFile(it) }
            doc.thumbnailRelativePath?.let { fileManager.deleteFile(it) }
            documentRepository.deleteById(documentId)
            _overlay.update { it.copy(snackbarMessage = "Document deleted") }
        }
    }

    /** iOS: deleteGroup(_:) — deletes all group members */
    fun deleteGroup(primaryDocId: String) {
        viewModelScope.launch {
            val state = uiState.value
            val doc = state.allDocuments.firstOrNull { it.id == primaryDocId } ?: return@launch
            val gid = doc.groupId ?: run { deleteDocument(primaryDocId); return@launch }
            val allMembers = state.allDocuments.filter { it.groupId == gid }
            for (m in allMembers) {
                m.relativePath.takeIf { it.isNotBlank() }?.let { fileManager.deleteFile(it) }
                m.maskedRelativePath?.let { fileManager.deleteFile(it) }
                m.thumbnailRelativePath?.let { fileManager.deleteFile(it) }
                documentRepository.deleteById(m.id)
            }
            _overlay.update { it.copy(snackbarMessage = "Group deleted") }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** iOS: buildGroupName(for:) — canonical group naming pattern */
    fun buildGroupName(docs: List<Document>, allDocs: List<Document>): String {
        if (docs.isNotEmpty() && docs.all { it.documentClass == DocumentClass.AADHAAR }) {
            val orderedAadhaarDocs = docs.sortedWith(
                compareBy<Document> { if (it.aadhaarSide == "front") 0 else 1 }
            )
            return buildAadhaarGroupName(
                front = orderedAadhaarDocs.first(),
                back = orderedAadhaarDocs.getOrNull(1)
            )
        }

        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        val timestamp = sdf.format(Date())
        val orderedDocs = docs.sortedWith(compareBy { if (it.aadhaarSide == "front") 0 else 1 })
        val ocrName = orderedDocs
            .mapNotNull { extractAadhaarHolderName(it.extractedText) }
            .firstOrNull()
            ?.replace(" ", "_")
        val clsString = docs
            .mapNotNull { if (it.documentClass == DocumentClass.OTHER) null else it.documentClass }
            .firstOrNull()
            ?.displayName?.replace(" ", "_")
        return when {
            ocrName != null && clsString != null -> "${ocrName}_${clsString}_$timestamp"
            clsString != null                    -> "${clsString}_$timestamp"
            else                                 -> "Unknown_$timestamp"
        }
    }

    /** iOS: extractAadhaarHolderName(from:) */
    private fun extractAadhaarHolderName(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val skipTerms = listOf("government", "india", "aadhaar", "unique", "authority",
            "enrolment", "identity", "address", "male", "female",
            "dob", "yob", "year", "date", "birth", "mobile",
            "vid", "download", "issued", "helpline", "www")
        for (line in text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }) {
            val lower = line.lowercase()
            if (skipTerms.any { lower.contains(it) }) continue
            if (line.length !in 4..40) continue
            if (!line.all { it.isLetter() || it.isWhitespace() }) continue
            if (line.split(" ").count { it.length >= 2 } < 2) continue
            return line
        }
        return null
    }

    // ── Group query helpers ────────────────────────────────────────────────────

    fun allGroupMembers(docId: String, allDocs: List<Document>): List<Document> {
        val doc = allDocs.firstOrNull { it.id == docId } ?: return emptyList()
        val gid = doc.groupId ?: return emptyList()
        return allDocs.filter { it.groupId == gid && it.id != docId }
            .sortedWith(compareBy { it.groupPageIndex ?: (it.pageIndex ?: Int.MAX_VALUE) })
    }

    fun selectionIndex(docId: String): Int? {
        val ids = _overlay.value.selectedDocumentIds
        val idx = ids.indexOf(docId)
        return if (idx >= 0) idx + 1 else null  // 1-based like iOS
    }

    fun clearSnackbar() { _overlay.update { it.copy(snackbarMessage = null) } }

    // ── Routing conflicts (hint vs ML) ─────────────────────────────────────────

    /** User closed the review sheet without choosing — documents already stay in scanned folders. */
    fun dismissRoutingConflictsKeepAllHinted() {
        val n = _overlay.value.pendingRoutingConflicts.size
        if (n == 0) return
        _overlay.update {
            it.copy(
                pendingRoutingConflicts = emptyList(),
                snackbarMessage = if (n == 1) "Kept" else "Kept all ($n)"
            )
        }
    }

    /** Resolve one queued conflict: [useDetectedCategory] true → move to ML section + sync class. */
    fun resolveRoutingConflict(conflictId: String, useDetectedCategory: Boolean) {
        val c = _overlay.value.pendingRoutingConflicts.firstOrNull { it.id == conflictId } ?: return
        if (!useDetectedCategory) {
            _overlay.update {
                it.copy(
                    pendingRoutingConflicts = it.pendingRoutingConflicts.filter { it.id != conflictId },
                    snackbarMessage = "Kept"
                )
            }
            return
        }
        viewModelScope.launch {
            val moved = documentRepository.getById(c.documentId)
            val instanceId = moved?.applicationInstanceId
            val currentState = uiState.value
            val sections = instanceId?.let { sectionRepository.getByInstanceOnce(it) }.orEmpty()
            if (moved != null && sections.isNotEmpty()) {
                documentRepository.syncDocumentClassForSectionMove(moved, c.mlSectionId, sections)
            }
            reExtractFieldsForCurrentClass(c.documentId, currentState.allDocuments)
            documentRepository.updateSectionId(c.documentId, c.mlSectionId)
            _overlay.update {
                it.copy(
                    pendingRoutingConflicts = it.pendingRoutingConflicts.filter { it.id != conflictId },
                    snackbarMessage = "Moved"
                )
            }
        }
    }

    /** Apply ML destination + classification for every queued conflict. */
    fun applyAllRoutingConflictsUseDetected() {
        val list = _overlay.value.pendingRoutingConflicts
        if (list.isEmpty()) return
        viewModelScope.launch {
            val firstDoc = documentRepository.getById(list.first().documentId)
            val instanceId = firstDoc?.applicationInstanceId ?: return@launch
            val sections = sectionRepository.getByInstanceOnce(instanceId)
            for (c in list) {
                val moved = documentRepository.getById(c.documentId) ?: continue
                if (sections.isNotEmpty()) {
                    documentRepository.syncDocumentClassForSectionMove(moved, c.mlSectionId, sections)
                }
                val currentAll = documentRepository.getByInstanceOnce(instanceId)
                reExtractFieldsForCurrentClass(c.documentId, currentAll)
                documentRepository.updateSectionId(c.documentId, c.mlSectionId)
            }
            val n = list.size
            _overlay.update {
                it.copy(
                    pendingRoutingConflicts = emptyList(),
                    snackbarMessage = if (n == 1) "Moved" else "Moved all ($n)"
                )
            }
        }
    }

    // ── Reclassify + auto-reroute ──────────────────────────────────────────────

    /**
     * Manually override the document's classification then immediately re-run
     * section routing so the document jumps to the correct category — no manual
     * drag required.
     *
     * iOS equivalent: ClassificationPickerSheet.rerouteAfterReclassification(newClass:)
     *
     * Logic mirrors iOS exactly:
     *  - OTHER → sectionId = null  (goes to Uncategorised / Review tab)
     *  - any other class → run SectionRoutingService against this instance's sections,
     *    ignoring capacity (currentDocCount = 0) so the best-match section always wins.
     */
    fun reclassifyAndReroute(documentId: String, newClass: DocumentClass) {
        viewModelScope.launch {
            // 1. Persist new classification (mark as manually classified)
            documentRepository.updateClassification(
                id        = documentId,
                classRaw  = if (newClass == DocumentClass.OTHER) null else newClass.displayName,
                aadhaarSide = null
            )
            documentRepository.updateManualClassification(
                id     = documentId,
                manual = newClass != DocumentClass.OTHER
            )

            val docNow = documentRepository.getById(documentId)
            val text = docNow?.extractedText?.takeIf { it.isNotBlank() }
            if (text != null) {
                val fields = fieldExtractor.extract(newClass, text)
                documentRepository.updateExtractedFields(documentId, gson.toJson(fields))
            }

            // 2. Determine new sectionId
            if (newClass == DocumentClass.OTHER) {
                // "Unknown / Clear" → send to Review/Uncategorised
                documentRepository.updateSectionId(documentId, null)
                _overlay.update { it.copy(snackbarMessage = "Moved to Uncategorised") }
                return@launch
            }

            // Route using current sections, ignoring capacity (just find first accepting section).
            // This mirrors iOS: rerouteAfterReclassification passes currentDocCount = 0.
            val sections = sectionRepository.getByInstanceOnce(instanceId)
            // Priority 1: required section accepting the class
            // Priority 2: any section accepting the class
            val newSectionId: String? = sections
                .filter { it.acceptedClasses.contains(newClass.displayName) }
                .let { accepting ->
                    accepting.firstOrNull { it.isRequired }?.id ?: accepting.firstOrNull()?.id
                }
            documentRepository.updateSectionId(documentId, newSectionId)
            _overlay.update {
                val sectionName = sections.firstOrNull { it.id == newSectionId }?.title ?: "Uncategorised"
                it.copy(snackbarMessage = "Moved to $sectionName")
            }
        }
    }

    private fun canonicalPassportSide(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "data", "front", "f", "mrz" -> "data"
            "address", "back", "b", "addr" -> "address"
            else -> null
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

        val gid = UUID.randomUUID().toString()
        val groupName = buildPassportGroupName(
            dataPage = if (dataPageId == moved.id) moved else candidate,
            addressPage = if (addressPageId == moved.id) moved else candidate
        )
        documentRepository.updateGrouping(dataPageId, gid, groupName, 0, null, "data")
        documentRepository.updateGrouping(addressPageId, gid, groupName, 1, null, "address")
        _overlay.update { it.copy(snackbarMessage = "Passport paired by number") }
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
        val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return cleaned.takeIf { it.isNotBlank() }
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

        val gid = UUID.randomUUID().toString()
        val groupName = buildAadhaarGroupName(frontDoc, backDoc)
        documentRepository.updateGrouping(frontDoc.id, gid, groupName, 0, "front", null)
        documentRepository.updateGrouping(backDoc.id, gid, groupName, 1, "back", null)
        _overlay.update { it.copy(snackbarMessage = "Aadhaar paired by number") }
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
