package com.yks2027.tracker.core.di

import com.yks2027.tracker.core.ai.AiClient
import com.yks2027.tracker.core.ai.AiProfilesRepository
import com.yks2027.tracker.core.ai.AnthropicProvider
import com.yks2027.tracker.core.ai.ExamExtractor
import com.yks2027.tracker.core.ai.OpenAiCompatProvider
import com.yks2027.tracker.core.ai.StatsContextBuilder
import com.yks2027.tracker.core.backup.BackupManager
import com.yks2027.tracker.core.database.DatabaseFactory
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.YksDatabase
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.datastore.TimerStateRepository
import com.yks2027.tracker.core.platform.PreferencesStores
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.SystemIstanbulClock
import com.yks2027.tracker.feature.aikoc.AiKocViewModel
import com.yks2027.tracker.feature.dashboard.DashboardViewModel
import com.yks2027.tracker.feature.dashboard.StreakUseCase
import com.yks2027.tracker.feature.exams.AnalyticsViewModel
import com.yks2027.tracker.feature.exams.ExamEntryViewModel
import com.yks2027.tracker.feature.exams.ExamListViewModel
import com.yks2027.tracker.feature.importexport.ExamPrefillHolder
import com.yks2027.tracker.feature.importexport.ImportHubViewModel
import com.yks2027.tracker.feature.notes.NotesViewModel
import com.yks2027.tracker.feature.planner.PastWeekDetailViewModel
import com.yks2027.tracker.feature.planner.PastWeeksViewModel
import com.yks2027.tracker.feature.planner.PlannerViewModel
import com.yks2027.tracker.feature.planner.WeekRolloverUseCase
import com.yks2027.tracker.feature.settings.AiProfilesViewModel
import com.yks2027.tracker.feature.settings.SettingsViewModel
import com.yks2027.tracker.feature.timer.FocusSessionSink
import com.yks2027.tracker.feature.timer.TimerController
import com.yks2027.tracker.feature.timer.TimerViewModel
import com.yks2027.tracker.feature.topics.TopicsViewModel
import com.yks2027.tracker.ui.AppRootViewModel
import com.yks2027.tracker.ui.MainViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * v2.0 — Koin replaces Hilt (Hilt is Android-only). One module per layer; each platform
 * adds exactly one platform module binding the contracts in core.platform plus
 * DatabaseFactory. Nothing in here is platform-specific.
 */
val sharedDataModule = module {
    single<IstanbulClock> { SystemIstanbulClock() }

    single<YksDatabase> {
        // Driver / location are the platform's call (DatabaseFactory); the hand-written
        // migration chain is shared and byte-identical to v1.x.
        get<DatabaseFactory>().builder()
            .addMigrations(
                YksDatabase.MIGRATION_1_2,
                YksDatabase.MIGRATION_2_3,
                YksDatabase.MIGRATION_3_4,
                YksDatabase.MIGRATION_4_5,
            )
            .build()
    }
    single { get<YksDatabase>().examDao() }
    single { get<YksDatabase>().planDao() }
    single { get<YksDatabase>().focusDao() }
    single { get<YksDatabase>().topicDao() }
    single { get<YksDatabase>().chatDao() }
    single { get<YksDatabase>().aiProfileDao() }
    single { get<YksDatabase>().noteDao() }

    // PRD §9.3 — two preferences files; `ai_secrets` is opened by the platform SecretStore.
    single { SettingsRepository(get<PreferencesStores>().open("settings")) }
    single { TimerStateRepository(get<PreferencesStores>().open("timer_state")) }

    singleOf(::AiProfilesRepository)
    singleOf(::AnthropicProvider)
    singleOf(::OpenAiCompatProvider)
    singleOf(::StatsContextBuilder)
    singleOf(::AiClient)
    singleOf(::ExamExtractor)
    singleOf(::BackupManager)
    singleOf(::WeekRolloverUseCase)
    singleOf(::StreakUseCase)
    singleOf(::ExamPrefillHolder)

    single<FocusSessionSink> { FocusSessionSink { session -> get<FocusDao>().insert(session) } }
    singleOf(::TimerController)
}

val sharedViewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::AppRootViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::ExamListViewModel)
    viewModelOf(::ExamEntryViewModel)
    viewModelOf(::AnalyticsViewModel)
    viewModelOf(::TopicsViewModel)
    viewModelOf(::PlannerViewModel)
    viewModelOf(::PastWeeksViewModel)
    viewModelOf(::PastWeekDetailViewModel)
    viewModelOf(::TimerViewModel)
    viewModelOf(::NotesViewModel)
    viewModelOf(::ImportHubViewModel)
    viewModelOf(::AiKocViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AiProfilesViewModel)
}

val sharedModules = listOf(sharedDataModule, sharedViewModelModule)
