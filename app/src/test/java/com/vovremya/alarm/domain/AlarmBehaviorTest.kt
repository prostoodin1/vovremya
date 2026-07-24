package com.vovremya.alarm.domain

import com.vovremya.alarm.data.QuickDismissSettings
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmBehaviorTest {
    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun `quick dismiss starts at configured local minute`() {
        val settings = QuickDismissSettings(enabled = true, afterMinutes = 10 * 60)

        assertFalse(AlarmBehavior.usesQuickDismiss(settings, millisAt(9, 59), zone))
        assertTrue(AlarmBehavior.usesQuickDismiss(settings, millisAt(10, 0), zone))
        assertTrue(AlarmBehavior.usesQuickDismiss(settings, millisAt(23, 30), zone))
    }

    @Test
    fun `disabled quick dismiss keeps regular alarm controls`() {
        val settings = QuickDismissSettings(enabled = false, afterMinutes = 0)

        assertFalse(AlarmBehavior.usesQuickDismiss(settings, millisAt(23, 59), zone))
    }

    @Test
    fun `vibration intensity is converted to a safe Android amplitude`() {
        assertTrue(AlarmBehavior.vibrationAmplitude(-10) >= 1)
        assertTrue(AlarmBehavior.vibrationAmplitude(25) < AlarmBehavior.vibrationAmplitude(75))
        assertTrue(AlarmBehavior.vibrationAmplitude(75) < AlarmBehavior.vibrationAmplitude(100))
        assertTrue(AlarmBehavior.vibrationAmplitude(500) == 255)
    }

    private fun millisAt(hour: Int, minute: Int): Long = LocalDateTime.of(2026, 7, 24, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
