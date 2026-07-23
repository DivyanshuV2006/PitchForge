package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ApCheckupPolicyTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun epoch(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDate.of(year, month, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `due on last days of month if not yet completed`() {
        val onboarded = epoch(2026, 1, 5)
        assertFalse(ApCheckupPolicy.isDue(null, epoch(2026, 1, 15), onboarded, zone))
        assertTrue(ApCheckupPolicy.isDue(null, epoch(2026, 1, 29), onboarded, zone)) // 2 days left in Jan
        assertTrue(ApCheckupPolicy.isDue(null, epoch(2026, 1, 30), onboarded, zone))
        assertTrue(ApCheckupPolicy.isDue(null, epoch(2026, 1, 31), onboarded, zone))
    }

    @Test
    fun `not due after completing checkup in current month`() {
        val onboarded = epoch(2026, 1, 5)
        val done = epoch(2026, 1, 29)
        assertFalse(ApCheckupPolicy.isDue(done, epoch(2026, 1, 31), onboarded, zone))
    }

    @Test
    fun `due again next month end after prior month checkup`() {
        val onboarded = epoch(2026, 1, 5)
        val janCheckup = epoch(2026, 1, 30)
        assertFalse(ApCheckupPolicy.isDue(janCheckup, epoch(2026, 2, 10), onboarded, zone))
        assertTrue(ApCheckupPolicy.isDue(janCheckup, epoch(2026, 2, 26), onboarded, zone)) // Feb 2026 has 28 days
    }

    @Test
    fun `not due without onboarding`() {
        assertFalse(ApCheckupPolicy.isDue(null, epoch(2026, 1, 31), null, zone))
    }
}
