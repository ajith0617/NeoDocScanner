package com.example.neodocscanner.core.domain.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A checklist row within an ApplicationInstance vault.
 * Defines which document types are accepted and routing rules.
 *
 * iOS equivalent: ApplicationSection.swift (@Model class).
 */
data class ApplicationSection(
    val id: String = "",
    val applicationInstanceId: String? = null,
    val title: String = "",
    val iconName: String = "folder",
    val acceptedClassesJson: String = "[]",  // JSON-encoded [String] of DocumentClass.displayName values
    val isRequired: Boolean = false,
    val maxDocuments: Int = 0,               // 0 = unlimited
    val displayOrder: Int = 0,
    val isCollapsed: Boolean = false
) {
    /** Decoded list of accepted DocumentClass display names. */
    val acceptedClasses: List<String>
        get() {
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(acceptedClassesJson, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
}
