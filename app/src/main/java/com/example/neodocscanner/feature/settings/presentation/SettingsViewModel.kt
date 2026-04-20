package com.example.neodocscanner.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neodocscanner.core.data.local.preferences.AppPreferencesDataStore
import com.example.neodocscanner.core.domain.model.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages app-wide feature settings.
 *
 * iOS equivalent: @AppStorage bindings in SettingsView.swift — the view
 * directly read/wrote UserDefaults via @AppStorage. Here we use a proper
 * ViewModel + DataStore so state is lifecycle-safe and testable.
 *
 * Settings:
 *  1. autoRenameEnabled     — default false
 *  2. keepOriginalAfterMask — default true
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: AppPreferencesDataStore
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesDataStore.preferencesFlow
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPreferences()
        )

    /** iOS: Toggle(isOn: $autoRenameEnabled) */
    fun toggleAutoRename() {
        viewModelScope.launch {
            preferencesDataStore.setAutoRenameEnabled(!preferences.value.autoRenameEnabled)
        }
    }

    /** iOS: Toggle(isOn: $keepOriginalAfterMask) */
    fun toggleKeepOriginalAfterMask() {
        viewModelScope.launch {
            preferencesDataStore.setKeepOriginalAfterMask(!preferences.value.keepOriginalAfterMask)
        }
    }
}
