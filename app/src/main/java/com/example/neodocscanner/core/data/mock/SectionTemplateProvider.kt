package com.example.neodocscanner.core.data.mock

import com.example.neodocscanner.core.domain.model.ApplicationSection
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.google.gson.Gson
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the pre-defined checklist section templates seeded into every
 * new ApplicationInstance vault.
 *
 * iOS equivalent: MockCategoriesProvider.swift
 *
 * ── Key behaviour (MUST match iOS exactly) ──────────────────────────────────
 *
 * iOS seeds the SAME 10 categories for ALL application templates
 * (Bank Account Opening, Home Loan, Passport Application, etc.).
 * There is NO per-template category customisation in the iOS codebase.
 * [MockCategoriesProvider.seed(for:in:)] is called once per instance and
 * always inserts the same 10 CategoryTemplate entries regardless of the
 * template type.
 *
 * The 10 categories and their ML routing rules:
 *   1. Aadhaar          – accepts ["Aadhaar"],     required, unlimited
 *   2. PAN Card         – accepts ["PAN Card"],     required, max 1
 *   3. Photograph       – accepts [],               required, max 2   (no ML routing)
 *   4. Voter ID         – accepts ["Voter ID"],     optional, unlimited
 *   5. Passport         – accepts ["Passport"],     optional, unlimited
 *   6. Income Documents – accepts [],               optional, unlimited (no ML class yet)
 *   7. Property Documents – accepts [],             optional, unlimited
 *   8. Identity Proof   – accepts [],               optional, unlimited (server-side mapping)
 *   9. Address Proof    – accepts [],               optional, unlimited (server-side mapping)
 *  10. Others           – accepts [],               optional, unlimited (manual catch-all)
 *
 * ML routing outcome:
 *   • Aadhaar  → "Aadhaar" section
 *   • PAN Card → "PAN Card" section
 *   • Voter ID → "Voter ID" section
 *   • Passport → "Passport" section
 *   • Other / Driving Licence / unclassified → sectionId = null → "Uncategorised" tab
 *
 * "Others" (named section) ≠ "Uncategorised" (tab):
 *   – "Others" is a section the USER deliberately moves documents into.
 *   – "Uncategorised" (Uncategorised tab) holds documents with sectionId == null
 *     i.e. documents the ML could not auto-route to any named section.
 *
 * Future: Replace with an API-driven configuration endpoint without changing
 * the ViewModel or Repository contracts.
 */
@Singleton
class SectionTemplateProvider @Inject constructor() {

    private val gson = Gson()

    /**
     * Returns an ordered list of [ApplicationSection] seeds for [instanceId].
     *
     * All templates receive the same 10 sections — matching iOS behaviour.
     * [templateId] is accepted as a parameter to remain future-compatible with
     * server-driven per-template customisation.
     */
    fun sectionsFor(
        templateId: String,
        instanceId: String
    ): List<ApplicationSection> {
        return defaultSections().mapIndexed { index, raw ->
            ApplicationSection(
                id                    = "${instanceId}_${raw.templateId}",
                applicationInstanceId = instanceId,
                title                 = raw.title,
                iconName              = raw.iconName,
                acceptedClassesJson   = gson.toJson(raw.acceptedClasses),
                isRequired            = raw.isRequired,
                maxDocuments          = raw.maxDocuments,
                displayOrder          = index,
                isCollapsed           = false
            )
        }
    }

    // ── The 10 iOS categories (identical for every application template) ───────

    /**
     * Mirrors [MockCategoriesProvider.categories] in iOS exactly.
     *
     * Special entries (same comments as iOS source):
     *   • Photograph      — no ML routing (acceptedClasses empty); user scans passport-size photo
     *   • Identity Proof / Address Proof — presentation-only group names; no ML routing;
     *     mapping is decided server-side in future.
     *   • Income Documents / Property Documents — no ML class yet; no ML routing.
     *   • Others — permanent catch-all for documents the user deliberately places here
     *     because the relevant category is missing or not yet added server-side.
     *     Different from "Uncategorised" (which holds unclassified/unidentified scans).
     */
    private fun defaultSections() = listOf(
        RawSection(
            templateId      = "cat_aadhaar",
            title           = "Aadhaar",
            iconName        = "person_text_rectangle",
            acceptedClasses = listOf(DocumentClass.AADHAAR.displayName),
            isRequired      = true,
            maxDocuments    = 0     // unlimited — front + back both go here
        ),
        RawSection(
            templateId      = "cat_pan",
            title           = "PAN Card",
            iconName        = "credit_card",
            acceptedClasses = listOf(DocumentClass.PAN.displayName),
            isRequired      = true,
            maxDocuments    = 1
        ),
        RawSection(
            templateId      = "cat_photograph",
            title           = "Photograph",
            iconName        = "camera_viewfinder",
            acceptedClasses = emptyList(),  // no ML routing — user manually places photo here
            isRequired      = true,
            maxDocuments    = 2
        ),
        RawSection(
            templateId      = "cat_voter_id",
            title           = "Voter ID",
            iconName        = "checkmark_seal",
            acceptedClasses = listOf(DocumentClass.VOTER_ID.displayName),
            isRequired      = false,
            maxDocuments    = 0
        ),
        RawSection(
            templateId      = "cat_passport",
            title           = "Passport",
            iconName        = "globe",
            acceptedClasses = listOf(DocumentClass.PASSPORT.displayName),
            isRequired      = false,
            maxDocuments    = 0
        ),
        RawSection(
            templateId      = "cat_income",
            title           = "Income Documents",
            iconName        = "currency_rupee",
            acceptedClasses = emptyList(),  // salary slips, ITR etc. — no ML class yet
            isRequired      = false,
            maxDocuments    = 0
        ),
        RawSection(
            templateId      = "cat_property",
            title           = "Property Documents",
            iconName        = "apartment",
            acceptedClasses = emptyList(),  // no ML class yet
            isRequired      = false,
            maxDocuments    = 0
        ),
        RawSection(
            templateId      = "cat_identity_proof",
            title           = "Identity Proof",
            iconName        = "person_badge_shield_checkmark",
            acceptedClasses = emptyList(),  // no ML routing — server-side mapping
            isRequired      = false,
            maxDocuments    = 0
        ),
        RawSection(
            templateId      = "cat_address_proof",
            title           = "Address Proof",
            iconName        = "home",
            acceptedClasses = emptyList(),  // no ML routing — server-side mapping
            isRequired      = false,
            maxDocuments    = 0
        ),
        RawSection(
            templateId      = "cat_others",
            title           = "Others",
            iconName        = "tray",
            acceptedClasses = emptyList(),  // manual catch-all — never ML-routed
            isRequired      = false,
            maxDocuments    = 0
        )
    )

    // ── Internal model ────────────────────────────────────────────────────────

    private data class RawSection(
        val templateId: String,
        val title: String,
        val iconName: String,
        val acceptedClasses: List<String>,
        val isRequired: Boolean,
        val maxDocuments: Int
    )
}
