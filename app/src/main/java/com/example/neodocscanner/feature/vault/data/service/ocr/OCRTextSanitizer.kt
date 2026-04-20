package com.example.neodocscanner.feature.vault.data.service.ocr

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cleans raw OCR output before it is stored or passed to DocumentFieldExtractorService.
 *
 * iOS equivalent: OCRTextSanitizer.swift — three-pass pipeline:
 *   Pass 1  Character filter — strips garbage Unicode (Cyrillic OCR mis-reads,
 *           control chars, zero-width chars, box-drawing symbols, etc.)
 *   Pass 2  Line filter — drops lines that carry no real content
 *   Pass 3  Whitespace pass — trims every line, collapses multiple blank lines
 *
 * Allowed Unicode ranges (same as iOS OCRTextSanitizer.isAllowedScalar):
 *   0x0009, 0x000A, 0x000D    tab / LF / CR
 *   0x0020–0x007E             ASCII printable
 *   0x00A0–0x00FF             Latin-1 Supplement (accented letters for foreign names)
 *   0x0900–0x097F             Devanagari (Hindi text on Aadhaar cards)
 *   0x20B9                    ₹ Indian Rupee sign
 */
@Singleton
class OCRTextSanitizer @Inject constructor() {

    /**
     * Runs the full three-pass sanitisation pipeline on raw OCR text.
     * iOS equivalent: OCRTextSanitizer.sanitize(_:)
     */
    fun sanitize(raw: String): String {
        // Pass 1 — character filter
        val filtered = filterCharacters(raw)

        // Pass 2 & 3 — line filter + whitespace normalisation
        val result = mutableListOf<String>()
        var prevWasBlank = false

        for (line in filtered.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    if (!prevWasBlank) result.add("")
                    prevWasBlank = true
                }
                isNoiseLine(trimmed) -> {
                    // Silently drop noise lines
                }
                else -> {
                    result.add(trimmed)
                    prevWasBlank = false
                }
            }
        }

        return result.joinToString("\n").trim()
    }

    // ── Pass 1: Character Filter ──────────────────────────────────────────────

    private fun filterCharacters(text: String): String {
        return text.filter { ch -> isAllowedChar(ch) }
    }

    private fun isAllowedChar(ch: Char): Boolean {
        val v = ch.code
        return when {
            v == 0x0009 || v == 0x000A || v == 0x000D -> true  // tab, LF, CR
            v in 0x0020..0x007E -> true                         // ASCII printable
            v in 0x00A0..0x00FF -> true                         // Latin-1 Supplement
            v in 0x0900..0x097F -> true                         // Devanagari
            v == 0x20B9          -> true                         // ₹ Indian Rupee
            else                 -> false
        }
    }

    // ── Pass 2: Line Filter ───────────────────────────────────────────────────

    /**
     * Returns true for lines that carry no meaningful OCR content.
     *
     * Dropped line types (same as iOS OCRTextSanitizer.isNoiseLine):
     *   • Lines with fewer than 2 letters or digits (e.g. lone "|", ":", "1:")
     *   • Separator lines made of one repeated non-alphanumeric character
     *     (e.g. "----------", "==========", "• • • • •")
     */
    private fun isNoiseLine(line: String): Boolean {
        val usefulCount = line.count { it.isLetter() || it.isDigit() }
        if (usefulCount < 2) return true

        // Separator check: every non-whitespace char is the same non-alphanumeric glyph
        val nonSpace = line.filter { !it.isWhitespace() }
        if (nonSpace.length >= 2) {
            val first = nonSpace.first()
            if (!first.isLetter() && !first.isDigit() && nonSpace.all { it == first }) {
                return true
            }
        }
        return false
    }
}
