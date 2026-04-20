package com.example.neodocscanner.core.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.model.DocumentType
import com.example.neodocscanner.core.domain.model.ProcessingStatus
import java.util.UUID

/**
 * Room entity that maps to the `documents` table.
 *
 * iOS equivalent: Document.swift @Model class persisted by SwiftData.
 *
 * Design notes:
 * - Indexed on applicationInstanceId for fast per-vault queries (replaces SwiftData predicate)
 * - Indexed on sectionId for the inbox/section routing queries
 * - Nullable Int fields (pageIndex, groupPageIndex) stored as nullable INTEGER in SQLite
 */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["application_instance_id"]),
        Index(value = ["section_id"]),
        Index(value = ["group_id"])
    ]
)
data class DocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "file_name")       val fileName: String = "",
    @ColumnInfo(name = "relative_path")   val relativePath: String = "",
    @ColumnInfo(name = "file_size")       val fileSize: Long = 0L,
    @ColumnInfo(name = "date_added")      val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sort_order")      val sortOrder: Int = 0,
    @ColumnInfo(name = "category_name")   val categoryName: String = "All Documents",
    @ColumnInfo(name = "type_raw_value")  val typeRawValue: String = DocumentType.IMAGE.rawValue,

    // OCR
    @ColumnInfo(name = "extracted_text")  val extractedText: String? = null,
    @ColumnInfo(name = "is_ocr_processed") val isOcrProcessed: Boolean = false,
    @ColumnInfo(name = "extracted_regions") val extractedRegions: String? = null,

    // ML classification
    @ColumnInfo(name = "document_class")       val documentClassRaw: String? = null,
    @ColumnInfo(name = "extracted_fields")     val extractedFields: String? = null,
    @ColumnInfo(name = "is_manually_classified") val isManuallyClassified: Boolean = false,

    // Grouping
    @ColumnInfo(name = "group_id")        val groupId: String? = null,
    @ColumnInfo(name = "page_index")      val pageIndex: Int? = null,
    @ColumnInfo(name = "group_name")      val groupName: String? = null,
    @ColumnInfo(name = "group_page_index") val groupPageIndex: Int? = null,

    // Aadhaar
    @ColumnInfo(name = "aadhaar_side")     val aadhaarSide: String? = null,
    @ColumnInfo(name = "aadhaar_uid_hash") val aadhaarUidHash: String? = null,
    @ColumnInfo(name = "scan_session_id")  val scanSessionId: String? = null,

    // Passport
    @ColumnInfo(name = "passport_side")   val passportSide: String? = null,

    // Section routing
    @ColumnInfo(name = "section_id")      val sectionId: String? = null,

    // Masking
    @ColumnInfo(name = "masked_relative_path") val maskedRelativePath: String? = null,

    // PDF export/merge
    @ColumnInfo(name = "is_archived_by_export")   val isArchivedByExport: Boolean = false,
    @ColumnInfo(name = "exported_from_group_id")  val exportedFromGroupId: String? = null,

    // Pipeline
    @ColumnInfo(name = "processing_status_raw")   val processingStatusRaw: String = ProcessingStatus.COMPLETE.rawValue,
    @ColumnInfo(name = "thumbnail_relative_path") val thumbnailRelativePath: String? = null,

    // Vault scoping
    @ColumnInfo(name = "application_instance_id") val applicationInstanceId: String? = null
)

// ── Mappers ────────────────────────────────────────────────────────────────

fun DocumentEntity.toDomain(): Document = Document(
    id = id,
    fileName = fileName,
    relativePath = relativePath,
    fileSize = fileSize,
    dateAdded = dateAdded,
    sortOrder = sortOrder,
    categoryName = categoryName,
    typeRawValue = typeRawValue,
    extractedText = extractedText,
    isOcrProcessed = isOcrProcessed,
    extractedRegions = extractedRegions,
    documentClassRaw = documentClassRaw,
    extractedFields = extractedFields,
    isManuallyClassified = isManuallyClassified,
    groupId = groupId,
    pageIndex = pageIndex,
    groupName = groupName,
    groupPageIndex = groupPageIndex,
    aadhaarSide = aadhaarSide,
    aadhaarUidHash = aadhaarUidHash,
    scanSessionId = scanSessionId,
    passportSide = passportSide,
    sectionId = sectionId,
    maskedRelativePath = maskedRelativePath,
    isArchivedByExport = isArchivedByExport,
    exportedFromGroupId = exportedFromGroupId,
    processingStatusRaw = processingStatusRaw,
    thumbnailRelativePath = thumbnailRelativePath,
    applicationInstanceId = applicationInstanceId
)

fun Document.toEntity(): DocumentEntity = DocumentEntity(
    id = id,
    fileName = fileName,
    relativePath = relativePath,
    fileSize = fileSize,
    dateAdded = dateAdded,
    sortOrder = sortOrder,
    categoryName = categoryName,
    typeRawValue = typeRawValue,
    extractedText = extractedText,
    isOcrProcessed = isOcrProcessed,
    extractedRegions = extractedRegions,
    documentClassRaw = documentClassRaw,
    extractedFields = extractedFields,
    isManuallyClassified = isManuallyClassified,
    groupId = groupId,
    pageIndex = pageIndex,
    groupName = groupName,
    groupPageIndex = groupPageIndex,
    aadhaarSide = aadhaarSide,
    aadhaarUidHash = aadhaarUidHash,
    scanSessionId = scanSessionId,
    passportSide = passportSide,
    sectionId = sectionId,
    maskedRelativePath = maskedRelativePath,
    isArchivedByExport = isArchivedByExport,
    exportedFromGroupId = exportedFromGroupId,
    processingStatusRaw = processingStatusRaw,
    thumbnailRelativePath = thumbnailRelativePath,
    applicationInstanceId = applicationInstanceId
)
