package com.example.neodocscanner.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.neodocscanner.core.data.local.db.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for documents.
 *
 * iOS equivalent: SwiftData FetchDescriptor<Document> queries in DocuVaultViewModel.
 *
 * Flow-based queries provide reactive updates — UI recomposes when data changes,
 * mirroring SwiftData @Observable automatic UI refresh.
 */
@Dao
interface DocumentDao {

    // ── Reactive queries (Flow) ───────────────────────────────────────────────

    @Query("""
        SELECT * FROM documents
        WHERE application_instance_id = :instanceId
          AND is_archived_by_export = 0
        ORDER BY sort_order ASC, date_added DESC
    """)
    fun observeByInstance(instanceId: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents
        WHERE application_instance_id = :instanceId
        ORDER BY sort_order ASC, date_added DESC
    """)
    fun observeByInstanceAll(instanceId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY sort_order ASC, date_added DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    // ── One-shot queries (suspend) ────────────────────────────────────────────

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("""
        SELECT * FROM documents
        WHERE application_instance_id = :instanceId
        ORDER BY sort_order ASC, date_added DESC
    """)
    suspend fun getByInstanceOnce(instanceId: String): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM documents WHERE application_instance_id = :instanceId")
    suspend fun countByInstance(instanceId: String): Int

    @Query("""
        SELECT * FROM documents
        WHERE document_class IS NULL
          AND type_raw_value = 'IMG'
    """)
    suspend fun getUnclassifiedImages(): List<DocumentEntity>

    // ── Write operations ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<DocumentEntity>)

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM documents WHERE application_instance_id = :instanceId")
    suspend fun deleteAllByInstance(instanceId: String)

    // ── Targeted field updates (avoids full row write for pipeline steps) ─────

    @Query("UPDATE documents SET processing_status_raw = :status WHERE id = :id")
    suspend fun updateProcessingStatus(id: String, status: String)

    @Query("""
        UPDATE documents
        SET document_class = :cls,
            aadhaar_side   = :aadhaarSide
        WHERE id = :id
    """)
    suspend fun updateClassification(id: String, cls: String?, aadhaarSide: String?)

    @Query("UPDATE documents SET is_manually_classified = :manual WHERE id = :id")
    suspend fun updateManualClassification(id: String, manual: Boolean)

    @Query("""
        UPDATE documents
        SET extracted_text   = :text,
            is_ocr_processed = :processed,
            extracted_regions = :regions
        WHERE id = :id
    """)
    suspend fun updateOcr(id: String, text: String?, processed: Boolean, regions: String?)

    @Query("""
        UPDATE documents
        SET extracted_fields = :fields
        WHERE id = :id
    """)
    suspend fun updateExtractedFields(id: String, fields: String?)

    @Query("""
        UPDATE documents
        SET masked_relative_path  = :maskedPath,
            aadhaar_uid_hash      = :uidHash,
            thumbnail_relative_path = :thumbPath
        WHERE id = :id
    """)
    suspend fun updateMasking(id: String, maskedPath: String?, uidHash: String?, thumbPath: String?)

    @Query("""
        UPDATE documents
        SET section_id = :sectionId
        WHERE id = :id
    """)
    suspend fun updateSectionId(id: String, sectionId: String?)

    @Query("""
        UPDATE documents
        SET group_id         = :groupId,
            group_name       = :groupName,
            group_page_index = :groupPageIndex,
            aadhaar_side     = :aadhaarSide,
            passport_side    = :passportSide
        WHERE id = :id
    """)
    suspend fun updateGrouping(
        id: String,
        groupId: String?,
        groupName: String?,
        groupPageIndex: Int?,
        aadhaarSide: String?,
        passportSide: String?
    )

    @Query("""
        UPDATE documents
        SET file_name               = :fileName,
            relative_path           = :relativePath,
            masked_relative_path    = :maskedRelativePath,
            thumbnail_relative_path = :thumbRelativePath
        WHERE id = :id
    """)
    suspend fun updateFilePaths(
        id: String,
        fileName: String,
        relativePath: String,
        maskedRelativePath: String?,
        thumbRelativePath: String?
    )

    @Query("""
        UPDATE documents
        SET is_archived_by_export  = :archived,
            exported_from_group_id = :exportedFromGroupId
        WHERE id = :id
    """)
    suspend fun updateArchiveState(id: String, archived: Boolean, exportedFromGroupId: String?)

    @Query("""
        UPDATE documents
        SET sort_order = :sortOrder
        WHERE id = :id
    """)
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("""
        UPDATE documents
        SET page_index = :pageIndex
        WHERE id = :id
    """)
    suspend fun updatePageIndex(id: String, pageIndex: Int?)

    @Query("""
        SELECT * FROM documents
        WHERE group_id = :groupId
          AND is_archived_by_export = 1
    """)
    suspend fun getArchivedByGroupId(groupId: String): List<DocumentEntity>
}
