package com.galaxyrio.gracelauncher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
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
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.nextVisibleEvent
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.eventRemainingText
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.rememberBatteryPercent
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    uiState: LauncherUiState,
    topSpace: Dp,
    viewportHeight: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppDetails: (LauncherApp) -> Unit,
    onAppShortcuts: (LauncherApp, Rect) -> Unit,
    onDateClick: () -> Unit,
    onClockClick: () -> Unit,
    modifier: Modifier = Modifier,
    rowGestures: AppRowGestures = AppRowGestures(),
    highlightedAppKey: String? = null,
) {
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val event = nextVisibleEvent(uiState.events, now)
    val favorites = uiState.favoriteApps
    val configuration = LocalConfiguration.current
    val estimatedHeight = topSpace + (146f * configuration.fontScale).dp + (favorites.size * 56).dp + 72.dp
    val canScroll = estimatedHeight > viewportHeight

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_content"),
        // The row's 8dp inset keeps icons aligned at 44dp while giving its
        // rounded touch surface breathing room around the icon.
        contentPadding = PaddingValues(start = 36.dp, end = 60.dp, top = topSpace, bottom = 72.dp),
        userScrollEnabled = canScroll,
    ) {
        item(key = "date", contentType = "date") {
            DateHeader(
                now = now,
                event = event,
                onDateClick = onDateClick,
                onClockClick = onClockClick,
            )
            Spacer(Modifier.height(12.dp))
        }
        items(favorites, key = LauncherApp::key, contentType = { "app" }) { app ->
            LauncherAppRow(
                app = app,
                onClick = { onLaunchApp(app) },
                onLongClick = { onAppDetails(app) },
                onSwipeRight = { onAppShortcuts(app, it) },
                gestures = rowGestures,
                highlighted = highlightedAppKey == app.key,
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
    val appearance = LocalLauncherAppearance.current
    val battery = rememberBatteryPercent()
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
            text = clockText.replace(':', ' '),
            modifier = Modifier
                .testTag("home_clock")
                .semantics { contentDescription = clockDescription }
                .clip(RoundedCornerShape(20.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = appearance.text), onClick = onClockClick)
                .padding(horizontal = 8.dp),
            color = appearance.text,
            style = TextStyle(
                fontSize = 72.sp,
                lineHeight = 82.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-3).sp,
                fontFeatureSettings = "tnum",
                shadow = appearance.textShadow,
            ),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = dateText + (battery?.let { "  $it%" } ?: ""),
            modifier = Modifier
                .testTag("home_date")
                .semantics { contentDescription = dateDescription }
                .clip(RoundedCornerShape(12.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = appearance.text), onClick = onDateClick)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            color = appearance.text,
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                shadow = appearance.textShadow,
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
    val appearance = LocalLauncherAppearance.current
    val remaining = eventRemainingText(event, now)
    val style = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        shadow = appearance.textShadow,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_line")
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = appearance.text), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = event.title,
            modifier = Modifier.weight(1f, fill = false),
            style = style,
            color = appearance.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "· $remaining",
            style = style,
            color = appearance.text.copy(alpha = 0.93f),
            maxLines = 1,
        )
    }
}
