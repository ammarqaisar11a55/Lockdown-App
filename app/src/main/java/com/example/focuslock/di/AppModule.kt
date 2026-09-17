package com.example.focuslock.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.focuslock.BuildConfig
import com.example.focuslock.core.common.AllowDebugging
import com.example.focuslock.core.common.ApplicationScope
import com.example.focuslock.core.common.IoDispatcher
import com.example.focuslock.core.common.OwnPackage
import com.example.focuslock.data.local.FocusDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @AllowDebugging
    fun provideAllowDebugging(): Boolean = BuildConfig.DEBUG

    @Provides
    @OwnPackage
    fun provideOwnPackage(@ApplicationContext context: Context): String = context.packageName

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FocusDatabase =
        Room.databaseBuilder(context, FocusDatabase::class.java, FocusDatabase.NAME).build()

    @Provides
    fun provideScheduleDao(database: FocusDatabase) = database.scheduleDao()

    @Provides
    fun provideAllowedApplicationDao(database: FocusDatabase) = database.allowedApplicationDao()

    @Provides
    fun provideLockdownStateDao(database: FocusDatabase) = database.lockdownStateDao()

    @Provides
    fun provideSessionHistoryDao(database: FocusDatabase) = database.sessionHistoryDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile(SETTINGS_FILE)
    }

    private const val SETTINGS_FILE = "settings"
}
