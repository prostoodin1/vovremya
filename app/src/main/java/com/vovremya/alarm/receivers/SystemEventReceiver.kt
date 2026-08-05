package com.vovremya.alarm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vovremya.alarm.VovremyaApplication
import com.vovremya.alarm.update.UpdateNightScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val container = (context.applicationContext as VovremyaApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = container.settingsStore.settings.first()
                container.dailySyncScheduler.scheduleNext(settings.dailySyncMinutes)
                UpdateNightScheduler(context).reschedule()
                if (container.calendarRepository.hasPermission()) {
                    if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                        container.alarmScheduler.rescheduleSaved()
                    } else {
                        container.alarmScheduler.syncFromCalendar()
                    }
                }
            } catch (_: Exception) {
                // A boot/time-change broadcast must not crash the app process.
                // The next foreground launch or daily check will retry safely.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
