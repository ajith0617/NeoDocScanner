package com.example.neodocscanner.feature.vault.data.service.grouping

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pairs Aadhaar front and back pages captured in the same vault.
 *
 * iOS equivalent: AadhaarGroupingService.swift
 *
 * ── Pairing priority (MUST match iOS exactly) ─────────────────────────────
 * P1  UID hash match       — both sides share the same aadhaarUIDHash → definitive
 * P2  OCR name match       — front has OCR text; exactly ONE unmatched back in session → confident
 * P3  Session proximity    — back.pageIndex == front.pageIndex + 1 → tentative
 * P4  Unresolved           — returned to caller
 *
 * ── Option C guard (same as iOS) ─────────────────────────────────────────
 * When a session contains > 2 unmatched Aadhaar docs (likely multi-customer
 * batch), P2 and P3 are skipped for those sessions to avoid cross-customer
 * false pairings.
 *
 * ── UID conflict check (same as iOS) ─────────────────────────────────────
 * In P2 and P3: if BOTH sides have readable but mismatched UID hashes, they
 * are definitively different cards — do not pair.
 */
@Singleton
class AadhaarGroupingService @Inject constructor() {

    // ── Public types (mirrors iOS AadhaarDocumentSnapshot + GroupingDecision) ─

    data class AadhaarDocumentSnapshot(
        val id: String,
        val aadhaarSide: String?,       // "front" | "back" | null
        val aadhaarUIDHash: String?,
        val extractedText: String?,
        val scanSessionId: String?,
        val pageIndex: Int?,
        val groupId: String?
    )

    enum class GroupingConfidence {
        DEFINITIVE,   // UID hash match
        CONFIDENT,    // OCR name match
        TENTATIVE     // session proximity
    }

    data class GroupingDecision(
        val frontId: String,
        val backId: String,
        val confidence: GroupingConfidence
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Processes [snapshots] and returns (decisions, unresolvedIds).
     * iOS equivalent: AadhaarGroupingService.process(snapshots:)
     */
    fun process(
        snapshots: List<AadhaarDocumentSnapshot>
    ): Pair<List<GroupingDecision>, List<String>> {

        val aadhaar = snapshots.filter {
            (it.aadhaarSide == "front" || it.aadhaarSide == "back") && it.groupId == null
        }

        val fronts = aadhaar.filter { it.aadhaarSide == "front" }.toMutableList()
        val backs  = aadhaar.filter { it.aadhaarSide == "back"  }.toMutableList()

        Log.d(TAG, "process() | eligible: ${aadhaar.size} | fronts: ${fronts.size} | backs: ${backs.size}")

        val decisions = mutableListOf<GroupingDecision>()

        // P1: UID hash match
        val p1 = matchByUIDHash(fronts, backs)
        Log.d(TAG, "P1 (UID hash): ${p1.size} match(es) | remaining fronts: ${fronts.size} backs: ${backs.size}")
        decisions += p1

        // Option C guard
        val multiCustomerSessions = multiCustomerSessionIds(fronts, backs)
        if (multiCustomerSessions.isNotEmpty()) {
            Log.d(TAG, "Option C guard | sessions with >2 unmatched docs (P2+P3 skipped): $multiCustomerSessions")
        }

        // P2: OCR name match
        val p2 = matchByOCRName(fronts, backs, multiCustomerSessions)
        Log.d(TAG, "P2 (OCR name): ${p2.size} match(es)")
        decisions += p2

        // P3: Session proximity
        val p3 = matchBySessionProximity(fronts, backs, multiCustomerSessions)
        Log.d(TAG, "P3 (session prox): ${p3.size} match(es)")
        decisions += p3

        val unresolvedIds = (fronts + backs).map { it.id }
        Log.d(TAG, "Total decisions: ${decisions.size} | unresolved: ${unresolvedIds.size}")

        return Pair(decisions, unresolvedIds)
    }

    // ── P1: UID hash match ────────────────────────────────────────────────────

    private fun matchByUIDHash(
        fronts: MutableList<AadhaarDocumentSnapshot>,
        backs:  MutableList<AadhaarDocumentSnapshot>
    ): List<GroupingDecision> {
        val decisions          = mutableListOf<GroupingDecision>()
        val matchedFrontIdx    = mutableListOf<Int>()
        val matchedBackIdx     = mutableListOf<Int>()

        for ((fi, front) in fronts.withIndex()) {
            val hash = front.aadhaarUIDHash?.takeIf { it.isNotEmpty() } ?: continue

            val bi = backs.indexOfFirst { back ->
                back.aadhaarUIDHash == hash &&
                !matchedBackIdx.contains(backs.indexOf(back))
            }
            if (bi >= 0) {
                Log.d(TAG, "P1 ✅ matched front ${front.id.take(8)} ↔ back ${backs[bi].id.take(8)}")
                decisions += GroupingDecision(front.id, backs[bi].id, GroupingConfidence.DEFINITIVE)
                matchedFrontIdx += fi
                matchedBackIdx  += bi
            }
        }

        for (i in matchedBackIdx.sortedDescending())  backs.removeAt(i)
        for (i in matchedFrontIdx.sortedDescending()) fronts.removeAt(i)
        return decisions
    }

    // ── P2: OCR name match ────────────────────────────────────────────────────

    /**
     * Pairs a front (which has OCR text) with a back in the same session,
     * only when exactly ONE unmatched back exists in that session.
     * iOS: matchByOCRName(fronts:backs:skipSessions:)
     */
    private fun matchByOCRName(
        fronts: MutableList<AadhaarDocumentSnapshot>,
        backs:  MutableList<AadhaarDocumentSnapshot>,
        skipSessions: Set<String>
    ): List<GroupingDecision> {
        val decisions       = mutableListOf<GroupingDecision>()
        val matchedFrontIdx = mutableListOf<Int>()
        val matchedBackIdx  = mutableListOf<Int>()

        for ((fi, front) in fronts.withIndex()) {
            val sessionId = front.scanSessionId ?: continue
            val text      = front.extractedText?.trim() ?: continue
            if (text.isEmpty()) continue
            if (skipSessions.contains(sessionId)) continue

            val sessionBacks = backs.withIndex().filter {
                it.value.scanSessionId == sessionId &&
                !matchedBackIdx.contains(it.index)
            }
            if (sessionBacks.size != 1) continue

            val (bi, candidateBack) = sessionBacks.first()

            // UID conflict check
            val frontHash = front.aadhaarUIDHash?.takeIf { it.isNotEmpty() }
            val backHash  = candidateBack.aadhaarUIDHash?.takeIf { it.isNotEmpty() }
            if (frontHash != null && backHash != null && frontHash != backHash) {
                Log.d(TAG, "P2 skip: UID hashes present but mismatched — different cards")
                continue
            }

            Log.d(TAG, "P2 ✅ matched front ${front.id.take(8)} ↔ back ${candidateBack.id.take(8)}")
            decisions += GroupingDecision(front.id, candidateBack.id, GroupingConfidence.CONFIDENT)
            matchedFrontIdx += fi
            matchedBackIdx  += bi
        }

        for (i in matchedBackIdx.sortedDescending())  backs.removeAt(i)
        for (i in matchedFrontIdx.sortedDescending()) fronts.removeAt(i)
        return decisions
    }

    // ── P3: Session proximity ─────────────────────────────────────────────────

    /**
     * Pairs fronts and backs that are consecutive pages (pageIndex N and N+1)
     * within the same scanSessionId.
     * iOS: matchBySessionProximity(fronts:backs:skipSessions:)
     */
    private fun matchBySessionProximity(
        fronts: MutableList<AadhaarDocumentSnapshot>,
        backs:  MutableList<AadhaarDocumentSnapshot>,
        skipSessions: Set<String>
    ): List<GroupingDecision> {
        val decisions       = mutableListOf<GroupingDecision>()
        val matchedFrontIdx = mutableListOf<Int>()
        val matchedBackIdx  = mutableListOf<Int>()

        for ((fi, front) in fronts.withIndex()) {
            val sessionId  = front.scanSessionId ?: continue
            val frontIndex = front.pageIndex     ?: continue
            if (skipSessions.contains(sessionId)) continue

            val bi = backs.indexOfFirst { back ->
                back.scanSessionId == sessionId &&
                back.pageIndex == frontIndex + 1 &&
                !matchedBackIdx.contains(backs.indexOf(back))
            }
            if (bi < 0) continue

            val candidateBack = backs[bi]

            // UID conflict check
            val frontHash = front.aadhaarUIDHash?.takeIf { it.isNotEmpty() }
            val backHash  = candidateBack.aadhaarUIDHash?.takeIf { it.isNotEmpty() }
            if (frontHash != null && backHash != null && frontHash != backHash) {
                Log.d(TAG, "P3 skip: UID hashes present but mismatched — different cards")
                continue
            }

            Log.d(TAG, "P3 ✅ matched front ${front.id.take(8)} ↔ back ${candidateBack.id.take(8)}")
            decisions += GroupingDecision(front.id, candidateBack.id, GroupingConfidence.TENTATIVE)
            matchedFrontIdx += fi
            matchedBackIdx  += bi
        }

        for (i in matchedBackIdx.sortedDescending())  backs.removeAt(i)
        for (i in matchedFrontIdx.sortedDescending()) fronts.removeAt(i)
        return decisions
    }

    // ── Option C helper ───────────────────────────────────────────────────────

    /**
     * Returns session IDs that have > 2 unmatched Aadhaar documents —
     * likely multi-customer batches. P2 and P3 are skipped for these.
     * iOS: multiCustomerSessionIDs(fronts:backs:)
     */
    private fun multiCustomerSessionIds(
        fronts: List<AadhaarDocumentSnapshot>,
        backs:  List<AadhaarDocumentSnapshot>
    ): Set<String> {
        val counts = mutableMapOf<String, Int>()
        for (doc in fronts + backs) {
            val sid = doc.scanSessionId ?: continue
            counts[sid] = (counts[sid] ?: 0) + 1
        }
        return counts.filter { it.value > 2 }.keys.toSet()
    }

    companion object { private const val TAG = "AadhaarGroupingService" }
}
