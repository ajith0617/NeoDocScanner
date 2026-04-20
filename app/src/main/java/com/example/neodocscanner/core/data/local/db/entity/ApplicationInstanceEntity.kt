package com.example.neodocscanner.core.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.neodocscanner.core.domain.model.ApplicationInstance
import java.util.UUID

/**
 * Room entity that maps to the `application_instances` table.
 *
 * iOS equivalent: ApplicationInstance.swift @Model class.
 */
@Entity(tableName = "application_instances")
data class ApplicationInstanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "template_id")   val templateId: String = "",
    @ColumnInfo(name = "template_name") val templateName: String = "",
    @ColumnInfo(name = "custom_name")   val customName: String = "",
    @ColumnInfo(name = "icon_name")     val iconName: String = "",
    @ColumnInfo(name = "date_created")  val dateCreated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "status")        val status: String = "active",

    // Server linking
    @ColumnInfo(name = "server_reference_id")    val serverReferenceId: String? = null,
    @ColumnInfo(name = "server_applicant_name")  val serverApplicantName: String? = null,
    @ColumnInfo(name = "server_branch")          val serverBranch: String? = null,
    @ColumnInfo(name = "server_metadata")        val serverMetadata: String? = null,
    @ColumnInfo(name = "linked_at")              val linkedAt: Long? = null,
    @ColumnInfo(name = "link_status")            val linkStatus: String = "unlinked"
)

// ── Mappers ────────────────────────────────────────────────────────────────

fun ApplicationInstanceEntity.toDomain(): ApplicationInstance = ApplicationInstance(
    id = id,
    templateId = templateId,
    templateName = templateName,
    customName = customName,
    iconName = iconName,
    dateCreated = dateCreated,
    status = status,
    serverReferenceId = serverReferenceId,
    serverApplicantName = serverApplicantName,
    serverBranch = serverBranch,
    serverMetadata = serverMetadata,
    linkedAt = linkedAt,
    linkStatus = linkStatus
)

fun ApplicationInstance.toEntity(): ApplicationInstanceEntity = ApplicationInstanceEntity(
    id = id,
    templateId = templateId,
    templateName = templateName,
    customName = customName,
    iconName = iconName,
    dateCreated = dateCreated,
    status = status,
    serverReferenceId = serverReferenceId,
    serverApplicantName = serverApplicantName,
    serverBranch = serverBranch,
    serverMetadata = serverMetadata,
    linkedAt = linkedAt,
    linkStatus = linkStatus
)
