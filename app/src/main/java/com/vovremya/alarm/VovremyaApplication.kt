package com.vovremya.alarm

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VovremyaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.createChannels()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val settings = container.settingsStore.settings.first()
            container.dailySyncScheduler.scheduleNext(settings.dailySyncMinutes)
        }
    }
}
