package com.galaxyrio.gracelauncher.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

enum class CountdownUnit { LessThanMinute, Minutes, Hours, Days, Ongoing, AllDay }

data class EventCountdown(val unit: CountdownUnit, val value: Int = 0, val minutes: Int = 0)

// Calendar Provider stores all-day boundaries as UTC dates, not local timestamps.
private fun ScheduleEvent.localBoundary(instant: Instant, zone: ZoneId): Instant =
    if (isAllDay) instant.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant() else instant

fun nextVisibleEvent(
    events: List<ScheduleEvent>,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): ScheduleEvent? = events
    .filter { it.localBoundary(it.endsAt, zone).isAfter(now) }
    .minByOrNull { it.localBoundary(it.startsAt, zone) }

fun eventCountdown(
    event: ScheduleEvent,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): EventCountdown {
    val seconds = Duration.between(now, event.localBoundary(event.startsAt, zone)).seconds
    if (seconds <= 0) {
        return EventCountdown(if (event.isAllDay) CountdownUnit.AllDay else CountdownUnit.Ongoing)
    }
    if (seconds < 60) return EventCountdown(CountdownUnit.LessThanMinute)
    val minutes = ((seconds + 59) / 60).toInt()
    return when {
        minutes < 60 -> EventCountdown(CountdownUnit.Minutes, value = minutes)
        minutes < 24 * 60 -> EventCountdown(CountdownUnit.Hours, value = minutes / 60, minutes = minutes % 60)
        else -> EventCountdown(CountdownUnit.Days, value = minutes / (24 * 60))
    }
}
