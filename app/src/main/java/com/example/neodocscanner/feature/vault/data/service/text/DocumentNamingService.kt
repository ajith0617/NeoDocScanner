package com.example.neodocscanner.feature.vault.data.service.text

import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives a human-readable filename from extracted fields + document class.
 *
 * iOS equivalent: SmartNamingService.swift
 *
 * ── Naming convention (MUST match iOS exactly) ────────────────────────────
 * {Name}_{ClassPrefix}_{timestamp}    — when OCR name is available (Priority 1)
 * {ClassPrefix}_{timestamp}           — when no name (Priority 3)
 * "Document" class → "Document_{timestamp}"
 *
 * Priority order (same as iOS SmartNamingService.generateBaseName):
 *   1. Extracted name from DocumentFieldExtractorService (highest quality)
 *   2. Heuristic extraction from OCR text (fallback — not used here; done in extractor)
 *   3. Class + timestamp (when no name can be found)
 *
 * ── Class prefixes (exact iOS SmartNamingService.prefix(for:)) ────────────
 * AADHAAR         → "Aadhaar"
 * PAN             → "PAN"
 * PASSPORT        → "Passport"
 * VOTER_ID        → "VoterID"
 * DRIVING_LICENCE → "DL"
 * OTHER           → "Document"
 *
 * ── Timestamp format ─────────────────────────────────────────────────────
 * "yyyyMMdd_HHmm"  — same as iOS DateFormatter("yyyyMMdd_HHmm")
 *
 * ── Name sanitisation (same as iOS SmartNamingService.sanitise) ──────────
 * 1. Title-case if input is entirely uppercase
 * 2. Keep only letters, digits, spaces
 * 3. Limit to first 3 words joined with "_"
 * 4. Hard cap at 30 characters
 * 5. Return nil if nothing useful remains
 */
@Singleton
class DocumentNamingService @Inject constructor() {

    fun name(
        documentClass: DocumentClass,
        fields: List<DocumentField>,
        dateAdded: Date = Date()
    ): String {
        val classPrefix = prefixFor(documentClass)
        val timestamp   = timestampString(dateAdded)

        // Priority 1: use the "Name" field extracted by DocumentFieldExtractorService
        val extractedName = fields.firstOrNull { it.label == "Name" }?.value
        val sanitisedName = extractedName?.let { sanitise(it) }

        return if (!sanitisedName.isNullOrEmpty()) {
            "${sanitisedName}_${classPrefix}_${timestamp}"
        } else {
            "${classPrefix}_${timestamp}"
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** iOS: SmartNamingService.prefix(for:) */
    fun prefixFor(cls: DocumentClass): String = when (cls) {
        DocumentClass.AADHAAR         -> "Aadhaar"
        DocumentClass.PAN             -> "PAN"
        DocumentClass.PASSPORT        -> "Passport"
        DocumentClass.VOTER_ID        -> "VoterID"
        DocumentClass.DRIVING_LICENCE -> "DL"
        DocumentClass.OTHER           -> "Document"
    }

    /** iOS: SmartNamingService.timestampString(from:) — "yyyyMMdd_HHmm" */
    fun timestampString(date: Date = Date()): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(date)

    /**
     * Cleans an extracted name for safe use in a filename.
     *
     * iOS: SmartNamingService.sanitise(_:)
     *   1. Title-case if input is entirely uppercase
     *   2. Strip characters not valid in filenames (keep letters, digits, spaces)
     *   3. Limit to first 3 words joined with underscores
     *   4. Hard cap at 30 characters
     *   5. Return null if nothing useful remains
     */
    fun sanitise(name: String): String? {
        // Title-case if input is entirely uppercase
        val input = if (name == name.uppercase()) {
            name.split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
        } else {
            name
        }

        // Strip characters not valid in filenames
        val cleaned = input
            .filter { it.isLetter() || it.isDigit() || it == ' ' }
            .trim()

        if (cleaned.isEmpty()) return null

        // Limit to first 3 words joined with underscores
        val words  = cleaned.split(" ").filter { it.isNotEmpty() }.take(3)
        val joined = words.joinToString("_")

        // Hard cap at 30 characters
        val result = if (joined.length > 30) joined.substring(0, 30) else joined
        return result.ifEmpty { null }
    }
}
