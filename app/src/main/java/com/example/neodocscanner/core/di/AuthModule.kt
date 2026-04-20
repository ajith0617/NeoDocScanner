package com.example.neodocscanner.core.di

import com.example.neodocscanner.feature.auth.data.repository.AuthRepositoryImpl
import com.example.neodocscanner.feature.auth.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module — binds [AuthRepository] interface to [AuthRepositoryImpl].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}
