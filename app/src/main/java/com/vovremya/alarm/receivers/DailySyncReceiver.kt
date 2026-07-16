package com.vovremya.alarm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vovremya.alarm.VovremyaApplication
import com.vovremya.alarm.workers.DailySyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailySyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val request = OneTimeWorkRequestBuilder<DailySyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DailySyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        val pendingResult = goAsync()
        val container = (context.applicationContext as VovremyaApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = container.settingsStore.settings.first()
                container.dailySyncScheduler.scheduleNext(settings.dailySyncMinutes)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
