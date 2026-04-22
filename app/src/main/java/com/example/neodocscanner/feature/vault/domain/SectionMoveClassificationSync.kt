package com.example.neodocscanner.feature.vault.domain

import com.example.neodocscanner.core.domain.model.ApplicationSection
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentClass
import com.example.neodocscanner.core.domain.repository.DocumentRepository

/**
 * Keeps stored [Document.documentClassRaw] aligned with the section the user chose in
 * "Move to category", so badges and titles match the destination (e.g. Passport vs Voter ID).
 *
 * When moving to Uncategorised ([targetSectionId] null), classification is cleared to Other.
 */
suspend fun DocumentRepository.syncDocumentClassForSectionMove(
    document: Document,
    targetSectionId: String?,
    sections: List<ApplicationSection>,
) {
    val inferred: DocumentClass = when (targetSectionId) {
        null -> DocumentClass.OTHER
        else -> {
            val sec = sections.firstOrNull { it.id == targetSectionId } ?: return
            sec.inferredDocumentClassForMove(document.documentClass) ?: return
        }
    }

    val classRaw = if (inferred == DocumentClass.OTHER) null else inferred.displayName
    val newAadhaarSide = if (inferred == DocumentClass.AADHAAR) document.aadhaarSide else null
    val newPassportSide = if (inferred == DocumentClass.PASSPORT) document.passportSide else null

    updateClassification(document.id, classRaw, newAadhaarSide)
    updateManualClassification(document.id, inferred != DocumentClass.OTHER)

    val needsGroupingTouch = document.groupId != null ||
        document.passportSide != null ||
        document.aadhaarSide != null
    if (needsGroupingTouch) {
        updateGrouping(
            document.id,
            document.groupId,
            document.groupName,
            document.groupPageIndex,
            newAadhaarSide,
            newPassportSide
        )
    }
}
