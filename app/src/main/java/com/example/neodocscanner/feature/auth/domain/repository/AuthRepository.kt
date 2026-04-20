package com.example.neodocscanner.feature.auth.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Contract for authentication + session management.
 *
 * iOS equivalent: The combination of @AppStorage("isLoggedIn") in ContentView.swift
 * and any credentials stored in UserDefaults / Keychain.
 *
 * In this phase the implementation is local (no network).
 * The interface is designed to be drop-in replaceable with an API-backed
 * implementation when real auth is added.
 */
interface AuthRepository {

    /** Live stream of the current login state. Collected by AuthViewModel. */
    val isLoggedIn: Flow<Boolean>

    /** Live stream of the persisted username for display (e.g. in Hub header). */
    val username: Flow<String>

    /**
     * Attempts login with the given credentials.
     *
     * @return [AuthResult.Success] when accepted, [AuthResult.Error] with a
     *         human-readable message otherwise.
     */
    suspend fun login(username: String, password: String): AuthResult

    /** Clears the session — navigates the user back to Login. */
    suspend fun logout()
}

/** ADT for the login operation result. */
sealed class AuthResult {
    data object Success                    : AuthResult()
    data class  Error(val message: String) : AuthResult()
}
