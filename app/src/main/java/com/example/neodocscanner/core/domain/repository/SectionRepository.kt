package com.example.neodocscanner.core.domain.repository

import com.example.neodocscanner.core.domain.model.ApplicationSection
import kotlinx.coroutines.flow.Flow

/**
 * Contract for application section (checklist row) persistence operations.
 *
 * iOS equivalent: ModelContext calls in DocuVaultViewModel+Sections.swift.
 */
interface SectionRepository {

    fun observeByInstance(instanceId: String): Flow<List<ApplicationSection>>

    suspend fun getByInstanceOnce(instanceId: String): List<ApplicationSection>

    suspend fun countByInstance(instanceId: String): Int

    suspend fun insert(section: ApplicationSection)

    suspend fun insertAll(sections: List<ApplicationSection>)

    suspend fun update(section: ApplicationSection)

    suspend fun delete(section: ApplicationSection)

    suspend fun deleteAllByInstance(instanceId: String)

    suspend fun updateCollapsed(id: String, collapsed: Boolean)
}
