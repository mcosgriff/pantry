package io.cosgriff.pantry.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.pantry.data.db.ItemDao
import io.cosgriff.pantry.data.db.LocationDao
import io.cosgriff.pantry.data.db.PantryDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): PantryDatabase = PantryDatabase.open(context)

    @Provides
    fun itemDao(db: PantryDatabase): ItemDao = db.items()

    @Provides
    fun locationDao(db: PantryDatabase): LocationDao = db.locations()

    @Provides @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
