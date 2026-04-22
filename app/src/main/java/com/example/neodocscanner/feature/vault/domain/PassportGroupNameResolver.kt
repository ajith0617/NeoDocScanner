package com.example.neodocscanner.feature.vault.domain

import com.example.neodocscanner.core.domain.model.Document

fun buildPassportGroupName(dataPage: Document?, addressPage: Document? = null): String {
    val owner = extractPassportOwnerName(dataPage) ?: extractPassportOwnerName(addressPage)
    return owner ?: "Passport"
}

private fun extractPassportOwnerName(doc: Document?): String? {
    if (doc == null) return null
    val fields = doc.decodedFields
    val fullName = fields.firstOrNull { it.label.equals("Name", ignoreCase = true) }?.value
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("passport", ignoreCase = true) }
    if (fullName != null) return fullName

    val given = fields.firstOrNull { it.label.equals("Given Name", ignoreCase = true) }?.value?.trim()
    val surname = fields.firstOrNull { it.label.equals("Surname", ignoreCase = true) }?.value?.trim()
    val combined = listOfNotNull(given, surname)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
    if (combined.isNotBlank()) return combined

    val fromFile = doc.fileName
        .substringBeforeLast('.')
        .replace('_', ' ')
        .trim()
    return fromFile.takeIf {
        it.isNotBlank() &&
            !it.equals("passport", ignoreCase = true) &&
            !it.equals("data", ignoreCase = true) &&
            !it.equals("address", ignoreCase = true)
    }
}
