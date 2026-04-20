package com.example.neodocscanner.core.domain.repository

import com.example.neodocscanner.core.domain.model.ApplicationInstance
import kotlinx.coroutines.flow.Flow

/**
 * Contract for application instance (vault) persistence operations.
 *
 * iOS equivalent: ModelContext calls in ApplicationHubViewModel.swift.
 */
interface ApplicationRepository {

    fun observeAll(): Flow<List<ApplicationInstance>>

    suspend fun getAllOnce(): List<ApplicationInstance>

    suspend fun getById(id: String): ApplicationInstance?

    suspend fun insert(instance: ApplicationInstance)

    suspend fun update(instance: ApplicationInstance)

    suspend fun delete(instance: ApplicationInstance)

    suspend fun deleteById(id: String)

    suspend fun updateName(id: String, name: String)

    suspend fun updateStatus(id: String, status: String)

    suspend fun documentCount(instanceId: String): Int
}
