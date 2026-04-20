package com.example.neodocscanner.core.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages login session persistence.
 *
 * iOS equivalent: @AppStorage("isLoggedIn") in ContentView.swift +
 * any username stored in UserDefaults.
 *
 * Stored in a dedicated DataStore instance ("neodocs_session") to keep
 * auth state clearly separated from feature preferences.
 */
private val Context.sessionDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "neodocs_session")

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val IS_LOGGED_IN = booleanPreferencesKey("isLoggedIn")
        val USERNAME     = stringPreferencesKey("username")
    }

    val isLoggedIn: Flow<Boolean> = context.sessionDataStore.data
        .map { prefs -> prefs[Keys.IS_LOGGED_IN] ?: false }

    val username: Flow<String> = context.sessionDataStore.data
        .map { prefs -> prefs[Keys.USERNAME] ?: "" }

    suspend fun saveSession(username: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = true
            prefs[Keys.USERNAME]     = username
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = false
            prefs[Keys.USERNAME]     = ""
        }
    }
}
