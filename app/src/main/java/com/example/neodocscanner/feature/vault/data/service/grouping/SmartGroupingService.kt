package com.example.neodocscanner.feature.vault.data.service.grouping

import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.feature.vault.domain.buildAadhaarGroupName
import com.example.neodocscanner.feature.vault.domain.buildPassportGroupName
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates Phase-3 grouping: delegates to AadhaarGroupingService and
 * PassportGroupingService, then returns a flat list of [GroupingUpdate]s
 * that the pipeline applies to Room.
 *
 * iOS equivalent: The Phase-3 block in DocuVaultViewModel.runPhase3()
 * which calls AadhaarGroupingService.process() followed by
 * PassportGroupingService.process().
 */
@Singleton
class SmartGroupingService @Inject constructor(
    private val aadhaarGrouper: AadhaarGroupingService,
    private val passportGrouper: PassportGroupingService
) {

    data class GroupingUpdate(
        val documentId: String,
        val groupId: String?,
        val groupName: String?,
        val groupPageIndex: Int?,
        val aadhaarSide: String?,
        val passportSide: String?
    )

    /**
     * Analyses [documents] and returns grouping updates to apply.
     * iOS equivalent: DocuVaultViewModel.runPhase3() grouping section.
     */
    fun group(documents: List<Document>): List<GroupingUpdate> {
        val updates = mutableListOf<GroupingUpdate>()
        updates += groupAadhaar(documents)
        updates += groupPassport(documents)
        return updates
    }

    // ── Aadhaar pairing ───────────────────────────────────────────────────────

    private fun groupAadhaar(documents: List<Document>): List<GroupingUpdate> {
        val aadhaarDocs = documents.filter { it.documentClass == DocumentClass.AADHAAR }
        if (aadhaarDocs.isEmpty()) return emptyList()
        val aadhaarById = aadhaarDocs.associateBy { it.id }

        val snapshots = aadhaarDocs.map { doc ->
            AadhaarGroupingService.AadhaarDocumentSnapshot(
                id             = doc.id,
                aadhaarSide    = doc.aadhaarSide,
                aadhaarUIDHash = doc.aadhaarUidHash,
                extractedText  = doc.extractedText,
                scanSessionId  = doc.scanSessionId,
                pageIndex      = doc.pageIndex,
                groupId        = doc.groupId
            )
        }

        val (decisions, _) = aadhaarGrouper.process(snapshots)
        val updates = mutableListOf<GroupingUpdate>()

        for (decision in decisions) {
            val frontDoc = aadhaarById[decision.frontId] ?: continue
            val backDoc = aadhaarById[decision.backId] ?: continue
            val groupId = UUID.randomUUID().toString()
            val groupName = buildAadhaarGroupName(frontDoc, backDoc)
            updates += GroupingUpdate(decision.frontId, groupId, groupName, 0, "front", null)
            updates += GroupingUpdate(decision.backId,  groupId, groupName, 1, "back",  null)
        }
        return updates
    }

    // ── Passport pairing ──────────────────────────────────────────────────────

    private fun groupPassport(documents: List<Document>): List<GroupingUpdate> {
        val passportDocs = documents.filter { it.documentClass == DocumentClass.PASSPORT }
        if (passportDocs.isEmpty()) return emptyList()

        val snapshots = passportDocs.map { doc ->
            // Passport number extraction (iOS parity-friendly):
            // 1) field labels from extractor
            // 2) regex fallback from OCR text
            val passportNum = extractPassportNumber(doc)

            // Determine passportSide from rawLabel suffix or existing field
            val side = canonicalPassportSide(doc.passportSide) ?: inferPassportSide(doc)

            PassportGroupingService.PassportDocumentSnapshot(
                id             = doc.id,
                passportSide   = side,
                passportNumber = passportNum,
                scanSessionId  = doc.scanSessionId,
                pageIndex      = doc.pageIndex,
                groupId        = doc.groupId
            )
        }

        val (decisions, _) = passportGrouper.process(snapshots)
        val updates = mutableListOf<GroupingUpdate>()
        val passportById = passportDocs.associateBy { it.id }

        for (decision in decisions) {
            val groupId = UUID.randomUUID().toString()
            val dataDoc = passportById[decision.dataPageId]
            val addressDoc = passportById[decision.addressPageId]
            val groupName = buildPassportGroupName(dataDoc, addressDoc)
            updates += GroupingUpdate(decision.dataPageId,    groupId, groupName, 0, null, "data")
            updates += GroupingUpdate(decision.addressPageId, groupId, groupName, 1, null, "address")
        }
        return updates
    }

    /**
     * Infers passport page side when passportSide is not yet set.
     * iOS: passportDetectSide — looks for MRZ presence.
     * Data page has MRZ; address page has family/address keywords.
     */
    private fun inferPassportSide(doc: Document): String? {
        // 1) Strongest: structured extracted fields (mirrors old extractor behavior)
        val labels = doc.decodedFields.map { it.label.lowercase() }.toSet()
        val hasDataSideFields = labels.any {
            it.contains("passport number") ||
                it.contains("date of birth") ||
                it.contains("date of expiry") ||
                it.contains("country code") ||
                it.contains("nationality")
        }
        val hasAddressSideFields = labels.any {
            it.contains("father") ||
                it.contains("mother") ||
                it.contains("spouse") ||
                it.contains("address") ||
                it.contains("file no")
        }
        if (hasDataSideFields && !hasAddressSideFields) return "data"
        if (hasAddressSideFields && !hasDataSideFields) return "address"

        val text = doc.extractedText ?: return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // 2) MRZ markers for data page:
        // - strict MRZ regex when OCR is good
        // - fallback on "<<", used heavily in passport MRZ lines
        val mrzRegex = Regex("[A-Z]\\d{7}[0-9<]\\d[A-Z]{3}\\d{6}\\d[MF<]\\d{6}")
        val hasMrz = lines.any { line ->
            val stripped = line.replace(" ", "")
            val ratio = stripped.count { it.isLetterOrDigit() || it == '<' }.toDouble() /
                (stripped.length.coerceAtLeast(1).toDouble())
            (ratio >= 0.80 && mrzRegex.containsMatchIn(stripped)) || stripped.contains("<<")
        }
        if (hasMrz) return "data"

        // 3) Back-page keyword fallback
        val lower = text.lowercase()
        if (
            lower.contains("father") ||
            lower.contains("guardian") ||
            lower.contains("mother") ||
            lower.contains("spouse") ||
            lower.contains("file no") ||
            lower.contains("address")
        ) return "address"

        return null
    }

    private fun extractPassportNumber(doc: Document): String? {
        val fromFields = doc.decodedFields.firstOrNull { field ->
            val label = field.label.lowercase()
            label.contains("passport number") ||
                    label.contains("passport no")
        }?.value
        if (!fromFields.isNullOrBlank()) {
            val cleaned = fromFields.uppercase().replace(Regex("[^A-Z0-9]"), "")
            Regex("""[A-Z]\d{7}""").find(cleaned)?.value?.let { return it }
        }

        val text = doc.extractedText ?: return null
        // iOS parity number regex.
        val regex = Regex("""\b([A-Z])\s*[-]?\s*(\d{7})\b""")
        val lines = text.lines()
        for (line in lines) {
            val stripped = line.trim()
            val mrzRatio = stripped.count { it == '<' }.toDouble() / (stripped.length.coerceAtLeast(1).toDouble())
            if (stripped.length >= 30 && mrzRatio > 0.20) continue
            val m = regex.find(stripped.uppercase()) ?: continue
            return m.groupValues[1] + m.groupValues[2]
        }
        return null
    }

    private fun canonicalPassportSide(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "data", "front", "f", "mrz" -> "data"
            "address", "back", "b", "addr" -> "address"
            else -> null
        }
    }
}
