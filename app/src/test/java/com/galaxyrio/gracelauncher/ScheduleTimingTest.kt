package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.CountdownUnit
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.eventCountdown
import com.galaxyrio.gracelauncher.data.nextVisibleEvent
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTimingTest {
    private val now = Instant.parse("2026-09-22T12:00:00Z")
    private fun event(start: Instant, end: Instant, allDay: Boolean = false) = ScheduleEvent(
        id = start.epochSecond, title = "Movie night", startsAt = start, endsAt = end,
        isAllDay = allDay, location = null, calendarColor = null,
    )

    @Test
    fun expiredEventDoesNotHideTheNextAppointment() {
        val expired = event(now.minusSeconds(3600), now)
        val later = event(now.plusSeconds(7200), now.plusSeconds(10800))
        val next = event(now.plusSeconds(1680), now.plusSeconds(5280))
        assertEquals(next, nextVisibleEvent(listOf(expired, later, next), now))
        assertEquals(28, eventCountdown(next, now).value)
    }

    @Test
    fun countdownNeverShowsZeroMinutesBeforeAnEvent() {
        assertEquals(CountdownUnit.LessThanMinute,
            eventCountdown(event(now.plusSeconds(10), now.plusSeconds(3610)), now).unit)
        assertEquals(CountdownUnit.Ongoing,
            eventCountdown(event(now.minusSeconds(10), now.plusSeconds(3590)), now).unit)
    }

    @Test
    fun allDayDatesRespectTheUsersTimezone() {
        val allDay = event(
            Instant.parse("2026-09-23T00:00:00Z"),
            Instant.parse("2026-09-24T00:00:00Z"),
            allDay = true,
        )
        val shanghai = ZoneId.of("Asia/Shanghai")
        assertEquals(4, eventCountdown(allDay, now, shanghai).value)
        assertEquals(CountdownUnit.Hours, eventCountdown(allDay, now, shanghai).unit)
        assertEquals(CountdownUnit.AllDay,
            eventCountdown(allDay, Instant.parse("2026-09-22T16:00:00Z"), shanghai).unit)
        assertEquals(null,
            nextVisibleEvent(listOf(allDay), Instant.parse("2026-09-23T16:00:00Z"), shanghai))
    }
}
