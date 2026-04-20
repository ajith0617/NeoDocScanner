package com.example.neodocscanner.core.domain.model

import java.util.UUID

/**
 * A single key-value field extracted from an analysed document.
 * Stored as a JSON array in Document.extractedFields.
 *
 * iOS equivalent: DocumentField struct in DocumentClass.swift.
 * JSON format: [{"id":"<uuid>","label":"...","value":"..."}]
 */
data class DocumentField(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val value: String
)
