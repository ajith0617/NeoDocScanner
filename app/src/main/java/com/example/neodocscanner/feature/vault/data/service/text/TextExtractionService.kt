package com.example.neodocscanner.feature.vault.data.service.text

import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentField
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts structured fields from raw OCR text using regex patterns.
 *
 * iOS equivalent: TextExtractionService.swift which uses NSRegularExpression
 * to extract Aadhaar UID, PAN, passport number, voter ID, DL number, dates,
 * and names from OCR'd text.
 *
 * All regex patterns are transcribed 1:1 from the iOS implementation —
 * only the API differs (Kotlin Regex vs NSRegularExpression).
 */
@Singleton
class TextExtractionService @Inject constructor() {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Extracts document fields from [ocrText] given the predicted [documentClass].
     * Returns a list of [DocumentField] to be serialised as JSON and stored in Room.
     */
    fun extract(ocrText: String, documentClass: DocumentClass): List<DocumentField> {
        val fields = mutableListOf<DocumentField>()
        val text = ocrText.uppercase()

        when (documentClass) {
            DocumentClass.AADHAAR         -> extractAadhaar(text, fields)
            DocumentClass.PAN             -> extractPan(text, fields)
            DocumentClass.VOTER_ID        -> extractVoterId(text, fields)
            DocumentClass.DRIVING_LICENCE -> extractDrivingLicence(text, fields)
            DocumentClass.PASSPORT        -> extractPassport(text, fields)
            DocumentClass.OTHER           -> extractGeneric(text, fields)
        }

        // Dates are extracted for all document types
        extractDates(ocrText, fields)

        // Name extraction (heuristic NER — replaces iOS NLTagger)
        extractName(ocrText, fields, documentClass)

        return fields
    }

    // ── Aadhaar ───────────────────────────────────────────────────────────────

    private fun extractAadhaar(text: String, out: MutableList<DocumentField>) {
        REGEX_AADHAAR_UID.find(text)?.let { m ->
            out += DocumentField(label = "Aadhaar UID", value = m.value.replace(" ", ""))
        }
        REGEX_AADHAAR_VID.find(text)?.let { m ->
            out += DocumentField(label = "Virtual ID", value = m.value.replace(" ", ""))
        }
        when {
            "MALE"   in text -> out += DocumentField(label = "Gender", value = "Male")
            "FEMALE" in text -> out += DocumentField(label = "Gender", value = "Female")
        }
    }

    private fun extractPan(text: String, out: MutableList<DocumentField>) {
        REGEX_PAN.find(text)?.let { m ->
            out += DocumentField(label = "PAN Number", value = m.value)
        }
        REGEX_RELATIVE_NAME.find(text)?.groups?.get(1)?.value?.let { name ->
            out += DocumentField(label = "Father's Name", value = name.trim().titleCase())
        }
        REGEX_DATE_DDMMYYYY_SLASH.find(text)?.let { m ->
            out += DocumentField(label = "Date of Birth", value = m.value)
        }
    }

    private fun extractVoterId(text: String, out: MutableList<DocumentField>) {
        REGEX_VOTER_ID.find(text)?.let { m ->
            out += DocumentField(label = "EPIC Number", value = m.value)
        }
        when {
            "MALE"   in text -> out += DocumentField(label = "Gender", value = "Male")
            "FEMALE" in text -> out += DocumentField(label = "Gender", value = "Female")
        }
        REGEX_DATE_DDMMYYYY_SLASH.find(text)?.let { m ->
            out += DocumentField(label = "Date of Birth", value = m.value)
        }
    }

    private fun extractDrivingLicence(text: String, out: MutableList<DocumentField>) {
        REGEX_DL.find(text)?.let { m ->
            out += DocumentField(label = "DL Number", value = m.value)
        }
        REGEX_DATE_DDMMYYYY_SLASH.find(text)?.let { m ->
            out += DocumentField(label = "Date of Birth", value = m.value)
        }
        val allDates = REGEX_DATE_DDMMYYYY_SLASH.findAll(text).map { it.value }.toList()
        if (allDates.size >= 2) {
            out += DocumentField(label = "Valid Till", value = allDates[1])
        }
    }

    private fun extractPassport(text: String, out: MutableList<DocumentField>) {
        REGEX_PASSPORT_NUMBER.find(text)?.let { m ->
            out += DocumentField(label = "Passport Number", value = m.value)
        }
        val mrzLines = REGEX_MRZ_LINE.findAll(text).map { it.value }.toList()
        if (mrzLines.isNotEmpty()) {
            out += DocumentField(label = "MRZ", value = mrzLines.joinToString("\n"))
        }
        REGEX_DATE_DDMMYYYY_SLASH.find(text)?.let { m ->
            out += DocumentField(label = "Date of Birth", value = m.value)
        }
        REGEX_EXPIRY.find(text)?.groups?.get(1)?.value?.let { d ->
            out += DocumentField(label = "Expiry Date", value = d)
        }
    }

    private fun extractGeneric(text: String, out: MutableList<DocumentField>) {
        listOf(
            REGEX_PAN              to "PAN Number",
            REGEX_VOTER_ID         to "Voter ID",
            REGEX_PASSPORT_NUMBER  to "Passport Number"
        ).forEach { (regex, label) ->
            regex.find(text)?.let { m ->
                out += DocumentField(label = label, value = m.value)
            }
        }
    }

    private fun extractDates(text: String, out: MutableList<DocumentField>) {
        if (out.none { it.label == "Date of Birth" }) {
            REGEX_DATE_DDMMYYYY_SLASH.find(text)?.let { m ->
                out += DocumentField(label = "Date of Birth", value = m.value)
            }
        }
    }

    /**
     * Heuristic name extraction — replaces iOS NLTagger .personalName.
     */
    private fun extractName(text: String, out: MutableList<DocumentField>, docClass: DocumentClass) {
        if (out.any { it.label == "Name" }) return

        val upperText = text.uppercase()

        REGEX_NAME_LABEL.find(upperText)?.groups?.get(1)?.value?.trim()
            ?.takeIf { it.length >= 3 }
            ?.let {
                out += DocumentField(label = "Name", value = it.titleCase())
                return
            }

        text.split("\n").forEach { line ->
            val trimmed = line.trim()
            if (isLikelyName(trimmed, docClass)) {
                out += DocumentField(label = "Name", value = trimmed.titleCase())
                return
            }
        }
    }

    private fun isLikelyName(line: String, docClass: DocumentClass): Boolean {
        val upper = line.uppercase()
        if (upper.length < 3 || upper.length > 60) return false
        // Must be only letters and spaces
        if (!upper.all { it.isLetter() || it == ' ' }) return false
        val words = upper.trim().split("\\s+".toRegex())
        if (words.size < 2 || words.size > 5) return false
        // Exclude known header/footer tokens
        val excluded = setOf(
            "GOVERNMENT", "INDIA", "REPUBLIC", "AUTHORITY", "UNIQUE",
            "IDENTIFICATION", "UIDAI", "ELECTION", "COMMISSION",
            "INCOME TAX", "DEPARTMENT", "MINISTRY", "TRANSPORT",
            "DRIVING", "LICENCE", "PASSPORT", "PERMANENT", "ACCOUNT"
        )
        if (words.any { it in excluded }) return false
        return true
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun String.titleCase(): String =
        split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercaseChar() }
        }

    // ── Regex patterns ─────────────────────────────────────────────────────────

    companion object {
        // Aadhaar: 4-4-4 or 12 continuous digits
        private val REGEX_AADHAAR_UID = Regex("""\b(\d{4}\s\d{4}\s\d{4}|\d{12})\b""")
        // Virtual ID: 16 digits
        private val REGEX_AADHAAR_VID = Regex("""\b\d{4}\s\d{4}\s\d{4}\s\d{4}\b""")
        // PAN: AAAAA9999A format
        private val REGEX_PAN = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")
        // Voter EPIC number: 3 uppercase letters + 7 digits
        private val REGEX_VOTER_ID = Regex("""\b[A-Z]{3}[0-9]{7}\b""")
        // DL: state code (2 alpha) + year (2 digits) + 7-11 digit serial
        private val REGEX_DL = Regex("""\b[A-Z]{2}[-\s]?\d{2}[-\s]?\d{4}[-\s]?\d{7}\b""")
        // Passport: J1234567 format (letter + 7 digits)
        private val REGEX_PASSPORT_NUMBER = Regex("""\b[A-PR-WYa-pr-wy][1-9]\d\s?\d{4}[1-9]\b""")
        // MRZ line: 44 characters (digits + uppercase + <)
        private val REGEX_MRZ_LINE = Regex("""[A-Z0-9<]{44}""")
        // Date: DD/MM/YYYY or DD-MM-YYYY
        private val REGEX_DATE_DDMMYYYY_SLASH = Regex("""\b\d{2}[/-]\d{2}[/-]\d{4}\b""")
        // Relative name after S/O, D/O, C/O
        private val REGEX_RELATIVE_NAME = Regex("""[SDC]/O\s+([A-Z\s]{3,40})""")
        // Expiry date labelled
        private val REGEX_EXPIRY =
            Regex("""(?:EXPIRY|VALID\s+UNTIL|VALID\s+UPTO)\s+(\d{2}[/-]\d{2}[/-]\d{4})""")
        // Name label
        private val REGEX_NAME_LABEL =
            Regex("""(?:NAME|नाम)\s*[:\-]?\s*([A-Z\s]{3,50})""")
    }
}
