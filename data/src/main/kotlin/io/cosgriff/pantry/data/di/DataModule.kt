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
import io.cosgriff.pantry.data.OpenFoodFacts
import io.cosgriff.pantry.data.ProductLookup
import io.cosgriff.pantry.data.UrlConnectionGet
import io.cosgriff.pantry.data.db.ItemDao
import io.cosgriff.pantry.data.db.KnownProductDao
import io.cosgriff.pantry.data.db.LocationDao
import io.cosgriff.pantry.data.db.PantryDatabase
import io.cosgriff.pantry.data.db.ShoppingDao
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

    @Provides
    fun knownProductDao(db: PantryDatabase): KnownProductDao = db.knownProducts()

    @Provides
    fun shoppingDao(db: PantryDatabase): ShoppingDao = db.shopping()

    /**
     * Open Food Facts lookup (#9, `docs/adr/0001`). The `User-Agent` names the app, its version and the repo, as
     * Open Food Facts asks of apps; no personal contact.
     */
    @Provides @Singleton
    fun productLookup(@ApplicationContext context: Context): ProductLookup {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        return OpenFoodFacts(UrlConnectionGet(), userAgent = "Pantry/$version (https://github.com/mcosgriff/pantry)")
    }

    @Provides @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
