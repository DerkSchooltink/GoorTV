package dev.goor.tv.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dev.goor.tv.data.db.AppDatabase
import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.preferences.UserPreferencesRepository
import dev.goor.tv.dlna.DlnaService
import dev.goor.tv.network.SourceSyncService
import dev.goor.tv.ui.screens.home.HomeViewModel
import dev.goor.tv.ui.screens.player.PlayerViewModel
import dev.goor.tv.ui.screens.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "goortv.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .build()
    }
    single { get<AppDatabase>().sourceDao() }
    single { get<AppDatabase>().channelDao() }
    single { SourceSyncService(get(), get()) }
    single { DlnaService(androidContext()) }
    single { SearchHistoryRepository(androidContext()) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            produceFile = { androidContext().preferencesDataStoreFile("user_prefs") }
        )
    }
    single { UserPreferencesRepository(get()) }

    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { params -> PlayerViewModel(params.get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
