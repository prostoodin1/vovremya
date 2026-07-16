package com.vovremya.alarm.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vovremya.alarm.VovremyaApplication
import kotlinx.coroutines.flow.first

class DailySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VovremyaApplication).container
        if (!container.calendarRepository.hasPermission()) return Result.success()
        return runCatching {
            val sync = container.alarmScheduler.syncFromCalendar()
            container.notificationHelper.showPlanningSummary(sync)
            val settings = container.settingsStore.settings.first()
            if (settings.automaticUpdates) container.updateManager.checkAndDownloadUpdate()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val WORK_NAME = "calendar-sync-at-19"
    }
}
