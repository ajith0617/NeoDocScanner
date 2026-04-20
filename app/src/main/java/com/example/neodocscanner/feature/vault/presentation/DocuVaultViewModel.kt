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
import com.example.neodocscanner.core.domain.model.DocumentType
import com.example.neodocscanner.core.domain.model.ScanProcessingPhase
import com.example.neodocscanner.core.domain.repository.ApplicationRepository
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import com.example.neodocscanner.core.domain.repository.SectionRepository
import com.example.neodocscanner.feature.vault.data.service.ScanPipelineService
import com.example.neodocscanner.feature.vault.data.service.pdf.PdfExportService
import com.example.neodocscanner.feature.vault.data.service.routing.SectionRoutingService
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
import javax.inject.Inject

// ── UI Models ─────────────────────────────────────────────────────────────────

data class SectionWithDocs(
    val section: ApplicationSection,
    val documents: List<Document>
) {
    val isFull: Boolean
        get() = section.maxDocuments > 0 && documents.size >= section.maxDocuments
    val isComplete: Boolean
        get() = documents.isNotEmpty() && (!section.isRequired || documents.isNotEmpty())
}

data class VaultUiState(
    val instanceId: String                       = "",
    val instanceName: String                     = "",
    val sectionsWithDocs: List<SectionWithDocs>  = emptyList(),
    val inboxDocuments: List<Document>           = emptyList(),
    val allDocuments: List<Document>             = emptyList(),
    val selectedTabIndex: Int                    = 0,
    val collapsedSectionIds: Set<String>         = emptySet(),
    val isLoading: Boolean                       = true,

    // Scan pipeline progress
    val scanPhase: ScanProcessingPhase           = ScanProcessingPhase.Idle,

    // ── Selection mode ────────────────────────────────────────────────────────
    val isSelectionMode: Boolean                 = false,
    val selectedDocumentIds: List<String>        = emptyList(),   // ordered list (for group numbering)
    val selectionSectionId: String?              = null,          // null = all, "__inbox__" = inbox

    // ── Grouping / pairing modes ──────────────────────────────────────────────
    val isAadhaarPairingMode: Boolean            = false,
    val aadhaarPairingAnchorId: String?          = null,
    val isPassportPairingMode: Boolean           = false,
    val passportPairingAnchorId: String?         = null,
    val isGenericGroupingMode: Boolean           = false,
    val genericGroupingAnchorId: String?         = null,
    val genericGroupingCandidateIds: List<String> = emptyList(),

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

    val snackbarMessage: String?                 = null
) {
    val activeDocumentCount: Int  get() = allDocuments.size
    val inboxCount: Int           get() = inboxDocuments.size
    val completedSectionCount: Int get() = sectionsWithDocs.count { it.isComplete }

    // Computed from selection state
    val selectedCount: Int get() = selectedDocumentIds.size

    val canGroupSelected: Boolean get() {
        if (selectedCount < 2) return false
        val scopeId = selectionSectionId
        // All selected must be in the same section scope
        return allDocuments
            .filter { it.id in selectedDocumentIds }
            .map { it.sectionId ?: "__inbox__" }
            .toSet()
            .size == 1
    }
}

// ── Overlay state (separate MutableStateFlow to avoid re-building sections on every tick) ────────
private data class VaultOverlay(
    val scanPhase: ScanProcessingPhase           = ScanProcessingPhase.Idle,
    val isSelectionMode: Boolean                 = false,
    val selectedDocumentIds: List<String>        = emptyList(),
    val selectionSectionId: String?              = null,
    val isAadhaarPairingMode: Boolean            = false,
    val aadhaarPairingAnchorId: String?          = null,
    val isPassportPairingMode: Boolean           = false,
    val passportPairingAnchorId: String?         = null,
    val isGenericGroupingMode: Boolean           = false,
    val genericGroupingAnchorId: String?         = null,
    val genericGroupingCandidateIds: List<String> = emptyList(),
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
    val showPdfViewerDocId: String?              = null,
    val sharePdfDocId: String?                   = null,
    val collapsedSectionIds: Set<String>         = emptySet(),
    val snackbarMessage: String?                 = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocuVaultViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val applicationRepository: ApplicationRepository,
    private val documentRepository: DocumentRepository,
    private val sectionRepository: SectionRepository,
    private val scanPipeline: ScanPipelineService,
    private val fileManager: FileManagerRepository,
    private val pdfExportService: PdfExportService,
    private val sectionRoutingService: SectionRoutingService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val instanceId: String = savedState["instanceId"] ?: ""

    private val _overlay = MutableStateFlow(VaultOverlay())

    private val instanceFlow = applicationRepository.observeAll()
        .flatMapLatest { list -> flowOf(list.firstOrNull { it.id == instanceId }) }

    val uiState: StateFlow<VaultUiState> = combine(
        documentRepository.observeByInstance(instanceId),
        sectionRepository.observeByInstance(instanceId),
        instanceFlow,
        _overlay
    ) { docs: List<Document>, sections: List<ApplicationSection>, inst: ApplicationInstance?, ov: VaultOverlay ->
        val nonArchivedDocs = docs.filter { !it.isArchivedByExport }
        val inboxDocs = nonArchivedDocs.filter { it.sectionId == null }
        val sectionsWithDocs = sections.map { sec ->
            SectionWithDocs(
                section   = sec,
                documents = nonArchivedDocs.filter { it.sectionId == sec.id }
                    .sortedWith(compareBy({ it.groupPageIndex ?: Int.MAX_VALUE }, { it.sortOrder }))
            )
        }
        VaultUiState(
            instanceId                   = instanceId,
            instanceName                 = inst?.customName ?: "",
            sectionsWithDocs             = sectionsWithDocs,
            inboxDocuments               = inboxDocs,
            allDocuments                 = nonArchivedDocs,
            collapsedSectionIds          = ov.collapsedSectionIds,
            isLoading                    = false,
            scanPhase                    = ov.scanPhase,
            isSelectionMode              = ov.isSelectionMode,
            selectedDocumentIds          = ov.selectedDocumentIds,
            selectionSectionId           = ov.selectionSectionId,
            isAadhaarPairingMode         = ov.isAadhaarPairingMode,
            aadhaarPairingAnchorId       = ov.aadhaarPairingAnchorId,
            isPassportPairingMode        = ov.isPassportPairingMode,
            passportPairingAnchorId      = ov.passportPairingAnchorId,
            isGenericGroupingMode        = ov.isGenericGroupingMode,
            genericGroupingAnchorId      = ov.genericGroupingAnchorId,
            genericGroupingCandidateIds  = ov.genericGroupingCandidateIds,
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
            showPdfViewerDocId           = ov.showPdfViewerDocId,
            sharePdfDocId                = ov.sharePdfDocId,
            snackbarMessage              = ov.snackbarMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState(isLoading = true))

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

    fun onScanResult(imageUris: List<Uri>) {
        if (imageUris.isEmpty()) return
        viewModelScope.launch {
            try {
                scanPipeline.process(
                    imageUris     = imageUris,
                    instanceId    = instanceId,
                    onPhaseUpdate = { phase ->
                        _overlay.update { it.copy(scanPhase = phase) }
                    }
                )
            } catch (e: Exception) {
                _overlay.update {
                    it.copy(
                        scanPhase       = ScanProcessingPhase.Idle,
                        snackbarMessage = "Scan failed: ${e.localizedMessage}"
                    )
                }
            } finally {
                kotlinx.coroutines.delay(2_000)
                _overlay.update { it.copy(scanPhase = ScanProcessingPhase.Idle) }
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
                selectionSectionId   = scopeId
            )
        }
    }

    fun exitSelectionMode() {
        _overlay.update { ov ->
            ov.copy(
                isSelectionMode     = false,
                selectedDocumentIds = emptyList(),
                selectionSectionId  = null
            )
        }
    }

    /** iOS: toggleSelection(_:) */
    fun toggleSelection(doc: Document) {
        _overlay.update { ov ->
            val ids = ov.selectedDocumentIds.toMutableList()
            if (ids.remove(doc.id)) {
                ov.copy(selectedDocumentIds = ids)
            } else {
                ov.copy(selectedDocumentIds = ids + doc.id)
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
        _overlay.update { it.copy(selectedDocumentIds = ids) }
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
            for (d in toMove) {
                documentRepository.updateSectionId(d.id, sectionId)
            }
            dismissMoveSheet()
        }
    }

    // ── Aadhaar Pairing Mode ───────────────────────────────────────────────────

    /** iOS: startAadhaarPairing(from:) */
    fun startAadhaarPairing(from: Document) {
        cancelAllGroupingModes()
        _overlay.update { it.copy(isAadhaarPairingMode = true, aadhaarPairingAnchorId = from.id) }
    }

    /** iOS: isValidAadhaarPairingPartner(_:) */
    fun isValidAadhaarPairingPartner(doc: Document, allDocs: List<Document>): Boolean {
        val ov = _overlay.value
        val anchorId = ov.aadhaarPairingAnchorId ?: return false
        val anchor = allDocs.firstOrNull { it.id == anchorId } ?: return false
        return doc.id != anchorId
                && doc.documentClass == DocumentClass.AADHAAR
                && doc.groupId == null
                && (doc.sectionId ?: "__inbox__") == (anchor.sectionId ?: "__inbox__")
    }

    /** iOS: confirmAadhaarPair(with:) */
    fun confirmAadhaarPair(partner: Document, allDocs: List<Document>) {
        val ov = _overlay.value
        val anchorId = ov.aadhaarPairingAnchorId ?: run { cancelAllGroupingModes(); return }
        val anchor = allDocs.firstOrNull { it.id == anchorId } ?: run { cancelAllGroupingModes(); return }
        if ((partner.sectionId ?: "__inbox__") != (anchor.sectionId ?: "__inbox__")) return

        var front = anchor
        var back = partner
        if (anchor.aadhaarSide == "back" || partner.aadhaarSide == "front") {
            front = partner; back = anchor
        }
        val name = buildGroupName(listOf(front, back), allDocs)
        _overlay.update { it.copy(
            pendingAadhaarFrontId  = front.id,
            pendingAadhaarBackId   = back.id,
            pendingGroupIsAadhaar  = true,
            pendingGroupIsPassport = false,
            pendingGroupIsSelection = false,
            pendingGroupNameText   = name,
            showGroupNameSheet     = true
        ) }
    }

    // ── Passport Pairing Mode ──────────────────────────────────────────────────

    fun startPassportPairing(from: Document) {
        cancelAllGroupingModes()
        _overlay.update { it.copy(isPassportPairingMode = true, passportPairingAnchorId = from.id) }
    }

    fun isValidPassportPairingPartner(doc: Document, allDocs: List<Document>): Boolean {
        val ov = _overlay.value
        val anchorId = ov.passportPairingAnchorId ?: return false
        val anchor = allDocs.firstOrNull { it.id == anchorId } ?: return false
        return doc.id != anchorId
                && doc.documentClass == DocumentClass.PASSPORT
                && doc.groupId == null
                && (doc.sectionId ?: "__inbox__") == (anchor.sectionId ?: "__inbox__")
    }

    fun confirmPassportPair(partner: Document, allDocs: List<Document>) {
        val ov = _overlay.value
        val anchorId = ov.passportPairingAnchorId ?: run { cancelAllGroupingModes(); return }
        val anchor = allDocs.firstOrNull { it.id == anchorId } ?: run { cancelAllGroupingModes(); return }
        if ((partner.sectionId ?: "__inbox__") != (anchor.sectionId ?: "__inbox__")) return

        var dataPage = anchor
        var addressPage = partner
        if (anchor.passportSide == "address" || partner.passportSide == "data") {
            dataPage = partner; addressPage = anchor
        }
        val name = buildGroupName(listOf(dataPage, addressPage), allDocs)
        _overlay.update { it.copy(
            pendingPassportDataId    = dataPage.id,
            pendingPassportAddressId = addressPage.id,
            pendingGroupIsPassport   = true,
            pendingGroupIsAadhaar    = false,
            pendingGroupIsSelection  = false,
            pendingGroupNameText     = name,
            showGroupNameSheet       = true
        ) }
    }

    // ── Generic N-way Grouping Mode ────────────────────────────────────────────

    fun startGenericGrouping(from: Document) {
        cancelAllGroupingModes()
        _overlay.update { it.copy(
            isGenericGroupingMode       = true,
            genericGroupingAnchorId     = from.id,
            genericGroupingCandidateIds = emptyList()
        ) }
    }

    fun isValidGenericGroupCandidate(doc: Document, allDocs: List<Document>): Boolean {
        val ov = _overlay.value
        val anchorId = ov.genericGroupingAnchorId ?: return false
        val anchor = allDocs.firstOrNull { it.id == anchorId } ?: return false
        return doc.id != anchorId
                && (doc.sectionId ?: "__inbox__") == (anchor.sectionId ?: "__inbox__")
    }

    fun toggleGenericGroupingCandidate(doc: Document, allDocs: List<Document>) {
        if (!isValidGenericGroupCandidate(doc, allDocs)) return
        _overlay.update { ov ->
            val ids = ov.genericGroupingCandidateIds.toMutableList()
            if (!ids.remove(doc.id)) ids.add(doc.id)
            ov.copy(genericGroupingCandidateIds = ids)
        }
    }

    /** Show group name sheet for current generic grouping candidates */
    fun requestGenericGroupName() {
        val ov = _overlay.value
        val anchorId = ov.genericGroupingAnchorId ?: return
        if (ov.genericGroupingCandidateIds.isEmpty()) return
        val state = uiState.value
        val allIds = listOf(anchorId) + ov.genericGroupingCandidateIds
        val docs = state.allDocuments.filter { it.id in allIds }
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
            aadhaarPairingAnchorId       = null,
            isPassportPairingMode        = false,
            passportPairingAnchorId      = null,
            isGenericGroupingMode        = false,
            genericGroupingAnchorId      = null,
            genericGroupingCandidateIds  = emptyList()
        ) }
    }

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
            documentRepository.updateGrouping(dataId,    gid, groupName, 0, null, "data")
            documentRepository.updateGrouping(addressId, gid, groupName, 1, null, "address")
            clearPendingGroupState()
            cancelAllGroupingModes()
        }
    }

    private fun commitGenericGroup(groupName: String?) {
        val ov = _overlay.value
        val anchorId = ov.genericGroupingAnchorId ?: run { clearPendingGroupState(); cancelAllGroupingModes(); return }
        if (ov.genericGroupingCandidateIds.isEmpty()) { clearPendingGroupState(); cancelAllGroupingModes(); return }
        val orderedIds = listOf(anchorId) + ov.genericGroupingCandidateIds
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
}
