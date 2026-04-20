package com.example.neodocscanner.core.di

import android.content.Context
import androidx.room.Room
import com.example.neodocscanner.core.data.local.db.NeoDocsDatabase
import com.example.neodocscanner.core.data.local.db.dao.ApplicationInstanceDao
import com.example.neodocscanner.core.data.local.db.dao.ApplicationSectionDao
import com.example.neodocscanner.core.data.local.db.dao.DocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides Room database and DAO instances.
 *
 * iOS equivalent: The ModelContainer created in NeoDocsApp.init()
 * and passed as an environment value through the SwiftUI hierarchy.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NeoDocsDatabase =
        Room.databaseBuilder(
            context,
            NeoDocsDatabase::class.java,
            "neodocs_database"
        )
            // Allow destructive migration during early development.
            // Replace with proper Migration objects before production release.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideDocumentDao(db: NeoDocsDatabase): DocumentDao =
        db.documentDao()

    @Provides
    @Singleton
    fun provideApplicationInstanceDao(db: NeoDocsDatabase): ApplicationInstanceDao =
        db.applicationInstanceDao()

    @Provides
    @Singleton
    fun provideApplicationSectionDao(db: NeoDocsDatabase): ApplicationSectionDao =
        db.applicationSectionDao()
}
