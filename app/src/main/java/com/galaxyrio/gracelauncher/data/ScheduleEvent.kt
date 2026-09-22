package com.galaxyrio.gracelauncher.data

import java.time.Instant

data class ScheduleEvent(
    val id: Long,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val isAllDay: Boolean,
    val location: String?,
    val calendarColor: Int?,
)
