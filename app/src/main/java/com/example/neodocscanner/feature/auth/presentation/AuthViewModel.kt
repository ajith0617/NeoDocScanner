package com.example.neodocscanner.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neodocscanner.feature.auth.domain.repository.AuthRepository
import com.example.neodocscanner.feature.auth.domain.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

/**
 * Snapshot of what the LoginScreen needs to render.
 *
 * iOS equivalent: The @State properties scattered across LoginView.swift:
 * username, password, isLoading, errorMessage, etc.
 */
data class AuthUiState(
    val username: String         = "",
    val password: String         = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean       = false,
    val errorMessage: String?    = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Manages all auth-related state and coordinates login / logout operations.
 *
 * iOS equivalent: The ad-hoc @AppStorage + @State handling in ContentView.swift
 * and LoginView.swift, consolidated into a single observable object.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // ── Session state (persisted) ─────────────────────────────────────────────

    /**
     * True once DataStore confirms the user is logged in.
     * Drives navigation decisions in AppNavigation.
     */
    val isLoggedIn: StateFlow<Boolean?> = authRepository.isLoggedIn
        .stateIn(
            scope         = viewModelScope,
            started       = SharingStarted.WhileSubscribed(5_000),
            initialValue  = null   // null = "not yet read from DataStore"
        )

    /** Persisted display name shown in the Hub header. */
    val loggedInUsername: StateFlow<String> = authRepository.username
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    // ── Login form state ──────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // ── User input handlers ───────────────────────────────────────────────────

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    // ── Auth actions ──────────────────────────────────────────────────────────

    /**
     * Triggers login with the current username/password from [uiState].
     * On success, [isLoggedIn] updates to true, driving navigation to Hub.
     */
    fun login() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.login(
                username = state.username,
                password = state.password
            )
            when (result) {
                is AuthResult.Success -> {
                    // isLoggedIn StateFlow will flip to true automatically via DataStore;
                    // AppNavigation will respond and navigate to Hub.
                    _uiState.update { it.copy(isLoading = false) }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    /**
     * Clears the session. AppNavigation will detect isLoggedIn = false and
     * pop back to the Login screen.
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
