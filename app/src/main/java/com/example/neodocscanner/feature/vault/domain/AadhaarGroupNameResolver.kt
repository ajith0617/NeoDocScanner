package com.example.neodocscanner.feature.vault.domain

import com.example.neodocscanner.core.domain.model.Document

private val AadhaarTimestampSuffixRegex = Regex("""_[0-9]{8}_[0-9]{4}$""")
private val AadhaarUidRegex = Regex("""\b\d{4}\s?\d{4}\s?\d{4}\b""")
private val AadhaarNoiseSeparatorsRegex = Regex("""[|:_]+""")
private val AadhaarWhitespaceRegex = Regex("""\s+""")
private val AadhaarBlockedTerms = setOf(
    "aadhaar",
    "authority",
    "birth",
    "date",
    "dob",
    "download",
    "electors",
    "enrolment",
    "female",
    "government",
    "helpline",
    "identity",
    "india",
    "issued",
    "male",
    "mobile",
    "name",
    "uidai",
    "unique",
    "vid",
    "www",
    "year"
)

fun buildAadhaarGroupName(front: Document, back: Document? = null): String {
    val docs = listOfNotNull(front, back)
    val ownerToken = docs.asSequence()
        .mapNotNull(::extractAadhaarOwnerToken)
        .firstOrNull()
    val last4 = docs.asSequence()
        .mapNotNull(::extractAadhaarLast4)
        .firstOrNull()
    val fileNameFallback = docs.asSequence()
        .mapNotNull(::fallbackAadhaarFileName)
        .firstOrNull()

    return when {
        ownerToken != null && last4 != null -> "${ownerToken}_Aadhaar$last4"
        ownerToken != null -> "${ownerToken}_Aadhaar"
        fileNameFallback != null -> fileNameFallback
        last4 != null -> "Aadhaar$last4"
        else -> "Aadhaar"
    }
}

private fun extractAadhaarOwnerToken(doc: Document): String? {
    val fieldCandidate = doc.decodedFields.firstOrNull {
        it.label.equals("Name", ignoreCase = true)
    }?.value?.let(::cleanOwnerCandidate)
    if (fieldCandidate != null) return fieldCandidate

    return doc.extractedText
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.flatMap { line ->
            sequenceOf(line) + line.split(",", ";", "/", "\\").asSequence()
        }
        ?.mapNotNull(::cleanOwnerCandidate)
        ?.firstOrNull()
}

private fun cleanOwnerCandidate(raw: String?): String? {
    val candidate = raw?.trim()
        ?.replace(AadhaarNoiseSeparatorsRegex, " ")
        ?.replace(AadhaarWhitespaceRegex, " ")
        ?: return null

    if (candidate.isEmpty()) return null

    val lower = candidate.lowercase()
    if (AadhaarBlockedTerms.any { lower.contains(it) }) return null
    if (candidate.any(Char::isDigit)) return null

    val words = candidate.split(" ")
        .map { token ->
            token.filter { ch -> ch.isLetter() || ch == '\'' || ch == '-' || ch == '.' }
        }
        .filter { it.length >= 2 }
        .take(3)

    if (words.isEmpty()) return null

    return words.joinToString("_") { word ->
        word.lowercase().replaceFirstChar { it.uppercaseChar() }
    }
}

private fun extractAadhaarLast4(doc: Document): String? {
    val fieldDigits = doc.decodedFields.firstOrNull { field ->
        val label = field.label.lowercase()
        label.contains("aadhaar number") || label.contains("aadhaar uid")
    }?.value?.filter(Char::isDigit)

    if (!fieldDigits.isNullOrBlank() && fieldDigits.length >= 4) {
        return fieldDigits.takeLast(4)
    }

    val textDigits = AadhaarUidRegex.find(doc.extractedText.orEmpty())
        ?.value
        ?.filter(Char::isDigit)

    return textDigits?.takeLast(4)
}

private fun fallbackAadhaarFileName(doc: Document): String? {
    val baseName = doc.fileName
        .substringBeforeLast('.')
        .replace(AadhaarTimestampSuffixRegex, "")
        .trim('_', ' ')

    return baseName.takeIf { it.isNotBlank() }
}
