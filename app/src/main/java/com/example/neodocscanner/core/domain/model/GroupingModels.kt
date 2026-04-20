package com.example.neodocscanner.core.domain.model

/**
 * Snapshot of the fields AadhaarGroupingService reads from a Document.
 * Plain data class — keeps the service free of Room/data-layer imports.
 *
 * iOS equivalent: AadhaarDocumentSnapshot struct in AadhaarGroupingService.swift.
 */
data class AadhaarDocumentSnapshot(
    val id: String,
    val aadhaarSide: String?,        // "front" | "back" | null
    val aadhaarUidHash: String?,     // SHA-256 of raw UID
    val extractedText: String?,
    val scanSessionId: String?,
    val pageIndex: Int?,
    val groupId: String?
)

/**
 * iOS equivalent: GroupingDecision struct in AadhaarGroupingService.swift.
 */
data class AadhaarGroupingDecision(
    val frontId: String,
    val backId: String,
    val confidence: Confidence
) {
    enum class Confidence { DEFINITIVE, CONFIDENT, TENTATIVE }
}

/**
 * Snapshot of the fields PassportGroupingService reads from a Document.
 *
 * iOS equivalent: PassportDocumentSnapshot struct in PassportGroupingService.swift.
 */
data class PassportDocumentSnapshot(
    val id: String,
    val passportSide: String?,       // "data" | "address" | null
    val passportNumber: String?,
    val scanSessionId: String?,
    val pageIndex: Int?,
    val groupId: String?
)

/**
 * iOS equivalent: PassportPairingDecision struct in PassportGroupingService.swift.
 */
data class PassportPairingDecision(
    val dataPageId: String,
    val addressPageId: String,
    val confidence: Confidence
) {
    enum class Confidence { DEFINITIVE, CONFIDENT, TENTATIVE }
}

/**
 * Snapshot of a section's routing metadata for SectionRoutingService.
 *
 * iOS equivalent: SectionSnapshot struct in SectionRoutingService.swift.
 */
data class SectionSnapshot(
    val id: String,
    val acceptedClasses: List<String>,
    val isRequired: Boolean,
    val currentDocCount: Int,
    val maxDocuments: Int            // 0 = unlimited
)
