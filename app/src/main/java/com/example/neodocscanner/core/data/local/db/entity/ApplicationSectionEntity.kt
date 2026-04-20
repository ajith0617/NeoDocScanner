package com.example.neodocscanner.core.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.neodocscanner.core.domain.model.ApplicationSection

/**
 * Room entity that maps to the `application_sections` table.
 *
 * iOS equivalent: ApplicationSection.swift @Model class.
 */
@Entity(
    tableName = "application_sections",
    indices = [Index(value = ["application_instance_id"])]
)
data class ApplicationSectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "",

    @ColumnInfo(name = "application_instance_id") val applicationInstanceId: String? = null,
    @ColumnInfo(name = "title")                   val title: String = "",
    @ColumnInfo(name = "icon_name")               val iconName: String = "folder",
    @ColumnInfo(name = "accepted_classes_json")   val acceptedClassesJson: String = "[]",
    @ColumnInfo(name = "is_required")             val isRequired: Boolean = false,
    @ColumnInfo(name = "max_documents")           val maxDocuments: Int = 0,
    @ColumnInfo(name = "display_order")           val displayOrder: Int = 0,
    @ColumnInfo(name = "is_collapsed")            val isCollapsed: Boolean = false
)

// ── Mappers ────────────────────────────────────────────────────────────────

fun ApplicationSectionEntity.toDomain(): ApplicationSection = ApplicationSection(
    id = id,
    applicationInstanceId = applicationInstanceId,
    title = title,
    iconName = iconName,
    acceptedClassesJson = acceptedClassesJson,
    isRequired = isRequired,
    maxDocuments = maxDocuments,
    displayOrder = displayOrder,
    isCollapsed = isCollapsed
)

fun ApplicationSection.toEntity(): ApplicationSectionEntity = ApplicationSectionEntity(
    id = id,
    applicationInstanceId = applicationInstanceId,
    title = title,
    iconName = iconName,
    acceptedClassesJson = acceptedClassesJson,
    isRequired = isRequired,
    maxDocuments = maxDocuments,
    displayOrder = displayOrder,
    isCollapsed = isCollapsed
)
