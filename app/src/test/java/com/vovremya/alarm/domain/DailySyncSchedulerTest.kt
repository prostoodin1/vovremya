package com.vovremya.alarm.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DailySyncSchedulerTest {
    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun nextSyncIsTodayAtNineteenBeforeCutoff() {
        val now = time(2026, 7, 14, 18, 20)
        assertEquals(time(2026, 7, 14, 19, 0), DailySyncScheduler.nextSyncMillis(now, zone))
    }

    @Test
    fun nextSyncMovesToTomorrowAfterCutoff() {
        val now = time(2026, 7, 14, 19, 0)
        assertEquals(time(2026, 7, 15, 19, 0), DailySyncScheduler.nextSyncMillis(now, zone))
    }

    @Test
    fun nextSyncUsesConfiguredTime() {
        val now = time(2026, 7, 14, 7, 0)
        assertEquals(
            time(2026, 7, 14, 7, 45),
            DailySyncScheduler.nextSyncMillis(now, zone, syncMinutes = 7 * 60 + 45),
        )
    }

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
