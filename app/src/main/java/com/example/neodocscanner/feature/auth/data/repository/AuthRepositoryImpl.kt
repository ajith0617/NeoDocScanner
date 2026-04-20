package com.example.neodocscanner.feature.auth.data.repository

import com.example.neodocscanner.core.data.local.preferences.SessionDataStore
import com.example.neodocscanner.feature.auth.domain.repository.AuthRepository
import com.example.neodocscanner.feature.auth.domain.repository.AuthResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local implementation of [AuthRepository].
 *
 * Phase 1 (this module): validates non-empty credentials locally and
 * persists the session in DataStore.
 *
 * Future: replace [validateCredentials] with an API call (Retrofit/Ktor)
 * without changing the interface or the ViewModel.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionDataStore: SessionDataStore
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = sessionDataStore.isLoggedIn
    override val username: Flow<String>    = sessionDataStore.username

    override suspend fun login(username: String, password: String): AuthResult {
        val trimmedUser = username.trim()
        val trimmedPass = password.trim()

        val validationError = validateCredentials(trimmedUser, trimmedPass)
        if (validationError != null) return AuthResult.Error(validationError)

        sessionDataStore.saveSession(username = trimmedUser)
        return AuthResult.Success
    }

    override suspend fun logout() {
        sessionDataStore.clearSession()
    }

    // ── Local validation ──────────────────────────────────────────────────────

    /**
     * Returns an error message string, or null if credentials are valid.
     *
     * Swap this out for a real API call in a future auth module.
     */
    private fun validateCredentials(username: String, password: String): String? {
        if (username.isBlank()) return "Please enter your username."
        if (password.isBlank()) return "Please enter your password."
        if (username.length < 3) return "Username must be at least 3 characters."
        if (password.length < 4) return "Password must be at least 4 characters."
        return null
    }
}
