package com.example.neodocscanner.core.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Detected type of an Indian identity document — mirrors iOS DocumentClass enum.
 *
 * [displayName] is the canonical string stored in Room (matches iOS rawValue).
 * This is intentional: data exported / shared between platforms stays consistent.
 */
enum class DocumentClass(val displayName: String) {
    AADHAAR("Aadhaar"),
    PAN("PAN Card"),
    VOTER_ID("Voter ID"),
    DRIVING_LICENCE("Driving Licence"),
    PASSPORT("Passport"),
    OTHER("Other");

    /** Material 3 colour for classification badges — mirrors iOS DocumentClass.color. */
    val badgeColor: Color
        get() = when (this) {
            AADHAAR         -> Color(0xFF1565C0)  // UIDAI blue
            PAN             -> Color(0xFFE65100)  // Income Tax orange
            VOTER_ID        -> Color(0xFF6A1B9A)  // Election Commission purple
            DRIVING_LICENCE -> Color(0xFF2E7D32)  // Transport green
            PASSPORT        -> Color(0xFF1A237E)  // MEA navy/indigo
            OTHER           -> Color(0xFF616161)  // Grey
        }

    /** Material Icons name — used in badge composables. */
    val iconName: String
        get() = when (this) {
            AADHAAR         -> "person_outline"
            PAN             -> "credit_card"
            VOTER_ID        -> "how_to_vote"
            DRIVING_LICENCE -> "directions_car"
            PASSPORT        -> "language"
            OTHER           -> "help_outline"
        }

    companion object {
        /** Maps the stored string back to the enum — defaults to OTHER for unknown values. */
        fun fromRaw(raw: String?): DocumentClass =
            entries.find { it.displayName == raw } ?: OTHER
    }
}
