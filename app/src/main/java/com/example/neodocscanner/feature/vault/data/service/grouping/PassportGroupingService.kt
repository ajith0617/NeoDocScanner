package com.example.neodocscanner.feature.vault.data.service.grouping

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pairs Passport data pages with their address pages captured in the same vault.
 *
 * iOS equivalent: PassportGroupingService.swift
 *
 * ── Pairing priority (MUST match iOS exactly) ─────────────────────────────
 * P1  Exact passport number match                           → definitive
 * P2  Fuzzy passport number match (Levenshtein ≤ 2)        → confident
 *     (handles single-character OCR errors between pages)
 * P3  Session proximity (address.pageIndex == data.pageIndex + 1) → tentative
 * P4  Unresolved — returned to caller
 */
@Singleton
class PassportGroupingService @Inject constructor() {

    // ── Public types (mirrors iOS PassportDocumentSnapshot + PassportPairingDecision) ─

    data class PassportDocumentSnapshot(
        val id: String,
        val passportSide: String?,      // "data" | "address" | null
        val passportNumber: String?,
        val scanSessionId: String?,
        val pageIndex: Int?,
        val groupId: String?
    )

    enum class PairingConfidence {
        DEFINITIVE,   // exact passport number match
        CONFIDENT,    // fuzzy match (Levenshtein ≤ 2)
        TENTATIVE     // session proximity
    }

    data class PassportPairingDecision(
        val dataPageId: String,
        val addressPageId: String,
        val confidence: PairingConfidence
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Processes [snapshots] and returns (decisions, unresolvedIds).
     * iOS equivalent: PassportGroupingService.process(snapshots:)
     */
    fun process(
        snapshots: List<PassportDocumentSnapshot>
    ): Pair<List<PassportPairingDecision>, List<String>> {

        val passport = snapshots.filter {
            (it.passportSide == "data" || it.passportSide == "address") && it.groupId == null
        }

        val dataPages    = passport.filter { it.passportSide == "data"    }.toMutableList()
        val addressPages = passport.filter { it.passportSide == "address" }.toMutableList()

        Log.d(TAG, "process() | data: ${dataPages.size} | address: ${addressPages.size}")

        val decisions = mutableListOf<PassportPairingDecision>()

        // P1: Exact number match
        val p1 = matchByExactNumber(dataPages, addressPages)
        Log.d(TAG, "P1 (exact no.): ${p1.size} match(es)")
        decisions += p1

        // P2: Fuzzy number match (Levenshtein ≤ 2)
        val p2 = matchByFuzzyNumber(dataPages, addressPages)
        Log.d(TAG, "P2 (fuzzy no.): ${p2.size} match(es)")
        decisions += p2

        // P3: Session proximity
        val p3 = matchBySessionProximity(dataPages, addressPages)
        Log.d(TAG, "P3 (session): ${p3.size} match(es)")
        decisions += p3

        val unresolvedIds = (dataPages + addressPages).map { it.id }
        Log.d(TAG, "Total decisions: ${decisions.size} | unresolved: ${unresolvedIds.size}")

        return Pair(decisions, unresolvedIds)
    }

    // ── P1: Exact number match ────────────────────────────────────────────────

    private fun matchByExactNumber(
        dataPages:    MutableList<PassportDocumentSnapshot>,
        addressPages: MutableList<PassportDocumentSnapshot>
    ): List<PassportPairingDecision> {
        val decisions     = mutableListOf<PassportPairingDecision>()
        val matchedDataIdx    = mutableListOf<Int>()
        val matchedAddressIdx = mutableListOf<Int>()

        for ((di, data) in dataPages.withIndex()) {
            val num = data.passportNumber?.takeIf { it.isNotEmpty() } ?: continue

            val ai = addressPages.indexOfFirst { addr ->
                addr.passportNumber == num &&
                !matchedAddressIdx.contains(addressPages.indexOf(addr))
            }
            if (ai >= 0) {
                Log.d(TAG, "P1 ✅ matched data ${data.id.take(8)} ↔ address ${addressPages[ai].id.take(8)} | number: $num")
                decisions += PassportPairingDecision(data.id, addressPages[ai].id, PairingConfidence.DEFINITIVE)
                matchedDataIdx    += di
                matchedAddressIdx += ai
            }
        }

        for (i in matchedAddressIdx.sortedDescending()) addressPages.removeAt(i)
        for (i in matchedDataIdx.sortedDescending())    dataPages.removeAt(i)
        return decisions
    }

    // ── P2: Fuzzy number match ────────────────────────────────────────────────

    /**
     * Handles single-character OCR errors between data page and address page
     * reads of the same passport number (e.g. "R8043678" vs "R8043878").
     * iOS: matchByFuzzyNumber — Levenshtein ≤ 2
     */
    private fun matchByFuzzyNumber(
        dataPages:    MutableList<PassportDocumentSnapshot>,
        addressPages: MutableList<PassportDocumentSnapshot>
    ): List<PassportPairingDecision> {
        val decisions     = mutableListOf<PassportPairingDecision>()
        val matchedDataIdx    = mutableListOf<Int>()
        val matchedAddressIdx = mutableListOf<Int>()

        for ((di, data) in dataPages.withIndex()) {
            val num = data.passportNumber?.takeIf { it.isNotEmpty() } ?: continue

            var bestAI   = -1
            var bestDist = Int.MAX_VALUE

            for ((ai, addr) in addressPages.withIndex()) {
                if (matchedAddressIdx.contains(ai)) continue
                val addrNum = addr.passportNumber?.takeIf { it.isNotEmpty() } ?: continue
                val dist = levenshtein(num, addrNum)
                if (dist <= 2 && dist < bestDist) {
                    bestDist = dist
                    bestAI   = ai
                }
            }

            if (bestAI >= 0) {
                Log.d(TAG, "P2 ✅ fuzzy matched data ${data.id.take(8)} ↔ address ${addressPages[bestAI].id.take(8)} | numbers: '$num' ↔ '${addressPages[bestAI].passportNumber}', dist: $bestDist")
                decisions += PassportPairingDecision(data.id, addressPages[bestAI].id, PairingConfidence.CONFIDENT)
                matchedDataIdx    += di
                matchedAddressIdx += bestAI
            }
        }

        for (i in matchedAddressIdx.sortedDescending()) addressPages.removeAt(i)
        for (i in matchedDataIdx.sortedDescending())    dataPages.removeAt(i)
        return decisions
    }

    // ── P3: Session proximity ─────────────────────────────────────────────────

    private fun matchBySessionProximity(
        dataPages:    MutableList<PassportDocumentSnapshot>,
        addressPages: MutableList<PassportDocumentSnapshot>
    ): List<PassportPairingDecision> {
        val decisions     = mutableListOf<PassportPairingDecision>()
        val matchedDataIdx    = mutableListOf<Int>()
        val matchedAddressIdx = mutableListOf<Int>()

        for ((di, data) in dataPages.withIndex()) {
            val sessionId = data.scanSessionId ?: continue
            val dataIndex = data.pageIndex     ?: continue

            val ai = addressPages.indexOfFirst { addr ->
                addr.scanSessionId == sessionId &&
                addr.pageIndex == dataIndex + 1 &&
                !matchedAddressIdx.contains(addressPages.indexOf(addr))
            }
            if (ai >= 0) {
                Log.d(TAG, "P3 ✅ session prox matched data ${data.id.take(8)} ↔ address ${addressPages[ai].id.take(8)}")
                decisions += PassportPairingDecision(data.id, addressPages[ai].id, PairingConfidence.TENTATIVE)
                matchedDataIdx    += di
                matchedAddressIdx += ai
            }
        }

        for (i in matchedAddressIdx.sortedDescending()) addressPages.removeAt(i)
        for (i in matchedDataIdx.sortedDescending())    dataPages.removeAt(i)
        return decisions
    }

    // ── Levenshtein distance (same as iOS PassportGroupingService.levenshtein) ─

    /**
     * Standard iterative Levenshtein edit distance — case-insensitive.
     * iOS: PassportGroupingService.levenshtein(_:_:)
     */
    private fun levenshtein(a: String, b: String): Int {
        val s = a.lowercase().toCharArray()
        val t = b.lowercase().toCharArray()
        val m = s.size; val n = t.size
        if (m == 0) return n
        if (n == 0) return m

        var row = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = row[0]
            row[0] = i
            for (j in 1..n) {
                val temp = row[j]
                row[j] = if (s[i-1] == t[j-1]) prev
                         else 1 + minOf(prev, row[j], row[j-1])
                prev = temp
            }
        }
        return row[n]
    }

    companion object { private const val TAG = "PassportGroupingService" }
}
