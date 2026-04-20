package com.example.neodocscanner.feature.vault.presentation.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import com.example.neodocscanner.core.domain.repository.SectionRepository
import com.example.neodocscanner.feature.vault.data.service.routing.SectionRoutingService
import com.example.neodocscanner.feature.vault.data.service.scanner.DocumentFileManager
import com.example.neodocscanner.feature.vault.data.service.text.DocumentNamingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class DetailUiState(
    val document: Document? = null,
    val renameText: String = "",
    val renameSuccess: Boolean = false,
    val renameError: String? = null,
    val isBusy: Boolean = false,
    val showClassPicker: Boolean = false,
    val snackbarMessage: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Manages state for the DocumentDetailSheet.
 *
 * iOS equivalent: The @Observable DocuVaultViewModel methods used by
 * DocumentDetailView — renameDocument, plus the ClassificationPickerSheet logic.
 *
 * This is a standalone ViewModel (not scoped to the vault) so it can be
 * created fresh per document from the ViewerScreen or Uncategorised tab.
 */
@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val sectionRepository: SectionRepository,
    private val routingService: SectionRoutingService,
    private val fileManager: DocumentFileManager,
    private val namingService: DocumentNamingService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    // ── Initialise with a document ────────────────────────────────────────────

    fun init(document: Document) {
        val baseName = File(document.fileName).nameWithoutExtension
        _state.update { it.copy(document = document, renameText = baseName) }
    }

    fun refresh(document: Document) {
        _state.update { s ->
            s.copy(
                document    = document,
                renameText  = if (s.renameText.isBlank()) File(document.fileName).nameWithoutExtension
                              else s.renameText
            )
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    fun onRenameTextChange(text: String) {
        _state.update { it.copy(renameText = text, renameSuccess = false, renameError = null) }
    }

    /**
     * True when the rename field has a non-empty, changed value.
     * iOS equivalent: DocumentDetailView.isRenameEnabled
     */
    val isRenameEnabled: Boolean
        get() {
            val doc = _state.value.document ?: return false
            val trimmed   = _state.value.renameText.trim()
            val sanitized = trimmed.replace(" ", "_")
            val current   = File(doc.fileName).nameWithoutExtension
            return trimmed.isNotEmpty() && sanitized != current
        }

    /**
     * Auto-generates a name using SmartNamingService — the same algorithm as iOS.
     * Reads the "Name" field from extractedFields, then calls DocumentNamingService.
     */
    fun autoGenerateName() {
        val doc = _state.value.document ?: return
        val extractedName = doc.decodedFields.firstOrNull { it.label == "Name" }?.value
        val generated = namingService.name(
            documentClass = doc.documentClass,
            fields        = doc.decodedFields,
            dateAdded     = Date(doc.dateAdded)
        )
        _state.update { it.copy(renameText = generated, renameSuccess = false, renameError = null) }
    }

    /**
     * Applies the rename — renames the physical file and updates Room.
     * iOS equivalent: DocuVaultViewModel.renameDocument(_:to:)
     */
    fun applyRename() {
        val doc = _state.value.document ?: return
        val trimmed = _state.value.renameText.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(renameError = "Name cannot be empty.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, renameError = null, renameSuccess = false) }
            try {
                val newBase   = trimmed.replace(" ", "_")
                val extension = File(doc.fileName).extension
                val newName   = if (extension.isNotEmpty()) "$newBase.$extension" else newBase

                // Rename physical files on disk
                val oldFile = fileManager.toAbsolute(doc.relativePath)
                val newFile = File(oldFile.parent, newName)
                if (oldFile.exists()) oldFile.renameTo(newFile)

                val newRelative = fileManager.toRelative(newFile.absolutePath)

                // Also rename masked copy if present
                val newMasked = doc.maskedRelativePath?.let { maskedRel ->
                    val maskedOld = fileManager.toAbsolute(maskedRel)
                    val maskedExt = File(maskedRel).extension
                    val maskedNew = File(maskedOld.parent, "${newBase}_masked.$maskedExt")
                    if (maskedOld.exists()) maskedOld.renameTo(maskedNew)
                    fileManager.toRelative(maskedNew.absolutePath)
                }

                // Also rename thumbnail if present
                val newThumb = doc.thumbnailRelativePath?.let { thumbRel ->
                    val thumbOld = fileManager.toAbsolute(thumbRel)
                    val thumbExt = File(thumbRel).extension
                    val thumbNew = File(thumbOld.parent, "${newBase}_thumb.$thumbExt")
                    if (thumbOld.exists()) thumbOld.renameTo(thumbNew)
                    fileManager.toRelative(thumbNew.absolutePath)
                }

                documentRepository.updateFilePaths(
                    id                 = doc.id,
                    fileName           = newName,
                    relativePath       = newRelative,
                    maskedRelativePath = newMasked,
                    thumbRelativePath  = newThumb
                )

                val updated = documentRepository.getById(doc.id)
                _state.update {
                    it.copy(
                        document      = updated,
                        renameText    = File(newName).nameWithoutExtension,
                        renameSuccess = true,
                        renameError   = null,
                        isBusy        = false
                    )
                }

                // Auto-dismiss success badge after 2 s (matches iOS)
                kotlinx.coroutines.delay(2_000)
                _state.update { it.copy(renameSuccess = false) }

            } catch (e: Exception) {
                _state.update { it.copy(
                    isBusy      = false,
                    renameError = e.message ?: "Rename failed. Please try again."
                ) }
            }
        }
    }

    // ── Classification picker ─────────────────────────────────────────────────

    fun showClassPicker()    { _state.update { it.copy(showClassPicker = true) } }
    fun dismissClassPicker() { _state.update { it.copy(showClassPicker = false) } }

    /**
     * Applies manual reclassification and re-runs section routing.
     * iOS equivalent: ClassificationPickerSheet.rerouteAfterReclassification(newClass:)
     *
     * Rules:
     * - .other / Unknown → clear documentClass, send to Uncategorised (sectionId = null)
     * - Any other class   → update class, find best matching section and route there
     */
    fun reclassify(newClass: DocumentClass) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, showClassPicker = false) }

            val newClassRaw = if (newClass == DocumentClass.OTHER) null else newClass.displayName

            documentRepository.updateClassification(
                id          = doc.id,
                classRaw    = newClassRaw,
                aadhaarSide = doc.aadhaarSide
            )
            // Mark as manually classified (matches iOS isManuallyClassified flag)
            documentRepository.updateManualClassification(
                id     = doc.id,
                manual = newClass != DocumentClass.OTHER
            )

            // "Unknown / Clear" → send to Uncategorised, skip routing
            if (newClass == DocumentClass.OTHER) {
                documentRepository.updateSectionId(doc.id, null)
            } else {
                // Re-run routing to move doc to the correct section automatically
                val instanceId = doc.applicationInstanceId
                if (instanceId != null) {
                    val sections = sectionRepository.getByInstanceOnce(instanceId)
                    val result = routingService.route(
                        documents              = listOf(doc.copy(documentClassRaw = newClassRaw)),
                        sections               = sections,
                        existingCountBySection = emptyMap()
                    )
                    result.firstOrNull()?.sectionId?.let { sid ->
                        documentRepository.updateSectionId(doc.id, sid)
                    }
                }
            }

            // Persist isManuallyClassified flag
            val updatedDoc = documentRepository.getById(doc.id)
            _state.update { it.copy(document = updatedDoc, isBusy = false) }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes the document and its associated files.
     * iOS equivalent: DocuVaultViewModel.deleteDocument(_:)
     */
    fun deleteDocument(onDone: () -> Unit) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            fileManager.deleteDocumentFiles(
                instanceId = doc.applicationInstanceId ?: "",
                documentId = doc.id
            )
            // Also delete masked copy
            doc.maskedRelativePath?.let { fileManager.toAbsolute(it).delete() }
            // Delete thumbnail
            doc.thumbnailRelativePath?.let { fileManager.toAbsolute(it).delete() }

            documentRepository.delete(doc)
            onDone()
        }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}
