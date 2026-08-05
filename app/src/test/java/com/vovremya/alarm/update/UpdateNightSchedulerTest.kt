package com.vovremya.alarm.update

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateNightSchedulerTest {
    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun `before three schedules same night`() {
        val now = time(2026, 8, 5, 1, 30)

        val scheduled = UpdateNightScheduler.nextInstallTime(now, zone)

        assertEquals(time(2026, 8, 5, 3, 0), scheduled)
    }

    @Test
    fun `after three schedules next night`() {
        val now = time(2026, 8, 5, 18, 0)

        val scheduled = UpdateNightScheduler.nextInstallTime(now, zone)

        assertEquals(time(2026, 8, 6, 3, 0), scheduled)
    }

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
