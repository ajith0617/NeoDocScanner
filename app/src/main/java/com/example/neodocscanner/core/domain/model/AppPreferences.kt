package com.example.neodocscanner.core.domain.model

/**
 * User-facing feature preferences.
 *
 * iOS equivalent: AppPreferences enum in AppPreferences.swift
 * (backed by UserDefaults, replaced here by DataStore<Preferences>).
 */
data class AppPreferences(
    /** When true, each scanned document is automatically renamed after ML + OCR complete. */
    val autoRenameEnabled: Boolean = false,

    /**
     * When true  → a separate *_masked file is saved alongside the original (Option A).
     * When false → the original file is overwritten with the masked image (Option B).
     */
    val keepOriginalAfterMask: Boolean = true
)
