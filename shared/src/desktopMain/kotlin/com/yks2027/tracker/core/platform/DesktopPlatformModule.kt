package com.yks2027.tracker.core.platform

import com.yks2027.tracker.core.ai.DesktopSecretStore
import com.yks2027.tracker.core.database.DatabaseFactory
import com.yks2027.tracker.core.datastore.TimerStateRepository
import com.yks2027.tracker.feature.timer.TimerController
import org.koin.dsl.module

/** The one desktop platform module (spec §2): binds every core.platform contract. */
val desktopPlatformModule = module {
    single { DesktopPaths.default() }
    single<PreferencesStores> { DesktopPreferencesStores(get()) }
    single<DatabaseFactory> { DesktopDatabaseFactory(get()) }
    single<SecretStore> { DesktopSecretStore(get<DesktopPaths>().secretsDir) }
    single<ImageDownscaler> { DesktopImageDownscaler() }
    single<PlatformFiles> { DesktopPlatformFiles(get()) }
    single { TrayNotifier() }
    single<ShareService> { DesktopShareService(get(), get()) }
    single<TimerCompletionScheduler> {
        val koin = getKoin()
        val notifier = get<TrayNotifier>()
        DesktopTimerCompletionScheduler(notifier) {
            // Same sequence as Android's TimerAlarmReceiver (break vs. study wording).
            val wasBreak = koin.get<TimerStateRepository>().snapshot().isBreak
            if (koin.get<TimerController>().finalizeCompleted()) {
                if (wasBreak) notifier.notify("Mola bitti!", "Devam etmeye hazır mısın?")
                else notifier.notify("Süre doldu!", "Odak oturumu tamamlandı.")
            }
        }
    }
}
