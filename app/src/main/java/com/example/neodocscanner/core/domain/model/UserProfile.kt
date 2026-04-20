package com.example.neodocscanner.core.domain.model

/**
 * User profile information, persisted via ProfileDataStore.
 *
 * iOS equivalent: @AppStorage("profileName/Designation/Organisation/Email")
 * fields in ProfileView.swift.
 */
data class UserProfile(
    val name: String         = "",
    val designation: String  = "",
    val organisation: String = "",
    val email: String        = ""
) {
    /** Up to two capital initials from the full name — shown in the avatar circle. */
    val initials: String
        get() = name.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
}
