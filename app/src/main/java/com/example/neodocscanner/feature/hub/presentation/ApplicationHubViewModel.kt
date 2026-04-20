package com.example.neodocscanner.feature.hub.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neodocscanner.core.data.file.FileManagerRepository
import com.example.neodocscanner.core.data.mock.SectionTemplateProvider
import com.example.neodocscanner.core.domain.model.ApplicationInstance
import com.example.neodocscanner.core.domain.model.ApplicationTemplate
import com.example.neodocscanner.core.domain.model.QRPayload
import com.example.neodocscanner.core.domain.repository.ApplicationRepository
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import com.example.neodocscanner.core.domain.repository.SectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI Models ─────────────────────────────────────────────────────────────────

/**
 * Projection of one vault card — instance data + live document count.
 * iOS equivalent: the ApplicationInstance @Model accessed in the hub List.
 */
data class ApplicationInstanceUi(
    val instance: ApplicationInstance,
    val documentCount: Int
)

/**
 * Complete UI state for ApplicationHubScreen.
 *
 * iOS equivalent: The @Observable properties of ApplicationHubViewModel.swift:
 * - applicationInstances, showCreateSheet, instanceToRename, instanceToDelete, etc.
 */
data class HubUiState(
    val instances: List<ApplicationInstanceUi> = emptyList(),
    val isLoading: Boolean                     = true,
    val showCreateSheet: Boolean               = false,
    val renameTarget: ApplicationInstance?     = null,
    val renameInput: String                    = "",
    val deleteTarget: ApplicationInstance?     = null,
    val snackbarMessage: String?               = null,
    val isOperationRunning: Boolean            = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Manages the Application Hub screen state.
 *
 * iOS equivalent: ApplicationHubViewModel.swift (@Observable class).
 *
 * Key design decisions:
 * - Vault list is REACTIVE: uses flatMapLatest + combine so every card's
 *   document count updates live without manual refreshes.
 * - Create / rename / delete are suspend operations run in viewModelScope.
 * - File cleanup is done before Room deletion to prevent orphaned files.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ApplicationHubViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val documentRepository: DocumentRepository,
    private val sectionRepository: SectionRepository,
    private val fileManagerRepository: FileManagerRepository,
    private val sectionTemplateProvider: SectionTemplateProvider
) : ViewModel() {

    // ── Extra mutable state (dialogs, sheets, snackbar) ──────────────────────
    // MUST be declared before uiState so it's initialised in time for combine().
    private val _extraState = MutableStateFlow(HubUiState())

    // ── Reactive instance list ────────────────────────────────────────────────

    /**
     * Combines every ApplicationInstance with its live document count.
     * iOS equivalent: The @Query FetchDescriptor in ApplicationHubViewModel that
     * also computed documentCount per instance.
     */
    val uiState: StateFlow<HubUiState> = combine(
        applicationRepository.observeAll()
            .flatMapLatest { instances ->
                if (instances.isEmpty()) {
                    flowOf(emptyList<ApplicationInstanceUi>())
                } else {
                    combine(
                        instances.map { instance ->
                            documentRepository.observeByInstance(instance.id)
                                .map { docs ->
                                    ApplicationInstanceUi(
                                        instance      = instance,
                                        documentCount = docs.size
                                    )
                                }
                        }
                    ) { array -> array.toList() }
                }
            },
        _extraState
    ) { instanceUis, extra ->
        extra.copy(
            instances = instanceUis,
            isLoading = false
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = HubUiState(isLoading = true)
    )

    // ── Create vault ──────────────────────────────────────────────────────────

    fun showCreateSheet()  { _extraState.update { it.copy(showCreateSheet = true) } }
    fun dismissCreateSheet() { _extraState.update { it.copy(showCreateSheet = false) } }

    /**
     * Creates a new [ApplicationInstance] from a template and seeds its sections.
     *
     * iOS equivalent: ApplicationHubViewModel.createInstance(from:customName:)
     */
    fun createFromTemplate(
        template: ApplicationTemplate,
        customName: String
    ) {
        val resolvedName = customName.ifBlank { template.name }
        _extraState.update { it.copy(isOperationRunning = true, showCreateSheet = false) }

        viewModelScope.launch {
            val instance = ApplicationInstance(
                templateId   = template.id,
                templateName = template.name,
                customName   = resolvedName,
                iconName     = template.iconName
            )
            applicationRepository.insert(instance)

            val sections = sectionTemplateProvider.sectionsFor(
                templateId = template.id,
                instanceId = instance.id
            )
            sectionRepository.insertAll(sections)

            _extraState.update {
                it.copy(
                    isOperationRunning = false,
                    snackbarMessage    = "\"$resolvedName\" created"
                )
            }
        }
    }

    /**
     * Creates a new vault from a QR payload.
     *
     * iOS equivalent: ApplicationHubViewModel.createInstance(from:QRPayload)
     */
    fun createFromQR(payload: QRPayload) {
        _extraState.update { it.copy(isOperationRunning = true, showCreateSheet = false) }

        viewModelScope.launch {
            val template = payload.resolvedTemplate
            val instance = ApplicationInstance(
                templateId         = template.id,
                templateName       = template.name,
                customName         = payload.suggestedName,
                iconName           = template.iconName,
                serverReferenceId  = payload.ref,
                serverApplicantName = payload.applicantName,
                serverBranch       = payload.branch,
                serverMetadata     = payload.rawJson,
                linkedAt           = System.currentTimeMillis(),
                linkStatus         = "linked"
            )
            applicationRepository.insert(instance)
            sectionRepository.insertAll(
                sectionTemplateProvider.sectionsFor(template.id, instance.id)
            )
            _extraState.update {
                it.copy(
                    isOperationRunning = false,
                    snackbarMessage    = "Vault linked: ${payload.suggestedName}"
                )
            }
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    fun startRename(instance: ApplicationInstance) {
        _extraState.update {
            it.copy(renameTarget = instance, renameInput = instance.customName)
        }
    }

    fun onRenameInputChange(value: String) {
        _extraState.update { it.copy(renameInput = value) }
    }

    fun confirmRename() {
        val target = _extraState.value.renameTarget ?: return
        val newName = _extraState.value.renameInput.trim()
        if (newName.isBlank()) { dismissRename(); return }

        viewModelScope.launch {
            applicationRepository.updateName(target.id, newName)
            _extraState.update {
                it.copy(
                    renameTarget    = null,
                    renameInput     = "",
                    snackbarMessage = "Renamed to \"$newName\""
                )
            }
        }
    }

    fun dismissRename() {
        _extraState.update { it.copy(renameTarget = null, renameInput = "") }
    }

    // ── Archive ───────────────────────────────────────────────────────────────

    /**
     * iOS equivalent: ApplicationHubViewModel.archiveInstance(_:)
     */
    fun archiveInstance(instance: ApplicationInstance) {
        viewModelScope.launch {
            val newStatus = if (instance.isArchived) "active" else "archived"
            applicationRepository.updateStatus(instance.id, newStatus)
            val label = if (newStatus == "archived") "archived" else "restored"
            _extraState.update { it.copy(snackbarMessage = "\"${instance.customName}\" $label") }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun startDelete(instance: ApplicationInstance) {
        _extraState.update { it.copy(deleteTarget = instance) }
    }

    /**
     * Cascade-deletes all files, documents, sections, then the instance itself.
     *
     * iOS equivalent: ApplicationHubViewModel.deleteInstance(_:) which calls
     * FileManagerService.deleteFiles then modelContext.delete.
     */
    fun confirmDelete() {
        val target = _extraState.value.deleteTarget ?: return
        _extraState.update { it.copy(deleteTarget = null, isOperationRunning = true) }

        viewModelScope.launch {
            // 1. Delete files on disk
            val documents = documentRepository.getByInstanceOnce(target.id)
            documents.forEach { doc ->
                doc.relativePath.takeIf { it.isNotBlank() }
                    ?.let { fileManagerRepository.deleteFile(it) }
                doc.maskedRelativePath
                    ?.let { fileManagerRepository.deleteFile(it) }
                doc.thumbnailRelativePath
                    ?.let { fileManagerRepository.deleteFile(it) }
            }

            // 2. Delete Room records (cascade order)
            documentRepository.deleteAllByInstance(target.id)
            sectionRepository.deleteAllByInstance(target.id)
            applicationRepository.deleteById(target.id)

            _extraState.update {
                it.copy(
                    isOperationRunning = false,
                    snackbarMessage    = "\"${target.customName}\" deleted"
                )
            }
        }
    }

    fun dismissDelete() {
        _extraState.update { it.copy(deleteTarget = null) }
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────

    fun clearSnackbar() { _extraState.update { it.copy(snackbarMessage = null) } }
}
