package com.vovremya.alarm.domain

import com.vovremya.alarm.data.QuickDismissSettings
import java.time.Instant
import java.time.ZoneId

object AlarmBehavior {
    fun vibrationAmplitude(intensity: Int): Int =
        (intensity.coerceIn(1, 100) * 255 / 100).coerceAtLeast(1)

    fun usesQuickDismiss(
        settings: QuickDismissSettings,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (!settings.enabled) return false
        val time = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalTime()
        return time.hour * 60 + time.minute >= settings.afterMinutes
    }
}
