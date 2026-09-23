package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.CountdownUnit
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.eventCountdown
import java.time.Instant

@Composable
fun eventRemainingText(event: ScheduleEvent, now: Instant): String {
    val countdown = eventCountdown(event, now)
    return when (countdown.unit) {
        CountdownUnit.LessThanMinute -> stringResource(R.string.event_soon)
        CountdownUnit.Minutes -> stringResource(R.string.event_in_minutes, countdown.value)
        CountdownUnit.Hours -> if (countdown.minutes == 0) {
            stringResource(R.string.event_in_hours, countdown.value)
        } else {
            stringResource(R.string.event_in_hours_minutes, countdown.value, countdown.minutes)
        }
        CountdownUnit.Days -> stringResource(R.string.event_in_days, countdown.value)
        CountdownUnit.Ongoing -> stringResource(R.string.event_ongoing)
        CountdownUnit.AllDay -> stringResource(R.string.all_day)
    }
}
