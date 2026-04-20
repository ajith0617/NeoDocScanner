package com.example.neodocscanner.core.di

import com.example.neodocscanner.core.data.local.preferences.AppPreferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for DataStore preferences.
 *
 * AppPreferencesDataStore uses @Inject constructor so this module only exists
 * to document intent and reserve space for future preference providers
 * (e.g. encrypted DataStore for sensitive fields).
 *
 * iOS equivalent: AppPreferences.swift (UserDefaults-backed enum).
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    // AppPreferencesDataStore is annotated with @Singleton + @Inject constructor,
    // so Hilt auto-provides it. This module is intentionally sparse — add explicit
    // @Provides functions here when a second DataStore instance is needed.
}
