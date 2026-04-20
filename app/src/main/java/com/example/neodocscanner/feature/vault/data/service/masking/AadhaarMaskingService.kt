package com.example.neodocscanner.feature.vault.data.service.masking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import com.example.neodocscanner.core.domain.model.TextRegion
import com.example.neodocscanner.feature.vault.data.service.scanner.DocumentFileManager
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Masks the first 8 digits of every Aadhaar UID found in a document image
 * and computes the SHA-256 hash of the raw 12-digit UID.
 *
 * iOS equivalent: AadhaarMaskingService.swift
 *
 * ── Behaviour (matches iOS Option A — keepOriginal = true default) ────────
 * Writes a separate "<name>_masked.png" alongside the original.
 * Document.maskedRelativePath points to the masked copy.
 * Original file is preserved untouched.
 *
 * ── Mask rendering (identical to iOS renderMasked) ────────────────────────
 * 1. Black filled rectangle over the first-8-digit region
 *    (slightly expanded: dx = -6, dy = -4 in normalised space equivalent)
 * 2. "XXXX XXXX" white label centred over the black rectangle
 *    font size = max(maskRect.height * 0.50, 10) — same as iOS
 *
 * ── UID regex (exact patterns from iOS AadhaarMaskingService.swift) ────────
 * Spaced  : (?<!\d )\d{4} \d{4} \d{4}(?! \d{4})   — 12-digit, not VID
 * Compact : (?<!\d)\d{12}(?!\d)
 *
 * ── Hash (same as iOS CryptoKit.SHA256) ───────────────────────────────────
 * SHA-256 of raw 12-digit UID string (spaces stripped), hex-encoded lowercase.
 */
@Singleton
class AadhaarMaskingService @Inject constructor(
    private val fileManager: DocumentFileManager
) {

    companion object {
        private const val TAG = "AadhaarMaskingService"

        // iOS: spacedUIDRegex — matches "XXXX XXXX XXXX" but NOT 16-digit VID
        // Lookbehind (?<!\d ) rejects mid-VID; lookahead (?! \d{4}) rejects VID tail
        private val REGEX_UID_SPACED = Regex("""(?<!\d )\d{4} \d{4} \d{4}(?! \d{4})""")

        // iOS: compactUIDRegex — exactly 12 consecutive digits, no adjacent digit
        private val REGEX_UID_COMPACT = Regex("""(?<!\d)\d{12}(?!\d)""")

        private const val MASKED_FILE_SUFFIX = "_masked.png"
    }

    // ── Result (mirrors iOS MaskResult) ───────────────────────────────────────

    data class MaskingResult(
        /** Relative path of the saved masked file (Option A). */
        val maskedRelativePath: String?,
        /**
         * SHA-256 hash of the raw 12-digit UID.
         * Used by AadhaarGroupingService to match front/back pairs.
         * null when no UID was found (e.g. back-side with no UID visible).
         */
        val uidHash: String?
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Masks the Aadhaar UID(s) in the image at [originalRelativePath].
     *
     * iOS equivalent: AadhaarMaskingService.mask(fileURL:document:)
     *
     * Steps:
     *   1. Find UID text regions via regex on OCR [regions] text
     *   2. Collect raw 12-digit UID strings for hashing
     *   3. Determine mask rectangles (first 8 digits = spaced ? 9 chars : 8 chars)
     *   4. Single render pass — black fill + "XXXX XXXX" white label for every match
     *   5. Save masked copy; return path + UID hash
     */
    fun maskAndHash(
        originalRelativePath: String,
        regions: List<TextRegion>,
        instanceId: String,
        documentId: String
    ): MaskingResult {
        val bitmap = fileManager.loadBitmap(originalRelativePath) ?: run {
            Log.w(TAG, "Could not load bitmap for masking")
            return MaskingResult(null, null)
        }

        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()

        data class MaskBox(
            val rectF: RectF,
            val isSpaced: Boolean   // for "XXXX XXXX" label placement
        )

        val maskBoxes = mutableListOf<MaskBox>()
        val rawUIDs   = mutableListOf<String>()   // de-duplicated 12-digit strings

        for (region in regions) {
            val text = region.text

            // Try spaced pattern first, then compact (same priority as iOS)
            var matchedThisRegion = false

            for (regex in listOf(REGEX_UID_SPACED, REGEX_UID_COMPACT)) {
                val matches = regex.findAll(text)
                var foundInThisRegex = false

                for (match in matches) {
                    val matchedText = match.value
                    val digits = matchedText.filter { it.isDigit() }
                    if (digits.length == 12 && !rawUIDs.contains(digits)) {
                        rawUIDs.add(digits)
                    }

                    // Calculate what fraction of the matched text is the first 8 digits
                    // Spaced "XXXX XXXX XXXX": first 9 chars = "XXXX XXXX" (8 digits + 1 space)
                    // Compact "\d{12}":         first 8 chars = first 8 digits
                    val isSpaced = matchedText.contains(" ")
                    val maskCharCount = if (isSpaced) 9 else 8
                    val totalChars    = matchedText.length  // 14 for spaced, 12 for compact

                    // Fraction of the region width that the mask occupies
                    val maskFraction = maskCharCount.toFloat() / totalChars.toFloat()

                    // Denormalise region coordinates (0-1) → pixel coordinates
                    // Android ML Kit uses top-left origin (same as UIKit after Vision flip)
                    val left   = region.x * imgW
                    val top    = region.y * imgH
                    val right  = (region.x + region.w) * imgW
                    val bottom = (region.y + region.h) * imgH

                    // Mask rectangle covers the first 8 digits (left portion of region)
                    val maskRight = left + (right - left) * maskFraction

                    // iOS: insetBy(dx: -6, cy: -4) — expand slightly so no digit edge bleeds
                    val expandX = (right - left) * 0.04f
                    val expandY = (bottom - top) * 0.08f

                    maskBoxes.add(MaskBox(
                        rectF = RectF(
                            left   - expandX,
                            top    - expandY,
                            maskRight + expandX,
                            bottom + expandY
                        ),
                        isSpaced = isSpaced
                    ))
                    foundInThisRegex = true
                }

                if (foundInThisRegex) {
                    matchedThisRegion = true
                    break   // first matching pattern wins (same as iOS)
                }
            }
            if (matchedThisRegion) { /* no-op — break out of region loop? No, check all regions */ }
        }

        if (maskBoxes.isEmpty() && rawUIDs.isEmpty()) {
            bitmap.recycle()
            return MaskingResult(null, null)
        }

        // UID hash from the first captured UID (same physical card → same 12-digit number)
        val uidHash: String? = rawUIDs.firstOrNull()?.let { sha256(it) }

        // Single render pass — apply all masks (iOS: renderMasked)
        val masked = renderMasked(bitmap, maskBoxes.map { it.rectF })
        bitmap.recycle()

        // Save masked copy
        val maskedPath = fileManager.saveBitmap(masked, instanceId, documentId, MASKED_FILE_SUFFIX)
        masked.recycle()

        return MaskingResult(maskedRelativePath = maskedPath, uidHash = uidHash)
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    /**
     * Draws the original image once, then overlays a black mask + "XXXX XXXX" label
     * for every box in [maskBoxes] — all in a single render pass.
     *
     * iOS equivalent: AadhaarMaskingService.renderMasked(image:normalizedBoxes:)
     */
    private fun renderMasked(src: Bitmap, maskBoxes: List<RectF>): Bitmap {
        val mutable = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas  = Canvas(mutable)

        val fillPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        for (maskRect in maskBoxes) {
            // Black fill
            canvas.drawRect(maskRect, fillPaint)

            // "XXXX XXXX" white label centred over the mask
            // iOS: fontSize = max(maskRect.height * 0.50, 10)
            val fontSize  = maxOf(maskRect.height() * 0.50f, 10f)
            val textPaint = Paint().apply {
                color     = Color.WHITE
                textSize  = fontSize
                typeface  = Typeface.MONOSPACE
                isFakeBoldText = true
                isAntiAlias    = true
                textAlign      = Paint.Align.CENTER
            }

            val label   = "XXXX XXXX"
            val centerX = maskRect.centerX()
            // Vertical centre: offset by half of text height
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val centerY = maskRect.centerY() + textBounds.height() / 2f

            canvas.drawText(label, centerX, centerY, textPaint)
        }

        return mutable
    }

    // ── Hashing ───────────────────────────────────────────────────────────────

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of [input].
     * iOS equivalent: CryptoKit.SHA256.hash(data:) → hexString.
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
