package com.example.neodocscanner.core.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * A single user-created application vault.
 * e.g. "SBI Home Loan — Mar 2026" is one instance of the "Home Loan" template.
 *
 * iOS equivalent: ApplicationInstance.swift (@Model class).
 */
data class ApplicationInstance(
    val id: String = UUID.randomUUID().toString(),
    val templateId: String = "",
    val templateName: String = "",
    val customName: String = "",
    val iconName: String = "",
    val dateCreated: Long = System.currentTimeMillis(),
    val status: String = "active",

    // Server linking — populated when instance is created from a QR payload
    val serverReferenceId: String? = null,
    val serverApplicantName: String? = null,
    val serverBranch: String? = null,
    val serverMetadata: String? = null,      // raw JSON string from QR payload
    val linkedAt: Long? = null,
    val linkStatus: String = "unlinked"      // "unlinked" | "linked" | "synced"
) {
    val isArchived: Boolean get() = status == "archived"
    val isLinked: Boolean   get() = linkStatus != "unlinked"

    /** Short formatted date — e.g. "20 Mar 2026" */
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            return sdf.format(Date(dateCreated))
        }
}
