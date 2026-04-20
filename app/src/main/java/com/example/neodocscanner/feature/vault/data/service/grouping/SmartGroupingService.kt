package com.example.neodocscanner.feature.vault.data.service.grouping

import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
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
            val groupId = UUID.randomUUID().toString()
            updates += GroupingUpdate(decision.frontId, groupId, "Aadhaar", 0, "front", null)
            updates += GroupingUpdate(decision.backId,  groupId, "Aadhaar", 1, "back",  null)
        }
        return updates
    }

    // ── Passport pairing ──────────────────────────────────────────────────────

    private fun groupPassport(documents: List<Document>): List<GroupingUpdate> {
        val passportDocs = documents.filter { it.documentClass == DocumentClass.PASSPORT }
        if (passportDocs.isEmpty()) return emptyList()

        val snapshots = passportDocs.map { doc ->
            // Passport number is in the extracted fields under "Passport Number"
            val passportNum = doc.decodedFields
                .firstOrNull { it.label == "Passport Number" }?.value

            // Determine passportSide from rawLabel suffix or existing field
            val side = doc.passportSide ?: inferPassportSide(doc)

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

        for (decision in decisions) {
            val groupId = UUID.randomUUID().toString()
            updates += GroupingUpdate(decision.dataPageId,    groupId, "Passport", 0, null, "data")
            updates += GroupingUpdate(decision.addressPageId, groupId, "Passport", 1, null, "address")
        }
        return updates
    }

    /**
     * Infers passport page side when passportSide is not yet set.
     * iOS: passportDetectSide — looks for MRZ presence.
     * Data page has MRZ ("P<" or long digit lines); address page doesn't.
     */
    private fun inferPassportSide(doc: Document): String {
        val text = doc.extractedText ?: return "data"
        val hasMrz = text.lines().any { line ->
            val stripped = line.replace(" ", "")
            stripped.length == 44 && stripped.matches(Regex("[A-Z0-9<]+"))
        }
        return if (hasMrz) "data" else "address"
    }
}
