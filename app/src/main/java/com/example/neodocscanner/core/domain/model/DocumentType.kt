package com.example.neodocscanner.core.domain.model

/**
 * File format of a stored document — mirrors iOS DocumentType enum.
 *
 * [rawValue] matches iOS rawValue so JSON payloads / exports are cross-platform compatible.
 */
enum class DocumentType(val rawValue: String) {
    PDF("PDF"),
    IMAGE("IMG"),
    DOCX("DOCX");

    companion object {
        fun fromRaw(raw: String?): DocumentType =
            entries.find { it.rawValue == raw } ?: DOCX

        /** Infers DocumentType from a file path extension. */
        fun fromPath(path: String): DocumentType {
            return when (path.substringAfterLast('.', "").lowercase()) {
                "pdf"                          -> PDF
                "jpg", "jpeg", "png",
                "heic", "gif", "webp", "bmp"   -> IMAGE
                "doc", "docx"                  -> DOCX
                else                           -> DOCX
            }
        }
    }
}
