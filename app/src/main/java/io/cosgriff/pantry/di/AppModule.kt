package io.cosgriff.pantry.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** "Today" for ViewModels; tests give a fixed clock. */
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
