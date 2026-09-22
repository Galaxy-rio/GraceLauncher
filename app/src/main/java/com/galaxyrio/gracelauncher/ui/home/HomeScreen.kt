package com.galaxyrio.gracelauncher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.CountdownUnit
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.eventCountdown
import com.galaxyrio.gracelauncher.data.nextVisibleEvent
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.WallpaperTextShadow
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    uiState: LauncherUiState,
    topSpace: Dp,
    viewportHeight: Dp,
    onOpenDrawer: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onToggleFavorite: (LauncherApp) -> Unit,
    onDateClick: () -> Unit,
    onClockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOpenDrawer by rememberUpdatedState(onOpenDrawer)
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val event = nextVisibleEvent(uiState.events, now)
    val favorites = uiState.favoriteApps
    val configuration = LocalConfiguration.current
    val estimatedHeight = topSpace + (116f * configuration.fontScale).dp + (favorites.size * 60).dp
    val canScroll = estimatedHeight > viewportHeight

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_content")
            .pointerInput(canScroll) {
                if (canScroll) return@pointerInput
                var upwardDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { upwardDrag = 0f },
                    onDragEnd = {
                        if (upwardDrag > 48.dp.toPx()) currentOpenDrawer()
                    },
                    onDragCancel = { upwardDrag = 0f },
                ) { change, amount ->
                    upwardDrag = (upwardDrag - amount).coerceAtLeast(0f)
                    change.consume()
                }
            },
        contentPadding = PaddingValues(start = 28.dp, end = 68.dp, top = topSpace, bottom = 24.dp),
        userScrollEnabled = canScroll,
    ) {
        item(key = "date", contentType = "date") {
            DateHeader(
                now = now,
                event = event,
                onDateClick = onDateClick,
                onClockClick = onClockClick,
            )
            Spacer(Modifier.height(18.dp))
        }
        items(favorites, key = LauncherApp::key, contentType = { "app" }) { app ->
            LauncherAppRow(
                app = app,
                onClick = { onLaunchApp(app) },
                onLongClick = { onToggleFavorite(app) },
            )
        }
    }
}

@Composable
private fun DateHeader(
    now: Instant,
    event: ScheduleEvent?,
    onDateClick: () -> Unit,
    onClockClick: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val clockPattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    val clockText = SimpleDateFormat(clockPattern, locale).format(Date.from(now))
    val datePattern = DateFormat.getBestDateTimePattern(locale, "MMMEd")
    val dateText = SimpleDateFormat(datePattern, locale).format(Date.from(now))
    val dateDescription = stringResource(R.string.date_calendar_action, dateText)
    val clockDescription = stringResource(R.string.clock_action, clockText)

    Column {
        Text(
            text = clockText,
            modifier = Modifier
                .testTag("home_clock")
                .semantics { contentDescription = clockDescription }
                .clickable(onClick = onClockClick),
            color = Color.White,
            style = TextStyle(
                fontSize = 42.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.8).sp,
                fontFeatureSettings = "tnum",
                shadow = WallpaperTextShadow,
            ),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = dateText,
            modifier = Modifier
                .testTag("home_date")
                .semantics { contentDescription = dateDescription }
                .clickable(onClick = onDateClick)
                .padding(vertical = 2.dp),
            color = Color.White.copy(alpha = 0.95f),
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                shadow = WallpaperTextShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (event != null) {
            ScheduleLine(event = event, now = now, onClick = onDateClick)
        }
    }
}

@Composable
private fun ScheduleLine(event: ScheduleEvent, now: Instant, onClick: () -> Unit) {
    val countdown = eventCountdown(event, now)
    val remaining = when (countdown.unit) {
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
    val style = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        shadow = WallpaperTextShadow,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_line")
            .clickable(onClick = onClick)
            .padding(top = 2.dp),
    ) {
        Text(
            text = event.title,
            modifier = Modifier.weight(1f, fill = false),
            style = style,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "· $remaining",
            style = style,
            color = Color.White.copy(alpha = 0.93f),
            maxLines = 1,
        )
    }
}
