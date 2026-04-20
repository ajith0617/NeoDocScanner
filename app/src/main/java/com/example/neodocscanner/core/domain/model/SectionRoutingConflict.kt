package com.example.neodocscanner.core.domain.model

import java.util.UUID

/**
 * Represents a mismatch between what the user intended (hint section)
 * and what ML classification detected (mlSection).
 *
 * Queued and presented to the user one at a time via an alert in DocuVaultScreen.
 *
 * iOS equivalent: SectionRoutingConflict struct in DocuVaultViewModel.swift.
 */
data class SectionRoutingConflict(
    val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val detectedLabel: String,      // e.g. "PAN Card"
    val hintSectionId: String,
    val hintSectionTitle: String,
    val mlSectionId: String,
    val mlSectionTitle: String
)
