package com.example.neodocscanner.core.data.repository

import com.example.neodocscanner.core.data.local.db.dao.ApplicationSectionDao
import com.example.neodocscanner.core.data.local.db.entity.toDomain
import com.example.neodocscanner.core.data.local.db.entity.toEntity
import com.example.neodocscanner.core.domain.model.ApplicationSection
import com.example.neodocscanner.core.domain.repository.SectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SectionRepositoryImpl @Inject constructor(
    private val dao: ApplicationSectionDao
) : SectionRepository {

    override fun observeByInstance(instanceId: String): Flow<List<ApplicationSection>> =
        dao.observeByInstance(instanceId).map { list -> list.map { it.toDomain() } }

    override suspend fun getByInstanceOnce(instanceId: String): List<ApplicationSection> =
        dao.getByInstanceOnce(instanceId).map { it.toDomain() }

    override suspend fun countByInstance(instanceId: String): Int =
        dao.countByInstance(instanceId)

    override suspend fun insert(section: ApplicationSection) =
        dao.insert(section.toEntity())

    override suspend fun insertAll(sections: List<ApplicationSection>) =
        dao.insertAll(sections.map { it.toEntity() })

    override suspend fun update(section: ApplicationSection) =
        dao.update(section.toEntity())

    override suspend fun delete(section: ApplicationSection) =
        dao.delete(section.toEntity())

    override suspend fun deleteAllByInstance(instanceId: String) =
        dao.deleteAllByInstance(instanceId)

    override suspend fun updateCollapsed(id: String, collapsed: Boolean) =
        dao.updateCollapsed(id, collapsed)
}
