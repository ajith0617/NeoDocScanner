package com.example.neodocscanner.core.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the scan pipeline infrastructure.
 *
 * All scan services are @Singleton + @Inject constructor, so Hilt auto-provides
 * them. This module only supplies shared utility singletons that can't use
 * constructor injection directly (Gson).
 *
 * Services provided via constructor injection (no explicit @Provides needed):
 *   - DocumentFileManager
 *   - MLClassificationService
 *   - OcrService
 *   - TextExtractionService
 *   - DocumentNamingService
 *   - AadhaarMaskingService
 *   - SmartGroupingService
 *   - SectionRoutingService
 *   - ScanPipelineService
 */
@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {

    /**
     * Single Gson instance shared across the pipeline services and repositories.
     * iOS equivalent: JSONEncoder/JSONDecoder — shared in Swift as value-type
     * singletons. Using a single Gson avoids repeated allocation in the pipeline.
     */
    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
