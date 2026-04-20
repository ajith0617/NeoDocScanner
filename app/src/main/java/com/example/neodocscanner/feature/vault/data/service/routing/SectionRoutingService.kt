package com.example.neodocscanner.feature.vault.data.service.routing

import com.example.neodocscanner.core.domain.model.ApplicationSection
import com.example.neodocscanner.core.domain.model.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatically assigns documents to the most appropriate checklist section.
 *
 * iOS equivalent: SectionRoutingService.swift
 *
 * Routing priorities (MUST match iOS exactly):
 *   1. Required section that accepts class AND is not full
 *   2. Any section that accepts class AND is not full
 *   3. Any section that accepts class (even if full)  ← iOS Priority 3
 *   4. nil → Review Inbox (sectionId = null)
 *
 * Note: iOS Priority 3 ensures a document is ALWAYS routed to a matching
 * section even when that section is at capacity. This is intentional — the
 * user can then review the overflow manually.
 */
@Singleton
class SectionRoutingService @Inject constructor() {

    data class RoutingResult(
        val documentId: String,
        val sectionId: String?           // null = Review Inbox (no matching section)
    )

    /**
     * Routes [documents] into sections given the current [sections] state.
     *
     * @param documents              Documents to route (all should have a classification).
     * @param sections               Available sections with accepted class info.
     * @param existingCountBySection Current document count per section (from Room).
     */
    fun route(
        documents: List<Document>,
        sections: List<ApplicationSection>,
        existingCountBySection: Map<String, Int>
    ): List<RoutingResult> {
        val provisionalCount = existingCountBySection.toMutableMap()

        return documents.map { doc ->
            val result = findBestSection(doc, sections, provisionalCount)
            if (result.sectionId != null) {
                provisionalCount[result.sectionId] =
                    (provisionalCount[result.sectionId] ?: 0) + 1
            }
            result
        }
    }

    // ── iOS SectionRoutingService.route(documentClassRaw:into:) ──────────────

    private fun findBestSection(
        document: Document,
        sections: List<ApplicationSection>,
        countMap: MutableMap<String, Int>
    ): RoutingResult {
        val docClassDisplay = document.documentClass.displayName

        // All sections that accept this document class
        val accepting = sections.filter { section ->
            section.acceptedClasses.contains(docClassDisplay)
        }
        if (accepting.isEmpty()) {
            return RoutingResult(documentId = document.id, sectionId = null)
        }

        fun isNotFull(s: ApplicationSection): Boolean {
            val current = countMap[s.id] ?: 0
            return s.maxDocuments == 0 || current < s.maxDocuments
        }

        // Priority 1: required section that accepts class AND is not full
        accepting.firstOrNull { it.isRequired && isNotFull(it) }?.let {
            return RoutingResult(document.id, it.id)
        }

        // Priority 2: any section that accepts class AND is not full
        accepting.firstOrNull { isNotFull(it) }?.let {
            return RoutingResult(document.id, it.id)
        }

        // Priority 3: any section that accepts class (even if full) — iOS behaviour
        accepting.firstOrNull()?.let {
            return RoutingResult(document.id, it.id)
        }

        // Priority 4: nil → Review Inbox
        return RoutingResult(documentId = document.id, sectionId = null)
    }
}
