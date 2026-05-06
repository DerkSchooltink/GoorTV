package dev.goor.tv.di

import androidx.room.Room
import dev.goor.tv.data.db.AppDatabase
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
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }
    single { get<AppDatabase>().sourceDao() }
    single { get<AppDatabase>().channelDao() }
    single { SourceSyncService(get(), get()) }

    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { params -> PlayerViewModel(params.get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}
