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
    private val nonAlphaNum = Regex("[^A-Za-z0-9]")

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

        val passport = snapshots
            .filter { it.groupId == null }
            .map { it.copy(passportSide = canonicalPassportSide(it.passportSide)) }

        val dataPages    = passport.filter { it.passportSide == "data"    }.toMutableList()
        val addressPages = passport.filter { it.passportSide == "address" }.toMutableList()
        val unknownPages = passport.filter { it.passportSide != "data" && it.passportSide != "address" }.toMutableList()

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

        // P2b: Number-first fallback for unknown-side pages
        val p2b = matchUnknownByNumber(dataPages, addressPages, unknownPages)
        Log.d(TAG, "P2b (unknown-side number): ${p2b.size} match(es)")
        decisions += p2b

        // P2c: Same-side rescue only when one side is missing.
        // This avoids overriding valid front/back side detections.
        if (dataPages.isEmpty() || addressPages.isEmpty()) {
            val p2c = matchSameSideByNumber(dataPages, addressPages)
            Log.d(TAG, "P2c (same-side number): ${p2c.size} match(es)")
            decisions += p2c
        } else {
            Log.d(TAG, "P2c skipped (both sides still present)")
        }

        // P3: Session proximity
        val p3 = matchBySessionProximity(dataPages, addressPages)
        Log.d(TAG, "P3 (session): ${p3.size} match(es)")
        decisions += p3

        val unresolvedIds = (dataPages + addressPages + unknownPages).map { it.id }
        Log.d(TAG, "Total decisions: ${decisions.size} | unresolved: ${unresolvedIds.size}")

        return Pair(decisions, unresolvedIds)
    }

    /**
     * Number-first fallback:
     * - If unknown-side page has a number matching a known data page, pair as address.
     * - If unknown-side page has a number matching a known address page, pair as data.
     * - If two unknown-side pages share the same passport number, pair by scan order.
     */
    private fun matchUnknownByNumber(
        dataPages: MutableList<PassportDocumentSnapshot>,
        addressPages: MutableList<PassportDocumentSnapshot>,
        unknownPages: MutableList<PassportDocumentSnapshot>
    ): List<PassportPairingDecision> {
        val decisions = mutableListOf<PassportPairingDecision>()

        // Unknown + known(data/address) matches
        val matchedUnknownIdx = mutableSetOf<Int>()
        val matchedDataIdx = mutableSetOf<Int>()
        val matchedAddressIdx = mutableSetOf<Int>()

        for ((ui, unknown) in unknownPages.withIndex()) {
            val num = normalizePassportNumber(unknown.passportNumber) ?: continue

            val dataIdx = dataPages.indexOfFirst {
                normalizePassportNumber(it.passportNumber) == num &&
                        !matchedDataIdx.contains(dataPages.indexOf(it))
            }
            if (dataIdx >= 0) {
                decisions += PassportPairingDecision(
                    dataPageId = dataPages[dataIdx].id,
                    addressPageId = unknown.id,
                    confidence = PairingConfidence.CONFIDENT
                )
                matchedUnknownIdx += ui
                matchedDataIdx += dataIdx
                continue
            }

            val addressIdx = addressPages.indexOfFirst {
                normalizePassportNumber(it.passportNumber) == num &&
                        !matchedAddressIdx.contains(addressPages.indexOf(it))
            }
            if (addressIdx >= 0) {
                decisions += PassportPairingDecision(
                    dataPageId = unknown.id,
                    addressPageId = addressPages[addressIdx].id,
                    confidence = PairingConfidence.CONFIDENT
                )
                matchedUnknownIdx += ui
                matchedAddressIdx += addressIdx
            }
        }

        for (i in matchedAddressIdx.sortedDescending()) addressPages.removeAt(i)
        for (i in matchedDataIdx.sortedDescending()) dataPages.removeAt(i)
        for (i in matchedUnknownIdx.sortedDescending()) unknownPages.removeAt(i)

        // Unknown + unknown exact number pairing
        val byNumber = unknownPages
            .mapNotNull { doc ->
                normalizePassportNumber(doc.passportNumber)?.let { norm -> norm to doc }
            }
            .groupBy({ it.first }, { it.second })

        val consumedIds = mutableSetOf<String>()
        byNumber.forEach { (_, docs) ->
            val ordered = docs.sortedWith(
                compareBy<PassportDocumentSnapshot>({ it.scanSessionId ?: "" }, { it.pageIndex ?: Int.MAX_VALUE })
            )
            var idx = 0
            while (idx + 1 < ordered.size) {
                val first = ordered[idx]
                val second = ordered[idx + 1]
                if (first.id !in consumedIds && second.id !in consumedIds) {
                    decisions += PassportPairingDecision(
                        dataPageId = first.id,
                        addressPageId = second.id,
                        confidence = PairingConfidence.CONFIDENT
                    )
                    consumedIds += first.id
                    consumedIds += second.id
                }
                idx += 2
            }
        }

        if (consumedIds.isNotEmpty()) {
            unknownPages.removeAll { it.id in consumedIds }
        }

        return decisions
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
            val num = normalizePassportNumber(data.passportNumber) ?: continue

            val ai = addressPages.indexOfFirst { addr ->
                normalizePassportNumber(addr.passportNumber) == num &&
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
            val num = normalizePassportNumber(data.passportNumber) ?: continue

            var bestAI   = -1
            var bestDist = Int.MAX_VALUE

            for ((ai, addr) in addressPages.withIndex()) {
                if (matchedAddressIdx.contains(ai)) continue
                val addrNum = normalizePassportNumber(addr.passportNumber) ?: continue
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
                addr.pageIndex != null &&
                kotlin.math.abs(addr.pageIndex - dataIndex) == 1 &&
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

    /**
     * Rescue pairing when both pages were detected as the same side.
     * We still pair by passport number and assign first as data, second as address.
     */
    private fun matchSameSideByNumber(
        dataPages: MutableList<PassportDocumentSnapshot>,
        addressPages: MutableList<PassportDocumentSnapshot>
    ): List<PassportPairingDecision> {
        val decisions = mutableListOf<PassportPairingDecision>()

        fun pairWithin(list: MutableList<PassportDocumentSnapshot>) {
            val grouped = list
                .mapNotNull { snap ->
                    normalizePassportNumber(snap.passportNumber)?.let { norm -> norm to snap }
                }
                .groupBy({ it.first }, { it.second })

            val consumed = mutableSetOf<String>()
            grouped.forEach { (_, docs) ->
                val ordered = docs.sortedWith(
                    compareBy<PassportDocumentSnapshot>({ it.scanSessionId ?: "" }, { it.pageIndex ?: Int.MAX_VALUE })
                )
                var i = 0
                while (i + 1 < ordered.size) {
                    val first = ordered[i]
                    val second = ordered[i + 1]
                    if (first.id !in consumed && second.id !in consumed) {
                        decisions += PassportPairingDecision(
                            dataPageId = first.id,
                            addressPageId = second.id,
                            confidence = PairingConfidence.CONFIDENT
                        )
                        consumed += first.id
                        consumed += second.id
                    }
                    i += 2
                }
            }
            if (consumed.isNotEmpty()) list.removeAll { it.id in consumed }
        }

        pairWithin(dataPages)
        pairWithin(addressPages)
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

    private fun normalizePassportNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.uppercase().replace(nonAlphaNum, "")
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun canonicalPassportSide(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "data", "front", "f", "mrz" -> "data"
            "address", "back", "b", "addr" -> "address"
            else -> null
        }
    }
}
