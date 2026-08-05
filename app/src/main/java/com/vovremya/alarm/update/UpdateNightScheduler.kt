package com.vovremya.alarm.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import java.io.File
import java.time.Instant
import java.time.ZoneId

class UpdateNightScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun schedule(
        version: String,
        apk: File,
        isDowngrade: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        require(apk.isFile) { "APK not found" }
        val triggerAt = nextInstallTime(nowMillis)
        preferences.edit {
            putString(KEY_VERSION, version)
            putString(KEY_APK_PATH, apk.absolutePath)
            putBoolean(KEY_DOWNGRADE, isDowngrade)
            putLong(KEY_TRIGGER_AT, triggerAt)
        }
        scheduleAlarm(triggerAt, version, apk.absolutePath, isDowngrade)
        return triggerAt
    }

    fun reschedule() {
        val version = preferences.getString(KEY_VERSION, null) ?: return
        val path = preferences.getString(KEY_APK_PATH, null) ?: return
        val apk = File(path)
        if (!apk.isFile) {
            clear()
            return
        }
        val storedTrigger = preferences.getLong(KEY_TRIGGER_AT, 0L)
        val triggerAt = storedTrigger.takeIf { it > System.currentTimeMillis() }
            ?: (System.currentTimeMillis() + MISSED_UPDATE_DELAY_MILLIS)
        scheduleAlarm(triggerAt, version, path, preferences.getBoolean(KEY_DOWNGRADE, false))
    }

    fun cancel() {
        pendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { pending ->
            alarmManager.cancel(pending)
            pending.cancel()
        }
        clear()
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private fun scheduleAlarm(triggerAt: Long, version: String, path: String, isDowngrade: Boolean) {
        val pending = pendingIntent(
            flags = PendingIntent.FLAG_UPDATE_CURRENT,
            version = version,
            path = path,
            isDowngrade = isDowngrade,
        ) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return
            } catch (_: SecurityException) {
                // Fall back to an energy-efficient inexact alarm below.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    private fun pendingIntent(
        flags: Int,
        version: String = "",
        path: String = "",
        isDowngrade: Boolean = false,
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, UpdateActionReceiver::class.java).apply {
            action = UpdateActionReceiver.ACTION_SHOW_NIGHT_UPDATE
            if (version.isNotBlank()) putExtra(UpdateActionReceiver.EXTRA_VERSION, version)
            if (path.isNotBlank()) putExtra(UpdateActionReceiver.EXTRA_APK_PATH, path)
            putExtra(UpdateActionReceiver.EXTRA_IS_DOWNGRADE, isDowngrade)
        },
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val PREFERENCES = "night_update"
        private const val KEY_VERSION = "version"
        private const val KEY_APK_PATH = "apk_path"
        private const val KEY_DOWNGRADE = "downgrade"
        private const val KEY_TRIGGER_AT = "trigger_at"
        private const val REQUEST_CODE = 2601
        private const val INSTALL_HOUR = 3
        private const val MISSED_UPDATE_DELAY_MILLIS = 60_000L

        internal fun nextInstallTime(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
            var next = now.toLocalDate().atTime(INSTALL_HOUR, 0).atZone(zoneId)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return next.toInstant().toEpochMilli()
        }
    }
}
