package com.example.neodocscanner.core.di

import com.example.neodocscanner.core.data.repository.ApplicationRepositoryImpl
import com.example.neodocscanner.core.data.repository.DocumentRepositoryImpl
import com.example.neodocscanner.core.data.repository.SectionRepositoryImpl
import com.example.neodocscanner.core.domain.repository.ApplicationRepository
import com.example.neodocscanner.core.domain.repository.DocumentRepository
import com.example.neodocscanner.core.domain.repository.SectionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds repository interfaces to their implementations.
 *
 * Using @Binds (instead of @Provides) because the impl already has
 * constructor injection — Hilt only needs to know the interface→impl mapping.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: DocumentRepositoryImpl
    ): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindApplicationRepository(
        impl: ApplicationRepositoryImpl
    ): ApplicationRepository

    @Binds
    @Singleton
    abstract fun bindSectionRepository(
        impl: SectionRepositoryImpl
    ): SectionRepository
}
