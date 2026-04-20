package com.example.neodocscanner.feature.vault.data.service.text

import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.model.DocumentField
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Converts sanitised OCR text into structured [DocumentField]s.
 *
 * iOS equivalent: DocumentFieldExtractorService.swift +
 *   DocumentFieldExtractorService+Aadhaar.swift
 *   DocumentFieldExtractorService+PAN.swift
 *   DocumentFieldExtractorService+VoterID.swift
 *   DocumentFieldExtractorService+Passport.swift
 *
 * ── Design ────────────────────────────────────────────────────────────────
 * • One public entry: extract(for:from:backText:)
 * • Document type already known from TFLite classifier — no re-detection
 * • All regex patterns translated 1:1 from iOS NSRegularExpression patterns
 * • Digit normaliser: O/o→0, I/l/i→1, B→8, G→6, S→5, Z→2 (same as iOS)
 * • indianNameScore and fusedName NER logic (same as iOS)
 * • MRZ ICAO TD3 parser for Passport (same check-digit logic as iOS)
 * • extractAllCapsNames (same criteria as iOS)
 */
@Singleton
class DocumentFieldExtractorService @Inject constructor() {

    // ── Public API ────────────────────────────────────────────────────────────

    fun extract(
        documentClass: DocumentClass,
        text: String,
        backText: String = ""
    ): List<DocumentField> = when (documentClass) {
        DocumentClass.AADHAAR         -> extractAadhaar(text, backText)
        DocumentClass.PAN             -> extractPAN(text)
        DocumentClass.VOTER_ID        -> extractVoterID(text)
        DocumentClass.DRIVING_LICENCE -> extractDrivingLicence(text)
        DocumentClass.PASSPORT        -> extractPassport(text, backText)
        DocumentClass.OTHER           -> extractOther(text)
    }

    // =========================================================================
    // AADHAAR
    // iOS: DocumentFieldExtractorService+Aadhaar.swift
    // =========================================================================

    private fun extractAadhaar(frontText: String, backText: String): List<DocumentField> {
        val normedFront = normaliseDigits(frontText)
        val normedBack  = normaliseDigits(backText)
        val combined    = if (backText.isNotBlank()) "$normedFront\n$normedBack" else normedFront

        val fields = mutableListOf<DocumentField>()

        // ── VID (must run before UID — same order as iOS) ─────────────────
        // VID: 16 digits in "XXXX XXXX XXXX XXXX" form (back side preferred)
        val vidText = if (backText.isNotBlank()) normedBack else normedFront
        val vid = REGEX_AADHAAR_VID.find(vidText)?.value
            ?: REGEX_AADHAAR_VID.find(normedFront)?.value
        if (vid != null) {
            fields += DocumentField(label = "VID Number", value = vid.replace(" ", ""))
        }

        // ── UID (strip VID from text to avoid false match) ────────────────
        val frontForUID = if (vid != null) normedFront.replace(vid, "") else normedFront
        val uid = findAadhaarUID(frontForUID)
        if (uid != null) {
            fields += DocumentField(label = "Aadhaar Number", value = uid)
        }

        // ── Name ──────────────────────────────────────────────────────────
        val name = extractAadhaarName(frontText)
        if (name != null) {
            fields += DocumentField(label = "Name", value = name)
        }

        // ── DOB ───────────────────────────────────────────────────────────
        val dob = detectLabeledDate("do\\b|dob|date\\s+of\\s+birth|जन्म", frontText)
            ?: detectFirstDate(frontText)
        if (dob != null) {
            fields += DocumentField(label = "Date of Birth", value = dob)
        }

        // ── Gender ────────────────────────────────────────────────────────
        val gender = extractAadhaarGender(frontText)
        if (gender != null) {
            fields += DocumentField(label = "Gender", value = gender)
        }

        // ── Address (from back, fallback front) ───────────────────────────
        val addrText = if (backText.isNotBlank()) backText else frontText
        val address  = extractAadhaarAddress(addrText)
        if (address != null) {
            fields += DocumentField(label = "Address", value = address)
        }

        // ── Guardian (S/O, D/O, W/O) ─────────────────────────────────────
        val guardian = detectGuardianAadhaar(combined)
        if (guardian != null) {
            val parts = guardian.split(":", limit = 2)
            if (parts.size == 2) {
                fields += DocumentField(label = "Guardian Relation", value = parts[0].trim())
                fields += DocumentField(label = "Guardian Name",     value = parts[1].trim())
            }
        }

        // ── PIN Code ──────────────────────────────────────────────────────
        val pin = REGEX_PIN.find(addrText)?.value
        if (pin != null) {
            fields += DocumentField(label = "PIN Code", value = pin)
        }

        // ── District ──────────────────────────────────────────────────────
        val distMatch = Regex("""(?i)\bDIST\.?\s*:?\s*([A-Za-z][A-Za-z\s]{2,25}?)(?=[,\n]|$)""")
            .find(addrText)
        if (distMatch != null) {
            fields += DocumentField(label = "District", value = distMatch.groupValues[1].trim())
        }

        return fields
    }

    private fun findAadhaarUID(text: String): String? {
        // Spaced form: 4 4 4 (not VID)
        REGEX_AADHAAR_UID_SPACED.find(text)?.let { return it.value }
        // Compact form: 12 consecutive digits
        REGEX_AADHAAR_UID_COMPACT.find(text)?.let { return it.value }
        return null
    }

    private fun extractAadhaarName(text: String): String? {
        val dobPattern = Regex("""\d{2}/\d{2}/\d{4}""")
        val pinPattern = Regex("""\b\d{6}\b""")
        val headerPhrases = listOf("government", "india", "aadhaar", "unique", "authority",
            "identification", "uid")
        val addressKeywords = listOf("street", "road", "nagar", "colony", "district", "village",
            "house", "flat", "block", "near", "post", "sector", "ward",
            "taluk", "tehsil", "mandal", "state", "city", "town",
            "s/o", "d/o", "w/o", "c/o", "h/o")

        for (line in text.lines()) {
            val trimmed = line.trim()
            val lower   = trimmed.lowercase()
            if (trimmed.isEmpty()) continue
            if (headerPhrases.any { lower.contains(it) }) continue
            if (addressKeywords.any { lower.contains(it) }) continue
            if (trimmed.contains(",")) continue
            if (pinPattern.containsMatchIn(trimmed)) continue
            if (trimmed.filter { it.isDigit() }.length >= 12) continue
            if (dobPattern.containsMatchIn(trimmed)) continue

            val words = trimmed.split(" ").filter { it.isNotEmpty() }
            if (words.size in 2..3 && words.all { it.firstOrNull()?.isLetter() == true }) {
                return trimmed
            }
        }
        return null
    }

    private fun extractAadhaarGender(text: String): String? {
        val upper = text.uppercase()
        return when {
            "MALE" in upper || "पुरुष" in text   -> "Male"
            "FEMALE" in upper || "महिला" in text -> "Female"
            else -> null
        }
    }

    private fun extractAadhaarAddress(text: String): String? {
        val lines = text.lines()
        val addrIdx = lines.indexOfFirst { it.lowercase().contains("address") }
        if (addrIdx >= 0) {
            val addrLines = lines.drop(addrIdx + 1).takeWhile { line ->
                !line.trim().startsWith("date", ignoreCase = true)
            }.take(6).filter { it.trim().isNotEmpty() }
            if (addrLines.isNotEmpty()) {
                return addrLines.joinToString(", ").take(200)
            }
        }
        // Fallback: lines containing comma (address fragments use commas)
        val addrFragments = lines.filter { it.contains(",") && it.length > 10 }.take(3)
        return addrFragments.joinToString(", ").ifEmpty { null }?.take(200)
    }

    private fun detectGuardianAadhaar(text: String): String? {
        // iOS: S/O, W/O, D/O, C/O, S.O., W.O., D.O.
        val m = Regex("""(?i)\b(S/O|W/O|D/O|C/O|S\.O\.|W\.O\.|D\.O\.)\s*:?\s*([^,\n]{3,40})""")
            .find(text) ?: return null
        val relation = m.groupValues[1].uppercase()
        val guardian = m.groupValues[2].trim()
        val relLabel = when (relation.replace(".", "").replace("/", "")) {
            "SO" -> "S/O"; "WO" -> "W/O"; "DO" -> "D/O"; "CO" -> "C/O"
            else -> relation
        }
        return "$relLabel: $guardian"
    }

    // =========================================================================
    // PAN CARD
    // iOS: DocumentFieldExtractorService+PAN.swift
    // =========================================================================

    private fun extractPAN(text: String): List<DocumentField> {
        val yearFixed = panSanitiseYears(text)
        val lines     = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val fixedLines = yearFixed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val normed     = normaliseDigits(text)

        val fields = mutableListOf<DocumentField>()

        // PAN Number
        val pan = detectPANNumber(normed)
        if (pan != null) fields += DocumentField(label = "PAN Number", value = pan)

        // Entity Type from 4th char of PAN
        val entityType = pan?.let { panEntityType(it) }
        if (entityType != null) fields += DocumentField(label = "Entity Type", value = entityType)

        // PAN line index for positional extraction
        val panLineIdx = lines.indexOfFirst {
            Regex("""\b[A-Z]{5}\d{4}[A-Z]\b""").containsMatchIn(it)
        }

        // Name — Strategy A (label-based), B (positional), C (fusion fallback)
        var name     = panLabelBasedName(lines)
        var father   = panLabelBasedFather(lines)

        if ((name == null || father == null) && panLineIdx >= 0) {
            val pos = panPositionalNames(lines, panLineIdx)
            if (name   == null) name   = pos.first
            if (father == null) father = pos.second
        }
        if (name == null) {
            val caps   = extractAllCapsNames(text, 2, PAN_FUSION_SKIP)
            val (n, _) = fusedName(caps.getOrNull(0), caps.getOrNull(1))
            name = n
        }
        if (father == null) {
            val caps   = extractAllCapsNames(text, 2, PAN_FUSION_SKIP)
            val (_, f) = fusedName(caps.getOrNull(0), caps.getOrNull(1))
            father = f
        }

        // Levenshtein dedup / swap
        if (name != null && father != null) {
            if (levenshtein(name, father) <= 2) {
                father = null
            }
        }

        if (name   != null) fields += DocumentField(label = "Name",          value = name)
        if (father != null) fields += DocumentField(label = "Father's Name",  value = father)

        // DOB
        val dob = panExtractDOB(fixedLines, yearFixed)
        if (dob != null) fields += DocumentField(label = "Date of Birth", value = dob)

        return fields
    }

    private fun panSanitiseYears(text: String): String {
        // iOS: \b(1[0]\d{2})\b — OCR reads 1950s as 1050s; fix +900
        return Regex("""\b(1[0]\d{2})\b""").replace(text) { mr ->
            val year = mr.value.toIntOrNull()
            if (year != null && year in 1000..1099) (year + 900).toString() else mr.value
        }
    }

    private fun panEntityType(pan: String): String? {
        if (pan.length != 10) return null
        return when (pan[3]) {
            'P' -> "Individual"; 'C' -> "Company"; 'H' -> "HUF"
            'F' -> "Firm";       'A' -> "AOP";     'T' -> "Trust"
            'B' -> "BOI";        'L' -> "Local Authority"
            'J' -> "Juridical Person"; 'G' -> "Government"
            else -> null
        }
    }

    private fun panLabelBasedName(lines: List<String>): String? {
        for ((i, line) in lines.withIndex()) {
            val lower = line.lowercase()
            if (!Regex(PAN_NAME_LABEL_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn(lower)) continue
            if (lower.contains("father") || lower.contains("birth") || lower.contains("date")) continue
            for (j in (i + 1) until minOf(i + 4, lines.size)) {
                val candidate = lines[j].trim()
                if (panIsHeaderNoise(candidate)) continue
                if (Regex(PAN_NAME_LABEL_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn(candidate.lowercase())) continue
                if (Regex(PAN_FATHER_LABEL_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn(candidate.lowercase())) break
                if (candidate.firstOrNull()?.isDigit() == true) break
                val words = candidate.split(" ").filter { it.length >= 2 && it.all { c -> c.isLetter() } }
                if (words.isNotEmpty()) return panTitleCase(words)
            }
        }
        return null
    }

    private fun panLabelBasedFather(lines: List<String>): String? {
        for ((i, line) in lines.withIndex()) {
            if (!Regex(PAN_FATHER_LABEL_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn(line.lowercase())) continue
            for (j in (i + 1) until minOf(i + 4, lines.size)) {
                val candidate = lines[j].trim()
                if (panIsHeaderNoise(candidate)) continue
                if (candidate.firstOrNull()?.isDigit() == true) break
                val words = candidate.split(" ").filter { it.length >= 2 && it.all { c -> c.isLetter() } }
                if (words.isNotEmpty()) return panTitleCase(words)
            }
        }
        return null
    }

    private fun panPositionalNames(lines: List<String>, panLineIdx: Int): Pair<String?, String?> {
        val isPanEarly = panLineIdx.toDouble() <= lines.size * 0.40
        val candidates = mutableListOf<String>()

        if (isPanEarly) {
            for (j in (panLineIdx + 1) until lines.size) {
                val qualified = panQualifyNameLine(lines[j])
                if (qualified != null) {
                    candidates.add(qualified)
                    if (candidates.size == 2) break
                }
            }
        } else {
            for (j in (panLineIdx - 1) downTo 0) {
                val qualified = panQualifyNameLine(lines[j])
                if (qualified != null) {
                    candidates.add(0, qualified)
                    if (candidates.size == 2) break
                }
            }
        }
        return Pair(candidates.getOrNull(0), candidates.getOrNull(1))
    }

    private fun panQualifyNameLine(line: String): String? {
        val trimmed = line.trim()
        if (panIsHeaderNoise(trimmed)) return null
        if (trimmed.any { it.isDigit() }) return null
        val lower = trimmed.lowercase()
        if (Regex(PAN_NAME_LABEL_PATTERN,   RegexOption.IGNORE_CASE).containsMatchIn(lower)) return null
        if (Regex(PAN_FATHER_LABEL_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn(lower)) return null
        if (Regex(PAN_DOB_LABEL_PATTERN,    RegexOption.IGNORE_CASE).containsMatchIn(lower)) return null
        val words = trimmed.split(" ").filter { it.length >= 2 && it.all { c -> c.isLetter() } }
        if (words.size < 2) return null
        val titleCased = panTitleCase(words)
        if (indianNameScore(titleCased) <= 0.15) return null
        return titleCased
    }

    private fun panExtractDOB(fixedLines: List<String>, fixedText: String): String? {
        for ((i, line) in fixedLines.withIndex()) {
            if (!Regex(PAN_DOB_LABEL_PATTERN, RegexOption.IGNORE_CASE).containsMatchIn(line.lowercase())) continue
            val searchText = if (i + 1 < fixedLines.size) "$line ${fixedLines[i + 1]}" else line
            detectFirstDate(searchText)?.let { return it }
        }
        return detectFirstDate(fixedText)
    }

    private fun panIsHeaderNoise(line: String): Boolean {
        val lower = line.lowercase().trim()
        if (lower.isEmpty()) return true
        val noiseKeywords = listOf("income tax", "income ta", "partment", "dept", "department",
            "govt", "government", "india", "permanent account", "account number",
            "commissioner", "signature", "fetter", "canuc", "satoluie")
        if (noiseKeywords.any { lower.contains(it) }) return true
        val stripped = lower.filter { it.isDigit() }
        if (stripped.length == 8 && lower.none { it.isLetter() }) return true
        return false
    }

    private fun panTitleCase(words: List<String>): String =
        words.joinToString(" ") { w ->
            w[0].uppercaseChar() + w.drop(1).lowercase()
        }

    // =========================================================================
    // VOTER ID
    // iOS: DocumentFieldExtractorService+VoterID.swift
    // =========================================================================

    private fun extractVoterID(text: String): List<DocumentField> {
        val lines    = voterIDCleanLines(text)
        val fullClean = lines.joinToString("\n")
        val normed   = normaliseDigits(fullClean)

        val fields = mutableListOf<DocumentField>()

        // EPIC Number
        val epic = voterIDExtractEPIC(lines, normed)
        if (epic != null) fields += DocumentField(label = "EPIC Number", value = epic)

        // Voter Name
        val name = voterIDExtractVoterName(lines, fullClean)
        if (name != null) fields += DocumentField(label = "Name", value = name)

        // Guardian
        val guardian = voterIDExtractGuardian(lines)
        if (guardian != null) {
            fields += DocumentField(label = "Guardian Relation", value = guardian.first)
            fields += DocumentField(label = "Guardian Name",     value = guardian.second)
        }

        // Gender
        val gender = voterIDExtractGender(lines)
        if (gender != null) fields += DocumentField(label = "Gender", value = gender)

        // DOB / Age
        val dobAge = voterIDExtractDOB(lines, fullClean)
        if (dobAge.first != null)  fields += DocumentField(label = "Date of Birth", value = dobAge.first!!)
        if (dobAge.second != null) fields += DocumentField(label = "Age",           value = dobAge.second!!)

        // Address
        val address = voterIDExtractAddress(lines)
        if (address != null) fields += DocumentField(label = "Address", value = address)

        // Assembly Constituency
        val constituency = detectLabeledValue("assembly\\s+constituency|ac\\s+name", fullClean)
        if (constituency != null) fields += DocumentField(label = "Assembly Constituency", value = constituency)

        return fields
    }

    private fun voterIDCleanLines(text: String): List<String> {
        val watermarkTokens = setOf("EPIC", "SPIC", "PIC", "EPIG", "EPIO", "ERIC", "EPI", "El", "PL", "Pl", "Pic", "EPl")
        return text.lines()
            .map { it.trim() }
            .filter { line ->
                if (line.isEmpty()) return@filter false
                val tokens = line.split(" ").filter { it.isNotEmpty() }
                !tokens.all { watermarkTokens.contains(it) }
            }
    }

    private fun voterIDExtractEPIC(lines: List<String>, fullText: String): String? {
        // Strategy A: header anchor
        for ((i, line) in lines.withIndex()) {
            val upper = line.uppercase()
            if (!upper.contains("ELECTOR PHOTO IDENTITY CARD") &&
                !upper.endsWith("IDENTITY CARD") &&
                !(upper.contains("ELECTION COMMISSION") && upper.endsWith("CARD"))) continue

            for (j in (i + 1) until minOf(i + 4, lines.size)) {
                val candidate = lines[j].replace(" ", "")
                if (Regex("^${VOTER_ID_EPIC_REGEX}$").matches(candidate)) return candidate
                val lower = lines[j].lowercase()
                if (lower.contains("name") || lower.contains("election") || lower.contains("elector")) break
                if (candidate.length > 15) break
            }
        }
        // Strategy B: regex scan
        val normed = normaliseDigits(fullText)
        for (line in normed.lines()) {
            val candidate = line.trim().replace(" ", "")
            if (Regex("^${VOTER_ID_EPIC_REGEX}$").matches(candidate)) return candidate
        }
        return firstMatch(VOTER_ID_EPIC_REGEX, normed)
    }

    private fun voterIDExtractVoterName(lines: List<String>, fullText: String): String? {
        // Label-based: "Elector's Name:" / "Name:"
        for ((i, line) in lines.withIndex()) {
            if (!Regex(VOTER_ID_NAME_LABEL, RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
            // Inline value
            val colonIdx = line.indexOf(':')
            if (colonIdx >= 0) {
                val inline = line.substring(colonIdx + 1).trim()
                if (inline.length >= 2) return inline.let { capitalise(it) }
            }
            // Next-line value
            if (i + 1 < lines.size) {
                val next = lines[i + 1].trim()
                if (next.isNotEmpty() && !Regex(VOTER_ID_NAME_STOP, RegexOption.IGNORE_CASE).containsMatchIn(next)) {
                    return capitalise(next)
                }
            }
        }
        // ALL-CAPS fallback
        return extractAllCapsNames(fullText, 1, listOf("election", "commission", "voter", "epic", "identity", "card")).firstOrNull()
    }

    private fun voterIDExtractGuardian(lines: List<String>): Pair<String, String>? {
        val guardianPatterns = listOf(
            Regex("""(?i)fa[rt]her'?[cs]?\s+(?:name|lame)\s*:?\s*"""),
            Regex("""(?i)mother'?s?\s+name\s*:?\s*"""),
            Regex("""(?i)husband'?s?\s+name\s*:?\s*"""),
            Regex("""(?i)\bS/O\b\s*:?\s*"""),
            Regex("""(?i)\bW/O\b\s*:?\s*"""),
            Regex("""(?i)\bD/O\b\s*:?\s*"""),
            Regex("""(?i)\bH/O\b\s*:?\s*""")
        )
        val relations = listOf("Father", "Mother", "Husband", "S/O", "W/O", "D/O", "H/O")

        for ((patIdx, pattern) in guardianPatterns.withIndex()) {
            for ((i, line) in lines.withIndex()) {
                val match = pattern.find(line) ?: continue
                // Inline value
                val afterMatch = line.substring(match.range.last + 1).trim()
                if (afterMatch.length >= 2) {
                    return Pair(relations.getOrElse(patIdx) { "Guardian" }, capitalise(afterMatch))
                }
                // Next line
                if (i + 1 < lines.size) {
                    val next = lines[i + 1].trim()
                    if (next.isNotEmpty()) {
                        return Pair(relations.getOrElse(patIdx) { "Guardian" }, capitalise(next))
                    }
                }
            }
        }
        return null
    }

    private fun voterIDExtractGender(lines: List<String>): String? {
        for (line in lines) {
            val upper = line.uppercase()
            if ("FEMALE" in upper || "महिला" in line) return "Female"
            if ("MALE"   in upper || "पुरुष"   in line) return "Male"
        }
        return null
    }

    private fun voterIDExtractDOB(lines: List<String>, fullText: String): Pair<String?, String?> {
        var dob: String? = null
        var age: String? = null

        // Labeled DOB
        dob = detectLabeledDate("dob|birth|year|date", fullText)

        // "Age as on" pattern
        val ageAsOnMatch = Regex("""(?i)age\s+as\s+on""").find(fullText)
        if (ageAsOnMatch != null) {
            val ageLineMatch = Regex("""^\d{1,3}$""", RegexOption.MULTILINE).find(
                fullText.substring(ageAsOnMatch.range.last)
            )
            if (ageLineMatch != null) age = ageLineMatch.value
        }
        // Inline age
        if (age == null) {
            val inlineAge = Regex("""(?i)age(?:\s+as\s+on[\s\d.:/\-]+?)?\s*:?\s*(\d{1,3})\b""").find(fullText)
            if (inlineAge != null) age = inlineAge.groupValues[1]
        }

        if (dob == null) dob = detectFirstDate(fullText)

        return Pair(dob, age)
    }

    private fun voterIDExtractAddress(lines: List<String>): String? {
        val addrIdx = lines.indexOfFirst { Regex("""\baddress\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        if (addrIdx < 0) return null
        val after = lines.drop(addrIdx + 1)
            .takeWhile { !it.trim().startsWith("date", ignoreCase = true) }
            .take(5)
            .filter { it.isNotEmpty() }
        return after.joinToString(", ").ifEmpty { null }?.take(200)
    }

    // =========================================================================
    // DRIVING LICENCE
    // iOS: DocumentFieldExtractorService.swift (extractDrivingLicence)
    // =========================================================================

    private fun extractDrivingLicence(text: String): List<DocumentField> {
        val normed = normaliseDigits(text)
        val fields = mutableListOf<DocumentField>()

        val dlNum = detectDLNumber(normed)
        if (dlNum != null) fields += DocumentField(label = "DL Number", value = dlNum)

        // Name (ALL-CAPS heuristic)
        val name = extractAllCapsNames(text, 1, DL_SKIP_KEYWORDS).firstOrNull()
            ?: detectPersonNameHeuristic(text)
        if (name != null) fields += DocumentField(label = "Name", value = name)

        // DOB
        val dob = detectLabeledDate("dob|date\\s+of\\s+birth|birth", text)
            ?: detectAllDates(text).firstOrNull()
        if (dob != null) fields += DocumentField(label = "Date of Birth", value = dob)

        // Valid Until
        val validUntil = detectLabeledDate("valid\\s+upto|validity|expiry|expires|valid\\s+till|valid\\s+to", text)
            ?: detectAllDates(text).getOrNull(1)
        if (validUntil != null) fields += DocumentField(label = "Valid Until", value = validUntil)

        // Licence Class
        val dlClass = Regex("""(?i)(?:class|cov|category)\s*[:\-]?\s*([A-Z]{2,10}(?:/[A-Z]{2,10})*)""")
            .find(text)?.groupValues?.get(1)
        if (dlClass != null) fields += DocumentField(label = "Licence Class", value = dlClass)

        return fields
    }

    // =========================================================================
    // PASSPORT
    // iOS: DocumentFieldExtractorService+Passport.swift
    // =========================================================================

    private fun extractPassport(text: String, backText: String): List<DocumentField> {
        val combined = if (backText.isNotBlank()) "$text\n$backText" else text
        val fields   = mutableListOf<DocumentField>()

        // Try MRZ first (confidence 1.0 when check digits pass)
        val mrz = parseMRZ(combined)
        if (mrz != null) {
            if (mrz.surname.isNotEmpty())        fields += DocumentField(label = "Surname",          value = mrz.surname)
            if (mrz.givenName.isNotEmpty())      fields += DocumentField(label = "Given Name",       value = mrz.givenName)
            if (mrz.surname.isNotEmpty() && mrz.givenName.isNotEmpty())
                                                  fields += DocumentField(label = "Name",             value = "${mrz.givenName} ${mrz.surname}")
            fields += DocumentField(label = "Passport Number",  value = mrz.passportNumber)
            fields += DocumentField(label = "Date of Birth",    value = mrz.dateOfBirth)
            fields += DocumentField(label = "Date of Expiry",   value = mrz.dateOfExpiry)
            fields += DocumentField(label = "Sex",              value = mrz.sex)
        } else {
            // Regex fallback
            val passNum = detectPassportNumber(normaliseDigits(combined))
            if (passNum != null) fields += DocumentField(label = "Passport Number", value = passNum)

            val surname   = detectLabeledValue("sur[nm]ame|sumname", combined)
            val givenName = detectLabeledValue("given\\s*name", combined)
            val name      = when {
                surname != null && givenName != null -> "$givenName $surname"
                surname != null                      -> surname
                givenName != null                    -> givenName
                else                                 -> null
            }
            if (name   != null) fields += DocumentField(label = "Name",       value = name)
            if (surname   != null) fields += DocumentField(label = "Surname",  value = surname)
            if (givenName != null) fields += DocumentField(label = "Given Name", value = givenName)

            val dob = detectLabeledDate("date\\s+of\\s+birth|dob", combined)
            if (dob != null) fields += DocumentField(label = "Date of Birth", value = dob)

            val expiry = detectLabeledDate("date\\s+of\\s+expir[yi]|date\\s+at\\s+expir|date\\s+of\\s+issue|expiry", combined)
            if (expiry != null) fields += DocumentField(label = "Date of Expiry", value = expiry)

            val nationality = detectLabeledValue("nationalit", combined)
            if (nationality != null) fields += DocumentField(label = "Nationality", value = nationality)

            val placeOfBirth = detectLabeledValue("place\\s+of\\s+birth", combined)
            if (placeOfBirth != null) fields += DocumentField(label = "Place of Birth", value = placeOfBirth)

            val placeOfIssue = detectLabeledValue("place\\s+of\\s+(i[sl]su[ae]?|issue|lssua|lusua)", combined)
            if (placeOfIssue != null) fields += DocumentField(label = "Place of Issue", value = placeOfIssue)

            val sex = Regex("""\bsex\b|sua\b""", RegexOption.IGNORE_CASE).find(combined)?.let {
                detectLabeledValue("sex", combined)
            }
            if (sex != null) fields += DocumentField(label = "Sex", value = sex)
        }

        return fields
    }

    // ── ICAO TD3 MRZ Parser ───────────────────────────────────────────────────

    /**
     * Scans text for consecutive 44-character ICAO TD3 MRZ lines.
     * iOS equivalent: DocumentFieldExtractorService.parseMRZ(from:)
     */
    private fun parseMRZ(text: String): MRZResult? {
        val candidates = text.lines().mapNotNull { normaliseMRZLine(it) }
        if (candidates.size < 2) return null

        for (i in 0 until candidates.size - 1) {
            val l1 = candidates[i]
            val l2 = candidates[i + 1]
            if (!l1.startsWith("P") || l1.length != 44 || l2.length != 44) continue

            // Name from line 1 (positions 5–43)
            val namePart = l1.substring(5, 44)
            val halves   = namePart.split("<<")
            val surname  = halves.getOrNull(0)?.replace("<", " ")?.trim()?.let { capitalise(it) } ?: ""
            val given    = halves.getOrNull(1)?.replace("<", " ")?.trim()?.let { capitalise(it) } ?: ""

            // Passport number (0–7), check digit at 8
            val passRaw   = l2.substring(0, 8)
            val passCheck = l2[8]
            val passNum   = passRaw.replace("<", "")

            // DOB (13–18), check digit at 19
            val dobRaw   = l2.substring(13, 19)
            val dobCheck = l2[19]

            // Sex (20)
            val sex = when (l2[20]) { 'M' -> "Male"; 'F' -> "Female"; else -> "Unspecified" }

            // Expiry (21–26), check digit at 27
            val expRaw   = l2.substring(21, 27)
            val expCheck = l2[27]

            // Validate check digits
            val passOK = mrzCheckDigit(passRaw) == passCheck
            val dobOK  = mrzCheckDigit(dobRaw)  == dobCheck
            val expOK  = mrzCheckDigit(expRaw)  == expCheck

            val dobStr = formatMRZDate(dobRaw,  isBirth = true)  ?: continue
            val expStr = formatMRZDate(expRaw,  isBirth = false) ?: continue

            return MRZResult(
                surname        = surname,
                givenName      = given,
                passportNumber = passNum,
                dateOfBirth    = dobStr,
                dateOfExpiry   = expStr,
                sex            = sex,
                allCheckDigitsOK = passOK && dobOK && expOK
            )
        }
        return null
    }

    private data class MRZResult(
        val surname: String, val givenName: String,
        val passportNumber: String,
        val dateOfBirth: String, val dateOfExpiry: String,
        val sex: String,
        val allCheckDigitsOK: Boolean
    )

    private fun normaliseMRZLine(raw: String): String? {
        val stripped = raw.replace(" ", "").uppercase()
        if (stripped.length != 44) return null
        val mrzChars = Regex("[^A-Z0-9<]")
        if (mrzChars.containsMatchIn(stripped)) return null
        if (stripped.count { it == '<' } < 5) return null
        return stripped
    }

    private fun mrzCheckDigit(field: String): Char {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        for ((idx, ch) in field.withIndex()) {
            val v = when {
                ch == '<'     -> 0
                ch.isDigit()  -> ch.digitToInt()
                else          -> ch.code - 'A'.code + 10
            }
            sum += v * weights[idx % 3]
        }
        return '0' + (sum % 10)
    }

    private fun formatMRZDate(yymmdd: String, isBirth: Boolean): String? {
        if (yymmdd.length != 6) return null
        val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return null
        val mm = yymmdd.substring(2, 4).toIntOrNull() ?: return null
        val dd = yymmdd.substring(4, 6).toIntOrNull() ?: return null
        if (mm !in 1..12 || dd !in 1..31) return null
        val century = if (isBirth && yy >= 30) 1900 else 2000
        val cal     = Calendar.getInstance()
        cal.set(century + yy, mm - 1, dd)
        return SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(cal.time)
    }

    // =========================================================================
    // OTHER / UNKNOWN
    // iOS: DocumentFieldExtractorService.extractOther(text:)
    // =========================================================================

    private fun extractOther(text: String): List<DocumentField> {
        val normed = normaliseDigits(text)
        val fields = mutableListOf<DocumentField>()

        // Step 1: Known ID patterns (most-specific first)
        val (idLabel, idValue) = when {
            detectPANNumber(normed)      != null -> Pair("PAN Number",     detectPANNumber(normed))
            detectDLNumber(normed)       != null -> Pair("DL Number",      detectDLNumber(normed))
            detectEPICNumber(normed)     != null -> Pair("EPIC Number",    detectEPICNumber(normed))
            detectAadhaarUID(normed)     != null -> Pair("Aadhaar Number", detectAadhaarUID(normed))
            detectPassportNumber(normed) != null -> Pair("Passport Number",detectPassportNumber(normed))
            detectGenericIDNumber(normed)!= null -> Pair("ID Number",      detectGenericIDNumber(normed))
            else                                 -> Pair("ID Number",      null)
        }
        if (idValue != null) fields += DocumentField(label = idLabel, value = idValue)

        // Step 2–3: Name + dates
        val name  = detectPersonNameHeuristic(text)
        val dates = detectAllDates(text)
        if (name != null) fields += DocumentField(label = "Name", value = name)
        dates.take(2).forEachIndexed { i, d ->
            fields += DocumentField(label = if (i == 0) "Date" else "Date ${i + 1}", value = d)
        }

        // Step 4: Generic KV sweep if very little found
        if (fields.size < 2) {
            val already = fields.map { it.label }.toSet()
            genericKVSweep(text, already).forEach { (k, v) ->
                fields += DocumentField(label = k, value = v)
            }
        }

        // Step 5: Last resort — first readable line
        if (fields.isEmpty()) {
            val firstLine = text.lines().firstOrNull { it.trim().length >= 3 }
            if (firstLine != null) fields += DocumentField(label = "Note", value = firstLine.trim())
        }

        return fields
    }

    private fun genericKVSweep(text: String, exclude: Set<String>): List<Pair<String, String>> {
        val pattern = Regex("""^([A-Za-z][A-Za-z\s\-/]{2,38})\s*[:\-]\s*(.{2,80})$""")
        val results = mutableListOf<Pair<String, String>>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = pattern.matchEntire(trimmed) ?: continue
            val key = match.groupValues[1].trim().let { k ->
                k.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            }
            val value = match.groupValues[2].trim()
            if (key.isNotEmpty() && value.isNotEmpty() && !exclude.contains(key) &&
                results.none { it.first == key }) {
                results += Pair(key, value)
                if (results.size == 8) break
            }
        }
        return results
    }

    // =========================================================================
    // DIGIT NORMALISER
    // iOS: DocumentFieldExtractorService.normaliseDigits(in:)
    // =========================================================================

    /**
     * Applies look-alike character→digit corrections to tokens that are ≥70%
     * numeric (real digits + look-alikes). Newlines preserved.
     * Look-alike map: O/o→0, I/l/i→1, B→8, G→6, S→5, Z→2
     */
    fun normaliseDigits(text: String): String {
        val tokens = text.split(" ")
        return tokens.joinToString(" ") { token ->
            if (!isMostlyNumeric(token)) token
            else token
                .replace('O', '0').replace('o', '0')
                .replace('I', '1').replace('l', '1').replace('i', '1')
                .replace('B', '8')
                .replace('G', '6')
                .replace('S', '5')
                .replace('Z', '2')
        }
    }

    private fun isMostlyNumeric(token: String): Boolean {
        if (token.isEmpty()) return false
        val lookAlikes = setOf('O', 'o', 'I', 'l', 'i', 'B', 'G', 'S', 'Z')
        val digitLike  = token.count { it.isDigit() || lookAlikes.contains(it) }
        return digitLike.toDouble() / token.length >= 0.70
    }

    // =========================================================================
    // ID PATTERN HELPERS
    // iOS: DocumentFieldExtractorService.swift — ID Pattern Helpers
    // =========================================================================

    fun detectAadhaarUID(text: String): String? =
        firstMatch("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""", text)

    fun detectPANNumber(text: String): String? =
        firstMatch("""\b[A-Z]{5}\d{4}[A-Z]\b""", text)

    fun detectEPICNumber(text: String): String? =
        firstMatch("""\b[A-Z]{3}\d{7}\b""", text)

    fun detectDLNumber(text: String): String? =
        firstMatch("""\b[A-Z]{2}\d{2}[\s-]?\d{4}[\s-]?\d{7}\b""", text)

    fun detectPassportNumber(text: String): String? =
        firstMatch("""\b[A-Z]\d{7}\b""", text)

    fun detectGenericIDNumber(text: String): String? =
        firstMatch("""\b[A-Z]{1,3}[\s-]?\d{6,12}\b""", text)

    private fun firstMatch(pattern: String, text: String): String? =
        Regex(pattern).find(text)?.value

    // =========================================================================
    // DATE HELPERS
    // iOS: DocumentFieldExtractorService.swift — Date Helpers
    // =========================================================================

    fun detectFirstDate(text: String): String? = detectAllDates(text).firstOrNull()

    fun detectAllDates(text: String): List<String> {
        // Parse dates in common Indian formats: DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY
        val dateRegex = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})\b""")
        val results   = mutableListOf<Date>()
        val fmt       = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)

        for (match in dateRegex.findAll(text)) {
            val d = match.groupValues[1].toIntOrNull() ?: continue
            val m = match.groupValues[2].toIntOrNull() ?: continue
            var y = match.groupValues[3].toIntOrNull() ?: continue
            if (y in 0..99) y += if (y >= 30) 1900 else 2000
            if (m !in 1..12 || d !in 1..31) continue
            val cal = Calendar.getInstance()
            cal.set(y, m - 1, d)
            results += cal.time
        }
        return results.map { fmt.format(it) }
    }

    /**
     * iOS: detectLabeledDate — scans for a label regex on a line,
     * then extracts the first date from that line + the next line.
     */
    fun detectLabeledDate(label: String, text: String): String? {
        val labelRegex = Regex(label, RegexOption.IGNORE_CASE)
        val lines      = text.lines()
        for ((i, line) in lines.withIndex()) {
            if (!labelRegex.containsMatchIn(line)) continue
            val searchText = if (i + 1 < lines.size) "$line ${lines[i + 1]}" else line
            detectFirstDate(searchText)?.let { return it }
        }
        return null
    }

    /**
     * iOS: detectLabeledValue — finds a value after a labelled prefix on the same line.
     */
    fun detectLabeledValue(label: String, text: String): String? {
        val pattern = "(?:$label)\\s*[:\\-]?\\s*(.{3,40})"
        val match   = Regex(pattern, RegexOption.IGNORE_CASE).find(text) ?: return null
        return match.groupValues.getOrNull(1)?.trim()
    }

    // =========================================================================
    // NAME HELPERS
    // iOS: DocumentFieldExtractorService.swift — ALL-CAPS Name Extractor,
    //      Name Scoring & Deduplication
    // =========================================================================

    /**
     * Extracts up to [count] person names from ALL-CAPS OCR text.
     * iOS: extractAllCapsNames(from:count:skipKeywords:)
     *
     * A line qualifies when:
     *  - No digit characters
     *  - No [skipKeywords] match (case-insensitive)
     *  - Only letters, spaces, hyphens, dots
     *  - ≥ 2 words with ≥ 2 letters each
     */
    fun extractAllCapsNames(text: String, count: Int, skipKeywords: List<String>): List<String> {
        val results = mutableListOf<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.any { it.isDigit() }) continue
            val lower = trimmed.lowercase()
            if (skipKeywords.any { lower.contains(it) }) continue
            val allowed = trimmed.filter { it.isLetter() || it.isWhitespace() || it == '-' || it == '.' }
            if (allowed.length != trimmed.length) continue
            val words = trimmed.split(" ").filter { it.length >= 2 && it.all { c -> c.isLetter() } }
            if (words.size < 2) continue
            val titleCased = words.joinToString(" ") { w ->
                w[0].uppercaseChar() + w.drop(1).lowercase()
            }
            results += titleCased
            if (results.size == count) break
        }
        return results
    }

    /**
     * Heuristic person name detection for ALL-CAPS OCR text.
     * Replaces iOS NLTagger .personalName for Android.
     */
    private fun detectPersonNameHeuristic(text: String): String? =
        extractAllCapsNames(text, 1, GENERIC_SKIP_KEYWORDS).firstOrNull()

    /**
     * Returns a 0.0–1.0 heuristic score estimating how likely [candidate] is
     * an Indian person name.
     * iOS: DocumentFieldExtractorService.indianNameScore(_:)
     */
    fun indianNameScore(candidate: String): Double {
        val words = candidate.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0.0
        var score = 0.0

        val lowerWords = words.map { it.lowercase() }
        if (lowerWords.any { INDIAN_SURNAMES.contains(it) }) score += 0.40
        if (words.size in 2..4) score += 0.20
        val allTitleCased = words.all { w -> w.isNotEmpty() && w[0].isUpperCase() && w.drop(1).all { !it.isUpperCase() || !it.isLetter() } }
        if (allTitleCased) score += 0.20
        val allLetterWords = words.all { w -> w.length in 2..15 && w.all { it.isLetter() } }
        if (allLetterWords) score += 0.10
        if (lowerWords.none { NAME_BLACKLIST.contains(it) }) score += 0.10

        return minOf(score, 1.0)
    }

    /**
     * Selects the better of two name candidates using indianNameScore.
     * iOS: DocumentFieldExtractorService.fusedName(heuristic:tagger:...)
     * Returns (name, father) where name is the more likely cardholder.
     */
    fun fusedName(heuristic: String?, tagger: String?): Pair<String?, String?> {
        return when {
            heuristic != null && tagger == null -> Pair(heuristic, null)
            heuristic == null && tagger != null -> Pair(tagger, null)
            heuristic != null && tagger != null -> {
                val hScore = indianNameScore(heuristic)
                val tScore = indianNameScore(tagger)
                if (abs(hScore - tScore) <= 0.10) Pair(heuristic, tagger)
                else if (hScore >= tScore)         Pair(heuristic, tagger)
                else                               Pair(tagger, heuristic)
            }
            else -> Pair(null, null)
        }
    }

    /**
     * Standard iterative Levenshtein edit distance — case-insensitive.
     * iOS: DocumentFieldExtractorService.levenshtein(_:_:)
     */
    fun levenshtein(a: String, b: String): Int {
        val s = a.lowercase().toCharArray()
        val t = b.lowercase().toCharArray()
        val m = s.size; val n = t.size
        if (m == 0) return n
        if (n == 0) return m
        var dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                dp[j] = if (s[i-1] == t[j-1]) prev
                         else 1 + minOf(prev, dp[j], dp[j-1])
                prev = temp
            }
        }
        return dp[n]
    }

    private fun capitalise(s: String): String = s.split(" ").joinToString(" ") { word ->
        if (word.isEmpty()) word
        else word[0].uppercaseChar() + word.drop(1).lowercase()
    }

    // =========================================================================
    // REGEX CONSTANTS
    // =========================================================================

    companion object {
        // Aadhaar UID — spaced form, not VID (iOS: spacedUIDRegex)
        private val REGEX_AADHAAR_UID_SPACED  = Regex("""(?<!\d )\d{4} \d{4} \d{4}(?! \d{4})""")
        // Aadhaar UID — compact form (iOS: compactUIDRegex)
        private val REGEX_AADHAAR_UID_COMPACT = Regex("""(?<!\d)\d{12}(?!\d)""")
        // Aadhaar VID — 16 digits in "XXXX XXXX XXXX XXXX" (iOS: VID regex)
        private val REGEX_AADHAAR_VID = Regex("""\b\d{4}[ ]\d{4}[ ]\d{4}[ ]\d{4}\b""")
        // PIN code — 6 digits, non-zero leading
        private val REGEX_PIN = Regex("""\b[1-9]\d{5}\b""")

        // PAN label patterns (iOS: panNameLabelPattern, panFatherLabelPattern, panDOBLabelPattern)
        private const val PAN_NAME_LABEL_PATTERN   = """(?i)(?<![a-zA-Z])(i?name)(?![a-zA-Z])"""
        private const val PAN_FATHER_LABEL_PATTERN = """(?i)father'?s?\s+name"""
        private const val PAN_DOB_LABEL_PATTERN    = """(?i)(date[\s.]*of[\s.]*birth|dob|\bbiab\b|\bdatr\b)"""

        // Voter ID EPIC pattern (iOS: voterIDEPICRegex)
        private const val VOTER_ID_EPIC_REGEX      = """[A-Z]{2,3}/?[0-9]{7,8}"""
        private const val VOTER_ID_NAME_LABEL      = """(?i)(elector'?s?\s+name|electoral\s+name|name)\s*:\s*"""
        private const val VOTER_ID_NAME_STOP       =
            """(?i)(father'?[cs]?\s+(name|lame)|mother'?s?\s+name|husband'?s?\s+name|relation'?s?\s+name|s/o|w/o|d/o|h/o|male|female|dob|date\s+of\s+birth|age\s+as|address)"""

        // Driving licence skip keywords
        private val DL_SKIP_KEYWORDS = listOf(
            "driving licence", "motor vehicles", "transport", "authority",
            "licence no", "dl no", "issue", "rto", "state", "india",
            "valid", "class", "category", "blood group"
        )

        // PAN fusion skip keywords (iOS: panFusionSkipKeywords)
        private val PAN_FUSION_SKIP = listOf(
            "income tax", "income ta", "partment", "dept", "department",
            "govt", "government", "india", "permanent account", "account number", "account",
            "number", "number card", "sample", "signature", "pan card", "commissioner",
            "name", "father", "date", "birth"
        )

        // Generic other-doc skip keywords
        private val GENERIC_SKIP_KEYWORDS = listOf(
            "government", "india", "republic", "authority", "unique",
            "identification", "election", "commission", "income", "permanent",
            "account", "number", "driving", "licence", "passport",
            "transport", "motor", "department", "ministry"
        )

        // Indian surnames (iOS: indianSurnames) — top ~120 by frequency
        private val INDIAN_SURNAMES = setOf(
            "sharma", "verma", "gupta", "singh", "kumar", "patel", "shah", "mehta",
            "joshi", "rao", "reddy", "naidu", "nair", "pillai", "menon", "iyer",
            "iyengar", "agarwal", "aggarwal", "agrawal", "banerjee", "bose",
            "chatterjee", "chakraborty", "das", "dey", "ghosh", "mukherjee",
            "roy", "sen", "bhat", "kamath", "hegde", "shetty", "kulkarni",
            "desai", "patil", "jadhav", "shinde", "pawar", "more", "mali",
            "yadav", "chauhan", "thakur", "rajput", "pandey", "mishra", "tiwari",
            "dubey", "shukla", "tripathi", "dixit", "awasthi", "srivastava",
            "sinha", "prasad", "narayan", "chaudhary", "chowdhury", "biswas",
            "mandal", "mondal", "sarkar", "paul", "datta", "mitra",
            "basu", "ganguly", "haldar", "islam", "ahmed", "khan",
            "shaikh", "ansari", "qureshi", "siddiqui", "malik", "hussain",
            "ali", "naqvi", "rizvi", "zaidi", "haider", "mirza", "baig",
            "naik", "sawant", "bhosale", "gawade", "gaikwad", "surve",
            "fernandes", "dsouza", "pereira", "lobo", "sequeira", "gomes",
            "thomas", "george", "mathew", "joseph", "varghese", "philip",
            "cherian", "antony", "augustine", "xavier", "sebastian",
            "krishnamurthy", "venkataraman", "subramanian", "ramaswamy",
            "annamalai", "sundaram", "balaji", "rajan", "ramesh", "suresh",
            "mahesh", "ganesh", "rajesh", "mukesh", "dinesh", "umesh",
            "naresh", "yogesh", "hitesh", "ritesh", "jitesh", "kamlesh"
        )

        // Name blacklist (iOS: nameBlacklist)
        private val NAME_BLACKLIST = setOf(
            "india", "govt", "government", "department", "ministry", "tax",
            "income", "permanent", "account", "number", "aadhaar", "voter",
            "driving", "licence", "passport", "republic", "election",
            "commission", "authority", "transport", "motor"
        )
    }
}
