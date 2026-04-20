package com.example.neodocscanner.core.data.repository

import com.example.neodocscanner.core.data.local.db.dao.ApplicationInstanceDao
import com.example.neodocscanner.core.data.local.db.dao.DocumentDao
import com.example.neodocscanner.core.data.local.db.entity.toDomain
import com.example.neodocscanner.core.data.local.db.entity.toEntity
import com.example.neodocscanner.core.domain.model.ApplicationInstance
import com.example.neodocscanner.core.domain.repository.ApplicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationRepositoryImpl @Inject constructor(
    private val instanceDao: ApplicationInstanceDao,
    private val documentDao: DocumentDao
) : ApplicationRepository {

    override fun observeAll(): Flow<List<ApplicationInstance>> =
        instanceDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllOnce(): List<ApplicationInstance> =
        instanceDao.getAllOnce().map { it.toDomain() }

    override suspend fun getById(id: String): ApplicationInstance? =
        instanceDao.getById(id)?.toDomain()

    override suspend fun insert(instance: ApplicationInstance) =
        instanceDao.insert(instance.toEntity())

    override suspend fun update(instance: ApplicationInstance) =
        instanceDao.update(instance.toEntity())

    override suspend fun delete(instance: ApplicationInstance) =
        instanceDao.delete(instance.toEntity())

    override suspend fun deleteById(id: String) =
        instanceDao.deleteById(id)

    override suspend fun updateName(id: String, name: String) =
        instanceDao.updateName(id, name)

    override suspend fun updateStatus(id: String, status: String) =
        instanceDao.updateStatus(id, status)

    override suspend fun documentCount(instanceId: String): Int =
        documentDao.countByInstance(instanceId)
}
