package com.example.neodocscanner.navigation

/**
 * Typed navigation destinations for the entire app.
 *
 * Structured as sealed classes so new routes are added in one place and
 * the compiler catches missing `when` branches.
 *
 * iOS equivalent: SwiftUI NavigationStack / TabView destinations. Each
 * `object` maps to a view struct in iOS.
 */
sealed class Screen(val route: String) {

    // ── Auth graph ────────────────────────────────────────────────────────────

    /** Branded launch screen shown for ~1.5 s before deciding where to go. */
    data object Splash : Screen("splash")

    /** Login screen — shown when the user is not authenticated. */
    data object Login  : Screen("login")

    // ── Hub graph ─────────────────────────────────────────────────────────────

    /**
     * Application Hub — the list of vault instances.
     * iOS equivalent: ApplicationHubView.swift
     */
    data object Hub : Screen("hub")

    /**
     * Document Vault — the per-vault view with checklist + review tabs.
     * Accepts the vault ID as a path argument.
     * iOS equivalent: DocuVaultView.swift
     */
    data object Vault : Screen("vault/{instanceId}") {
        fun createRoute(instanceId: String) = "vault/$instanceId"
        const val ARG_INSTANCE_ID = "instanceId"
    }

    /**
     * Full-screen document viewer — pinch-to-zoom image with OCR overlay.
     * iOS equivalent: DocumentFullscreenView.swift (.fullScreenCover presentation).
     */
    data object DocumentViewer : Screen("document_viewer/{documentId}") {
        fun createRoute(documentId: String) = "document_viewer/$documentId"
        const val ARG_DOCUMENT_ID = "documentId"
    }

    /**
     * In-app PDF viewer for merged group PDFs.
     * iOS equivalent: PDFDocumentViewer.swift (sheet presentation).
     */
    data object PdfViewer : Screen("pdf_viewer/{documentId}") {
        fun createRoute(documentId: String) = "pdf_viewer/$documentId"
        const val ARG_DOCUMENT_ID = "documentId"
    }

    // ── Profile / Settings ────────────────────────────────────────────────────

    /**
     * User profile editor (name, designation, organisation, email).
     * iOS equivalent: ProfileView.swift (sheet from hub toolbar).
     */
    data object Profile : Screen("profile")

    /**
     * App-wide feature settings (auto-rename, Aadhaar masking, about).
     * iOS equivalent: SettingsView.swift (sheet from hub toolbar).
     */
    data object Settings : Screen("settings")
}
