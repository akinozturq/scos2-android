package com.awork.camera6.di

import android.content.Context
import com.awork.camera6.camera.CameraController
import com.awork.camera6.util.FileManager
import com.awork.camera6.util.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager = PreferencesManager(context)

    @Provides
    @Singleton
    fun provideFileManager(
        @ApplicationContext context: Context
    ): FileManager = FileManager(context)

    @Provides
    @Singleton
    fun provideCameraController(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager,
        fileManager: FileManager
    ): CameraController = CameraController(context, preferencesManager, fileManager)
}
