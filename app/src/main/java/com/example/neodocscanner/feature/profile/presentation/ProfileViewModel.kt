package com.example.neodocscanner.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neodocscanner.core.data.local.preferences.ProfileDataStore
import com.example.neodocscanner.core.domain.model.UserProfile
import com.example.neodocscanner.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class ProfileUiState(
    val profile: UserProfile           = UserProfile(),
    val isEditing: Boolean             = false,
    val draftName: String              = "",
    val draftDesignation: String       = "",
    val draftOrganisation: String      = "",
    val draftEmail: String             = "",
    val showLogoutConfirm: Boolean     = false
)

// ── Private overlay ───────────────────────────────────────────────────────────

private data class ProfileOverlay(
    val isEditing: Boolean         = false,
    val draftName: String          = "",
    val draftDesignation: String   = "",
    val draftOrganisation: String  = "",
    val draftEmail: String         = "",
    val showLogoutConfirm: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Manages profile view/edit state.
 *
 * iOS equivalent: @AppStorage fields + @State edit/save/logout logic in ProfileView.swift.
 *
 * Data is persisted immediately on Save to ProfileDataStore (DataStore<Preferences>),
 * matching iOS @AppStorage instant persistence behaviour.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileDataStore: ProfileDataStore,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _overlay = MutableStateFlow(ProfileOverlay())

    val uiState: StateFlow<ProfileUiState> = combine(
        profileDataStore.profileFlow,
        _overlay
    ) { profile: UserProfile, ov: ProfileOverlay ->
        ProfileUiState(
            profile           = profile,
            isEditing         = ov.isEditing,
            draftName         = ov.draftName,
            draftDesignation  = ov.draftDesignation,
            draftOrganisation = ov.draftOrganisation,
            draftEmail        = ov.draftEmail,
            showLogoutConfirm = ov.showLogoutConfirm
        )
    }.stateIn(
        scope         = viewModelScope,
        started       = SharingStarted.WhileSubscribed(5_000),
        initialValue  = ProfileUiState()
    )

    // ── Edit / Save ────────────────────────────────────────────────────────────

    /** iOS: startEditing() — copies saved values into draft fields */
    fun startEditing() {
        val profile = uiState.value.profile
        _overlay.update {
            it.copy(
                isEditing         = true,
                draftName         = profile.name,
                draftDesignation  = profile.designation,
                draftOrganisation = profile.organisation,
                draftEmail        = profile.email
            )
        }
    }

    fun onDraftNameChange(value: String)         { _overlay.update { it.copy(draftName = value) } }
    fun onDraftDesignationChange(value: String)  { _overlay.update { it.copy(draftDesignation = value) } }
    fun onDraftOrganisationChange(value: String) { _overlay.update { it.copy(draftOrganisation = value) } }
    fun onDraftEmailChange(value: String)        { _overlay.update { it.copy(draftEmail = value) } }

    /** iOS: saveProfile() — trims & persists draft values, exits edit mode */
    fun saveProfile() {
        val ov = _overlay.value
        viewModelScope.launch {
            profileDataStore.saveProfile(
                UserProfile(
                    name         = ov.draftName.trim(),
                    designation  = ov.draftDesignation.trim(),
                    organisation = ov.draftOrganisation.trim(),
                    email        = ov.draftEmail.trim()
                )
            )
        }
        _overlay.update { it.copy(isEditing = false) }
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    fun showLogoutConfirm()    { _overlay.update { it.copy(showLogoutConfirm = true) } }
    fun dismissLogoutConfirm() { _overlay.update { it.copy(showLogoutConfirm = false) } }

    /** iOS: logout() — clears isLoggedIn after a short delay so sheet can dismiss */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
