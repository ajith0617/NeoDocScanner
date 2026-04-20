package com.example.neodocscanner.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.neodocscanner.core.data.local.db.entity.ApplicationSectionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for checklist sections within an application vault.
 *
 * iOS equivalent: SwiftData FetchDescriptor<ApplicationSection> queries
 * in DocuVaultViewModel+Sections.swift.
 */
@Dao
interface ApplicationSectionDao {

    @Query("""
        SELECT * FROM application_sections
        WHERE application_instance_id = :instanceId
        ORDER BY display_order ASC
    """)
    fun observeByInstance(instanceId: String): Flow<List<ApplicationSectionEntity>>

    @Query("""
        SELECT * FROM application_sections
        WHERE application_instance_id = :instanceId
        ORDER BY display_order ASC
    """)
    suspend fun getByInstanceOnce(instanceId: String): List<ApplicationSectionEntity>

    @Query("SELECT COUNT(*) FROM application_sections WHERE application_instance_id = :instanceId")
    suspend fun countByInstance(instanceId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(section: ApplicationSectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sections: List<ApplicationSectionEntity>)

    @Update
    suspend fun update(section: ApplicationSectionEntity)

    @Delete
    suspend fun delete(section: ApplicationSectionEntity)

    @Query("DELETE FROM application_sections WHERE application_instance_id = :instanceId")
    suspend fun deleteAllByInstance(instanceId: String)

    @Query("UPDATE application_sections SET is_collapsed = :collapsed WHERE id = :id")
    suspend fun updateCollapsed(id: String, collapsed: Boolean)
}
