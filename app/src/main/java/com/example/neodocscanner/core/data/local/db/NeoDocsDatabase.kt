package com.example.neodocscanner.core.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.neodocscanner.core.data.local.db.dao.ApplicationInstanceDao
import com.example.neodocscanner.core.data.local.db.dao.ApplicationSectionDao
import com.example.neodocscanner.core.data.local.db.dao.DocumentDao
import com.example.neodocscanner.core.data.local.db.entity.ApplicationInstanceEntity
import com.example.neodocscanner.core.data.local.db.entity.ApplicationSectionEntity
import com.example.neodocscanner.core.data.local.db.entity.DocumentEntity

/**
 * Room database — single source of truth for all persisted app data.
 *
 * iOS equivalent: SwiftData ModelContainer created in NeoDocsApp.init().
 *
 * Migration strategy: additive columns use default values so no migration is needed
 * for the first few schema versions. When a destructive migration is required,
 * increment [version] and add a Migration object in DatabaseModule.
 *
 * exportSchema = false: schema JSON export is disabled for simplicity.
 * Enable and add schema directory to your VCS when you add automated migration tests.
 */
@Database(
    entities = [
        DocumentEntity::class,
        ApplicationInstanceEntity::class,
        ApplicationSectionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NeoDocsDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun applicationInstanceDao(): ApplicationInstanceDao
    abstract fun applicationSectionDao(): ApplicationSectionDao
}
