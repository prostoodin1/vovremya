package com.vovremya.alarm.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vovremya.alarm.receivers.DailySyncReceiver
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class DailySyncScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext(
        syncMinutes: Int = DEFAULT_SYNC_MINUTES,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val triggerAt = nextSyncMillis(nowMillis, syncMinutes = syncMinutes)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailySyncReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } catch (_: SecurityException) {
                scheduleInexact(triggerAt, pendingIntent)
            }
        } else {
            scheduleInexact(triggerAt, pendingIntent)
        }
    }

    private fun scheduleInexact(triggerAt: Long, pendingIntent: PendingIntent) {
        // A short batching window protects battery when exact-alarm access is not granted.
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 15 * 60_000L, pendingIntent)
    }

    companion object {
        private const val REQUEST_CODE = 19_00
        const val DEFAULT_SYNC_MINUTES = 19 * 60

        fun nextSyncMillis(
            nowMillis: Long,
            zoneId: ZoneId = ZoneId.systemDefault(),
            syncMinutes: Int = DEFAULT_SYNC_MINUTES,
        ): Long {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
            val safeMinutes = syncMinutes.coerceIn(0, 23 * 60 + 59)
            val syncTime = LocalTime.of(safeMinutes / 60, safeMinutes % 60)
            var next = now.toLocalDate().atTime(syncTime).atZone(zoneId)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return next.toInstant().toEpochMilli()
        }
    }
}
