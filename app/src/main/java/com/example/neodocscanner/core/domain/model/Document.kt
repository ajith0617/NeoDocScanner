package com.example.neodocscanner.core.domain.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Domain model for a stored document.
 *
 * iOS equivalent: Document.swift (@Model class).
 *
 * This is a pure Kotlin data class — no Room or Android imports.
 * The Room entity (DocumentEntity) is a separate class that maps to/from this.
 */
data class Document(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String = "",
    val relativePath: String = "",
    val fileSize: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val categoryName: String = "All Documents",
    val typeRawValue: String = DocumentType.IMAGE.rawValue,

    // OCR / text extraction
    val extractedText: String? = null,
    val isOcrProcessed: Boolean = false,
    val extractedRegions: String? = null,    // JSON: [TextRegion]

    // ML classification
    val documentClassRaw: String? = null,
    val extractedFields: String? = null,     // JSON: [DocumentField]
    val isManuallyClassified: Boolean = false,

    // Smart grouping
    val groupId: String? = null,
    val pageIndex: Int? = null,
    val groupName: String? = null,
    val groupPageIndex: Int? = null,

    // Aadhaar front/back pairing
    val aadhaarSide: String? = null,         // "front" | "back" | null
    val aadhaarUidHash: String? = null,      // SHA-256 of raw UID — never displayed
    val scanSessionId: String? = null,

    // Passport data/address pairing
    val passportSide: String? = null,        // "data" | "address" | null

    // Section routing
    val sectionId: String? = null,           // null → Review inbox

    // Aadhaar masking
    val maskedRelativePath: String? = null,

    // PDF export
    val isArchivedByExport: Boolean = false,
    val exportedFromGroupId: String? = null,

    // Processing pipeline
    val processingStatusRaw: String = ProcessingStatus.COMPLETE.rawValue,
    val thumbnailRelativePath: String? = null,

    // Application vault scoping
    val applicationInstanceId: String? = null
) {
    // ── Computed properties (not persisted) ──────────────────────────────────

    val documentType: DocumentType
        get() = DocumentType.fromRaw(typeRawValue)

    val documentClass: DocumentClass
        get() = DocumentClass.fromRaw(documentClassRaw)

    val processingStatus: ProcessingStatus
        get() = ProcessingStatus.fromRaw(processingStatusRaw)

    val isIntelligenceProcessed: Boolean
        get() = documentClassRaw != null

    /** Human-readable display name — strips extension and underscores from filename. */
    val displayName: String
        get() = if (isManuallyClassified) {
            documentClass.displayName
        } else {
            fileName.substringBeforeLast('.').replace("_", " ")
        }

    /** UI-facing title — prefer the group label when this document belongs to a named pair/group. */
    val displayTitle: String
        get() = groupName?.takeIf { it.isNotBlank() } ?: displayName

    /** Relative date string. */
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            return sdf.format(Date(dateAdded))
        }

    /** Human-readable file size. */
    val formattedSize: String
        get() {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0  -> String.format(Locale.getDefault(), "%.1f MB", mb)
                kb >= 1.0  -> String.format(Locale.getDefault(), "%.0f KB", kb)
                else       -> "$fileSize B"
            }
        }

    // ── JSON helpers (mirrors iOS decodedFields / decodedRegions) ────────────

    private val gson = Gson()

    /** Decodes extractedFields JSON to a typed list. Returns empty list on parse failure. */
    val decodedFields: List<DocumentField>
        get() {
            if (extractedFields.isNullOrBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<DocumentField>>() {}.type
                gson.fromJson(extractedFields, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** Decodes extractedRegions JSON to a typed list. Returns empty list on parse failure. */
    val decodedRegions: List<TextRegion>
        get() {
            if (extractedRegions.isNullOrBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<TextRegion>>() {}.type
                gson.fromJson(extractedRegions, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
}
