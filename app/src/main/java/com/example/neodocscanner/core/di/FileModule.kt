package com.example.neodocscanner.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for file-system utilities.
 *
 * FileManagerRepository is annotated with @Singleton + @Inject constructor,
 * so Hilt auto-provides it. This module reserves space for future additions
 * such as a PDF renderer provider or ML model asset manager.
 *
 * iOS equivalent: FileManagerService.swift (enum namespace with static functions).
 */
@Module
@InstallIn(SingletonComponent::class)
object FileModule
