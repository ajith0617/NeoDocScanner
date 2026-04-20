package com.example.neodocscanner.core.data.repository

import com.example.neodocscanner.core.data.local.db.dao.DocumentDao
import com.example.neodocscanner.core.data.local.db.entity.toDomain
import com.example.neodocscanner.core.data.local.db.entity.toEntity
import com.example.neodocscanner.core.domain.model.Document
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao
) : DocumentRepository {

    override fun observeByInstance(instanceId: String): Flow<List<Document>> =
        dao.observeByInstance(instanceId).map { list -> list.map { it.toDomain() } }

    override fun observeByInstanceAll(instanceId: String): Flow<List<Document>> =
        dao.observeByInstanceAll(instanceId).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Document? =
        dao.getById(id)?.toDomain()

    override suspend fun getByInstanceOnce(instanceId: String): List<Document> =
        dao.getByInstanceOnce(instanceId).map { it.toDomain() }

    override suspend fun countByInstance(instanceId: String): Int =
        dao.countByInstance(instanceId)

    override suspend fun getUnclassifiedImages(): List<Document> =
        dao.getUnclassifiedImages().map { it.toDomain() }

    override suspend fun insert(document: Document) =
        dao.insert(document.toEntity())

    override suspend fun insertAll(documents: List<Document>) =
        dao.insertAll(documents.map { it.toEntity() })

    override suspend fun update(document: Document) =
        dao.update(document.toEntity())

    override suspend fun delete(document: Document) =
        dao.delete(document.toEntity())

    override suspend fun deleteById(id: String) =
        dao.deleteById(id)

    override suspend fun deleteAllByInstance(instanceId: String) =
        dao.deleteAllByInstance(instanceId)

    override suspend fun updateProcessingStatus(id: String, statusRaw: String) =
        dao.updateProcessingStatus(id, statusRaw)

    override suspend fun updateClassification(id: String, classRaw: String?, aadhaarSide: String?) =
        dao.updateClassification(id, classRaw, aadhaarSide)

    override suspend fun updateManualClassification(id: String, manual: Boolean) =
        dao.updateManualClassification(id, manual)

    override suspend fun updateOcr(id: String, text: String?, processed: Boolean, regionsJson: String?) =
        dao.updateOcr(id, text, processed, regionsJson)

    override suspend fun updateExtractedFields(id: String, fieldsJson: String?) =
        dao.updateExtractedFields(id, fieldsJson)

    override suspend fun updateMasking(id: String, maskedPath: String?, uidHash: String?, thumbPath: String?) =
        dao.updateMasking(id, maskedPath, uidHash, thumbPath)

    override suspend fun updateSectionId(id: String, sectionId: String?) =
        dao.updateSectionId(id, sectionId)

    override suspend fun updateGrouping(
        id: String, groupId: String?, groupName: String?,
        groupPageIndex: Int?, aadhaarSide: String?, passportSide: String?
    ) = dao.updateGrouping(id, groupId, groupName, groupPageIndex, aadhaarSide, passportSide)

    override suspend fun updateFilePaths(
        id: String, fileName: String, relativePath: String,
        maskedRelativePath: String?, thumbRelativePath: String?
    ) = dao.updateFilePaths(id, fileName, relativePath, maskedRelativePath, thumbRelativePath)

    override suspend fun updateArchiveState(id: String, archived: Boolean, exportedFromGroupId: String?) =
        dao.updateArchiveState(id, archived, exportedFromGroupId)

    override suspend fun updateSortOrder(id: String, sortOrder: Int) =
        dao.updateSortOrder(id, sortOrder)

    override suspend fun updatePageIndex(id: String, pageIndex: Int?) =
        dao.updatePageIndex(id, pageIndex)

    override suspend fun getArchivedByGroupId(groupId: String): List<Document> =
        dao.getArchivedByGroupId(groupId).map { it.toDomain() }
}
