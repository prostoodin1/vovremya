package com.vovremya.alarm

import android.app.Application
import android.content.Context
import com.vovremya.alarm.localization.AppLanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VovremyaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrapBaseContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.applyStoredLanguage(this)
        container = AppContainer(this)
        container.notificationHelper.createChannels()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val settings = container.settingsStore.settings.first()
            container.dailySyncScheduler.scheduleNext(settings.dailySyncMinutes)
            if (settings.automaticUpdates) {
                container.updateManager.checkAndDownloadUpdate(
                    force = false,
                    channel = settings.updateChannel,
                )
            }
        }
    }
}
