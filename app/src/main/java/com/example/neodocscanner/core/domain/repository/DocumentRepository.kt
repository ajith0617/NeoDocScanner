package com.example.neodocscanner.core.domain.repository

import com.example.neodocscanner.core.domain.model.Document
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all document persistence operations.
 *
 * iOS equivalent: the direct ModelContext calls scattered through DocuVaultViewModel.swift.
 * This interface decouples ViewModels from the Room implementation, enabling
 * easy testing and future data-source swaps.
 */
interface DocumentRepository {

    // ── Reactive queries ──────────────────────────────────────────────────────

    /** Emits the live document list for an application instance, excluding archived originals. */
    fun observeByInstance(instanceId: String): Flow<List<Document>>

    /** Emits ALL documents for an instance (including archived). */
    fun observeByInstanceAll(instanceId: String): Flow<List<Document>>

    // ── One-shot reads ────────────────────────────────────────────────────────

    suspend fun getById(id: String): Document?

    suspend fun getByInstanceOnce(instanceId: String): List<Document>

    suspend fun countByInstance(instanceId: String): Int

    /** Returns image documents that have no ML classification yet. */
    suspend fun getUnclassifiedImages(): List<Document>

    // ── Write operations ──────────────────────────────────────────────────────

    suspend fun insert(document: Document)

    suspend fun insertAll(documents: List<Document>)

    suspend fun update(document: Document)

    suspend fun delete(document: Document)

    suspend fun deleteById(id: String)

    suspend fun deleteAllByInstance(instanceId: String)

    // ── Targeted field updates ────────────────────────────────────────────────

    suspend fun updateProcessingStatus(id: String, statusRaw: String)

    suspend fun updateClassification(id: String, classRaw: String?, aadhaarSide: String?)

    suspend fun updateManualClassification(id: String, manual: Boolean)

    suspend fun updateOcr(id: String, text: String?, processed: Boolean, regionsJson: String?)

    suspend fun updateExtractedFields(id: String, fieldsJson: String?)

    suspend fun updateMasking(id: String, maskedPath: String?, uidHash: String?, thumbPath: String?)

    suspend fun updateSectionId(id: String, sectionId: String?)

    suspend fun updateGrouping(
        id: String,
        groupId: String?,
        groupName: String?,
        groupPageIndex: Int?,
        aadhaarSide: String?,
        passportSide: String?
    )

    suspend fun updateFilePaths(
        id: String,
        fileName: String,
        relativePath: String,
        maskedRelativePath: String?,
        thumbRelativePath: String?
    )

    suspend fun updateArchiveState(id: String, archived: Boolean, exportedFromGroupId: String?)

    suspend fun updateSortOrder(id: String, sortOrder: Int)

    suspend fun updatePageIndex(id: String, pageIndex: Int?)

    /** Returns archived originals belonging to a specific group (for unmerge). */
    suspend fun getArchivedByGroupId(groupId: String): List<Document>
}
