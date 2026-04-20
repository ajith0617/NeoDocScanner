package com.example.neodocscanner.feature.vault.data.service.ocr

import android.graphics.Bitmap
import android.util.Log
import com.example.neodocscanner.core.domain.model.TextRegion
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optical Character Recognition via ML Kit Text Recognition (Latin script).
 *
 * iOS equivalent: OCRService.swift which uses Vision's VNRecognizeTextRequest
 * with .accurate accuracy level. ML Kit provides equivalent quality with an
 * identical API surface (async callback → coroutine suspension).
 *
 * The recognizer instance is kept as a [Singleton] to avoid repeated
 * initialisation overhead — matches the iOS `lazy` pattern.
 */
@Singleton
class OcrService @Inject constructor() {

    companion object {
        private const val TAG = "OcrService"
        // Minimum confidence for a text block to be included in output
        private const val MIN_CONFIDENCE = 0.4f
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs OCR on [bitmap] and returns the extracted text plus per-block regions.
     * Bounding box coordinates are stored normalised (0–1) relative to [bitmap] dimensions.
     *
     * iOS equivalent: OCRService.recogniseText(image:) async throws.
     */
    suspend fun recognise(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val imgW  = bitmap.width.toFloat().coerceAtLeast(1f)
        val imgH  = bitmap.height.toFloat().coerceAtLeast(1f)

        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(buildResult(visionText, imgW, imgH))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed", e)
                    cont.resume(OcrResult("", emptyList()))
                }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildResult(visionText: Text, imgW: Float, imgH: Float): OcrResult {
        val regions  = mutableListOf<TextRegion>()
        val rawLines = mutableListOf<String>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val confidence = line.confidence ?: 1f
                if (confidence < MIN_CONFIDENCE) continue

                val text = sanitiseLine(line.text)
                if (text.isBlank()) continue

                rawLines.add(text)

                val box = line.boundingBox
                if (box != null) {
                    // Normalise coordinates to 0-1 range
                    regions.add(
                        TextRegion(
                            text = text,
                            x    = box.left.toFloat()    / imgW,
                            y    = box.top.toFloat()     / imgH,
                            w    = box.width().toFloat() / imgW,
                            h    = box.height().toFloat()/ imgH
                        )
                    )
                }
            }
        }

        return OcrResult(
            fullText = rawLines.joinToString("\n"),
            regions  = regions
        )
    }

    /**
     * Cleans a single OCR line.
     * iOS equivalent: OCRService.sanitiseLine() which:
     *   - Strips non-printable / control characters
     *   - Replaces common OCR confusions (O↔0, I↔1 in ID contexts)
     *   - Trims whitespace
     */
    private fun sanitiseLine(raw: String): String =
        raw
            .filter { it.isLetterOrDigit() || it.isWhitespace() || it in "/-:@#." }
            .replace(Regex("\\s{2,}"), " ")
            .trim()
}

// ── Result models ─────────────────────────────────────────────────────────────

data class OcrResult(
    val fullText: String,
    val regions: List<TextRegion>
)
