package com.example.neodocscanner.feature.vault.data.service.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.neodocscanner.core.domain.model.DocumentClass
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Document image classifier using TensorFlow Lite.
 *
 * iOS equivalent: DocumentClassifierService.swift
 *
 * ── Model contract (MUST match iOS exactly) ───────────────────────────────
 * Model file : document_classifier_v3.tflite  (labels: labels_v3.txt)
 * Input      : [1, 224, 224, 3] Float32 — RAW pixel values 0–255.
 *              The model has MobileNetV2 preprocess_input baked in
 *              (pixel / 127.5 – 1.0 applied INSIDE the graph).
 *              DO NOT pre-normalise here.
 * Output     : [1, 6] Float32 softmax probabilities, one per label line.
 * Labels     : aadhaar_back, aadhaar_front, pan_card, passport, unknown, voter_id
 *
 * ── Confidence rules (MUST match iOS exactly) ─────────────────────────────
 * CONFIDENCE_THRESHOLD   = 0.70  — below this → .other, rawLabel = "unknown"
 * UNKNOWN_FALLBACK_THRESHOLD = 0.40 — if top label is "unknown", use
 *   second-best only if its confidence ≥ 0.40 and it is not "unknown"
 *
 * ── Label → DocumentClass mapping (same substring rules as iOS) ───────────
 * "aadhaar" / "aadhar" → AADHAAR
 * "pan"                → PAN
 * "passport"           → PASSPORT
 * "voter"              → VOTER_ID
 * "driving" / "licence"→ DRIVING_LICENCE
 * else                 → OTHER
 */
@Singleton
class MLClassificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "MLClassificationService"
        private const val MODEL_FILE  = "document_classifier_v3.tflite"
        private const val LABELS_FILE = "labels_v3.txt"
        private const val INPUT_SIZE  = 224

        // iOS: confidenceThreshold = 0.70
        private const val CONFIDENCE_THRESHOLD = 0.70f
        // iOS: unknownFallbackThreshold = 0.40
        private const val UNKNOWN_FALLBACK_THRESHOLD = 0.40f
    }

    data class ClassificationResult(
        val documentClass: DocumentClass,
        /** Raw label from the model, e.g. "aadhaar_front", "aadhaar_back". */
        val rawLabel: String,
        val confidence: Float,
        /**
         * "front" | "back" | null — populated for Aadhaar only,
         * derived from the rawLabel suffix (same as iOS _front / _back).
         */
        val aadhaarSide: String?
    ) {
        val isReliable: Boolean get() = confidence >= CONFIDENCE_THRESHOLD
    }

    private val interpreter: Interpreter? by lazy { loadInterpreter() }
    private val labels: List<String> by lazy { loadLabels() }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Classifies [bitmap] and returns a [ClassificationResult].
     *
     * Mirrors iOS DocumentClassifierService.classify(image:) step-by-step:
     *   Step 1 — Resize to 224×224
     *   Step 2 — Build raw Float32 buffer (0–255, no normalisation)
     *   Step 3 — Run inference
     *   Step 4 — Find best label (argmax)
     *   Step 5 — Unknown fallback (second-best if ≥ 0.40)
     *   Step 6 — Confidence gate (< 0.70 → other)
     *   Step 7 — Map label → DocumentClass
     */
    fun classify(bitmap: Bitmap): ClassificationResult {
        val interp = interpreter ?: run {
            Log.w(TAG, "Model not loaded — returning OTHER")
            return ClassificationResult(DocumentClass.OTHER, "unknown", 0f, null)
        }
        if (labels.isEmpty()) {
            Log.w(TAG, "Labels not loaded — returning OTHER")
            return ClassificationResult(DocumentClass.OTHER, "unknown", 0f, null)
        }

        return runCatching {
            // Step 1 — Resize to 224×224
            val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // Step 2 — Build raw Float32 buffer (DO NOT normalise — model does it internally)
            // iOS: "Feed raw pixel values [0, 255] as Float32."
            val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val pixel = resized.getPixel(x, y)
                    inputBuffer.putFloat(Color.red(pixel).toFloat())    // raw 0–255
                    inputBuffer.putFloat(Color.green(pixel).toFloat())
                    inputBuffer.putFloat(Color.blue(pixel).toFloat())
                }
            }
            if (resized != bitmap) resized.recycle()
            inputBuffer.rewind()

            // Step 3 — Run inference
            val output = Array(1) { FloatArray(labels.size) }
            interp.run(inputBuffer, output)
            val scores = output[0]

            // Step 4 — Argmax
            val bestIdx = scores.indices.maxByOrNull { scores[it] } ?: labels.lastIndex
            var rawLabel   = labels[bestIdx]
            var confidence = scores[bestIdx]

            // Step 5 — Unknown fallback (iOS: if top == "unknown", try second-best)
            if (rawLabel == "unknown") {
                val sortedIdx = scores.indices.sortedByDescending { scores[it] }
                if (sortedIdx.size > 1) {
                    val secondIdx  = sortedIdx[1]
                    val secondLabel = labels[secondIdx]
                    val secondConf  = scores[secondIdx]
                    if (secondLabel != "unknown" && secondConf >= UNKNOWN_FALLBACK_THRESHOLD) {
                        rawLabel   = secondLabel
                        confidence = secondConf
                    }
                }
            }

            // Step 6 — Confidence gate
            if (confidence < CONFIDENCE_THRESHOLD) {
                return@runCatching ClassificationResult(
                    documentClass = DocumentClass.OTHER,
                    rawLabel      = "unknown",
                    confidence    = confidence,
                    aadhaarSide   = null
                )
            }

            // Step 7 — Map label → DocumentClass
            val docClass = mapToDocumentClass(rawLabel)

            // Aadhaar side: derived from rawLabel suffix "_front" / "_back"
            // iOS: rawLabel suffix sets doc.aadhaarSide = "front" / "back"
            val aadhaarSide = when {
                docClass == DocumentClass.AADHAAR && rawLabel.endsWith("_front") -> "front"
                docClass == DocumentClass.AADHAAR && rawLabel.endsWith("_back")  -> "back"
                else -> null
            }

            ClassificationResult(
                documentClass = docClass,
                rawLabel      = rawLabel,
                confidence    = confidence,
                aadhaarSide   = aadhaarSide
            )
        }.onFailure { Log.e(TAG, "classify failed", it) }
            .getOrDefault(ClassificationResult(DocumentClass.OTHER, "unknown", 0f, null))
    }

    // ── Label → DocumentClass mapping ─────────────────────────────────────────

    /**
     * Maps a raw label string to [DocumentClass] using substring rules.
     * iOS: mapToDocumentClass(rawLabel:) — identical logic.
     */
    private fun mapToDocumentClass(rawLabel: String): DocumentClass {
        val lower = rawLabel.lowercase()
        return when {
            lower.contains("aadhaar") || lower.contains("aadhar") -> DocumentClass.AADHAAR
            lower.contains("pan")                                  -> DocumentClass.PAN
            lower.contains("passport")                             -> DocumentClass.PASSPORT
            lower.contains("voter")                                -> DocumentClass.VOTER_ID
            lower.contains("driving") || lower.contains("licence") -> DocumentClass.DRIVING_LICENCE
            else                                                   -> DocumentClass.OTHER
        }
    }

    // ── Model / labels loading ────────────────────────────────────────────────

    private fun loadLabels(): List<String> = runCatching {
        context.assets.open(LABELS_FILE)
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }.onFailure {
        Log.w(TAG, "labels_v3.txt not found in assets — ML classification will fall back to OTHER. " +
                "Place labels_v3.txt in app/src/main/assets/")
    }.getOrDefault(emptyList())

    private fun loadInterpreter(): Interpreter? = runCatching {
        val model = loadModelFile()
        val options = Interpreter.Options().apply { numThreads = 2 }
        Interpreter(model, options)
    }.onFailure {
        Log.w(TAG, "$MODEL_FILE not found in assets. " +
                "Place the model file in app/src/main/assets/ to enable ML classification.")
    }.getOrNull()

    private fun loadModelFile(): MappedByteBuffer {
        val assetFd = context.assets.openFd(MODEL_FILE)
        val stream  = FileInputStream(assetFd.fileDescriptor)
        return stream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }
}
