package com.example.neodocscanner.core.domain.model

/**
 * Drives the scan-processing progress banner in DocuVaultScreen.
 *
 * Pipeline flow: IDLE → SAVING → ANALYSING → ROUTING → DONE → IDLE
 *
 * iOS equivalent: DocuVaultViewModel.ScanProcessingPhase enum.
 */
sealed class ScanProcessingPhase {
    data object Idle : ScanProcessingPhase()

    data class Saving(val done: Int, val total: Int) : ScanProcessingPhase() {
        val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
        val message: String get() = "Saving $done of $total…"
    }

    data class Analysing(val done: Int, val total: Int) : ScanProcessingPhase() {
        val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
        val message: String get() = "Analysing $done of $total…"
    }

    data object Routing : ScanProcessingPhase() {
        val message: String get() = "Organising documents…"
    }

    data class Done(val count: Int) : ScanProcessingPhase() {
        val message: String get() = if (count == 1) "1 document added" else "$count documents added"
    }
}
