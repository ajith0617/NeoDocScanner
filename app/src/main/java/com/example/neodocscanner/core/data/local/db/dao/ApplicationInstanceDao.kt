package com.example.neodocscanner.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.neodocscanner.core.data.local.db.entity.ApplicationInstanceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for application instances (vaults).
 *
 * iOS equivalent: SwiftData FetchDescriptor<ApplicationInstance> queries
 * in ApplicationHubViewModel.swift.
 */
@Dao
interface ApplicationInstanceDao {

    @Query("SELECT * FROM application_instances ORDER BY date_created DESC")
    fun observeAll(): Flow<List<ApplicationInstanceEntity>>

    @Query("SELECT * FROM application_instances ORDER BY date_created DESC")
    suspend fun getAllOnce(): List<ApplicationInstanceEntity>

    @Query("SELECT * FROM application_instances WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ApplicationInstanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(instance: ApplicationInstanceEntity)

    @Update
    suspend fun update(instance: ApplicationInstanceEntity)

    @Delete
    suspend fun delete(instance: ApplicationInstanceEntity)

    @Query("DELETE FROM application_instances WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE application_instances SET custom_name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("UPDATE application_instances SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}
