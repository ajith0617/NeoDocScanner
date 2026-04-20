package com.example.neodocscanner.core.domain.model

/**
 * Lifecycle stage of a document through the serial scan-processing pipeline.
 *
 * Flow: QUEUED → ANALYSING → COMPLETE
 *
 * iOS equivalent: ProcessingStatus enum in Document.swift.
 * [rawValue] matches iOS rawValue for storage consistency.
 */
enum class ProcessingStatus(val rawValue: String) {
    /** Saved to disk; waiting for the pipeline to pick it up. */
    QUEUED("queued"),

    /** Currently being processed (classify → mask → OCR → name). */
    ANALYSING("analysing"),

    /** All pipeline steps complete; card is fully interactive. */
    COMPLETE("complete");

    companion object {
        fun fromRaw(raw: String?): ProcessingStatus =
            entries.find { it.rawValue == raw } ?: COMPLETE
    }
}
