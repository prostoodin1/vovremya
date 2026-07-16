package com.vovremya.alarm.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vovremya.alarm.VovremyaApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class DailySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VovremyaApplication).container
        val settings = container.settingsStore.settings.first()
        val calendarSyncFailed = if (container.calendarRepository.hasPermission()) {
            runCatching {
                val calendars = container.calendarRepository.getCalendars()
                val repair = container.calendarRepository.repairAndRequestCalendarSync(
                    calendars = calendars,
                    selectedCalendarIds = settings.selectedCalendarIds,
                )
                if (repair.requestedAccounts > 0) delay(12_000)
                val sync = container.alarmScheduler.syncFromCalendar()
                container.notificationHelper.showPlanningSummary(sync)
            }.isFailure
        } else {
            false
        }

        // Update checks do not depend on calendar permission or provider health.
        // Android still asks the user to confirm installing a downloaded APK.
        if (settings.automaticUpdates) {
            runCatching { container.updateManager.checkAndDownloadUpdate(force = false) }
        }
        return if (calendarSyncFailed) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "calendar-sync-at-19"
    }
}
