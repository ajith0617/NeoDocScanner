package com.example.neodocscanner.core.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.neodocscanner.core.domain.model.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replaces iOS UserDefaults / AppPreferences enum.
 *
 * Uses Jetpack DataStore<Preferences> — the modern, coroutine-safe alternative
 * to SharedPreferences (analogous to UserDefaults on iOS).
 *
 * iOS equivalent: AppPreferences.swift (enum backed by UserDefaults).
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "neodocs_preferences"
)

@Singleton
class AppPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DARK_THEME_ENABLED      = booleanPreferencesKey("darkThemeEnabled")
        val AUTO_RENAME_ENABLED     = booleanPreferencesKey("autoRenameEnabled")
        val KEEP_ORIGINAL_AFTER_MASK = booleanPreferencesKey("keepOriginalAfterMask")
    }

    /** Reactive stream of the current preferences snapshot. */
    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            darkThemeEnabled       = prefs[Keys.DARK_THEME_ENABLED]       ?: false,
            autoRenameEnabled      = prefs[Keys.AUTO_RENAME_ENABLED]      ?: false,
            keepOriginalAfterMask  = prefs[Keys.KEEP_ORIGINAL_AFTER_MASK] ?: true
        )
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME_ENABLED] = enabled }
    }

    suspend fun setAutoRenameEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RENAME_ENABLED] = enabled }
    }

    suspend fun setKeepOriginalAfterMask(keep: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_ORIGINAL_AFTER_MASK] = keep }
    }
}
