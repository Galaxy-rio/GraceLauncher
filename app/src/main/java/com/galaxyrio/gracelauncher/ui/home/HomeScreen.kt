package com.galaxyrio.gracelauncher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.nextVisibleEvent
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.weather.WeatherCurrent
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.FolderRow
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.components.eventRemainingText
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LauncherFontFamily
import com.galaxyrio.gracelauncher.ui.theme.rememberBatteryPercent
import com.galaxyrio.gracelauncher.ui.weather.HomeWeather
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    uiState: LauncherUiState,
    topSpace: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppDetails: (LauncherApp) -> Unit,
    onAppShortcuts: (LauncherApp, Rect) -> Unit,
    onDateClick: () -> Unit,
    onClockClick: () -> Unit,
    modifier: Modifier = Modifier,
    rowGestures: AppRowGestures = AppRowGestures(),
    highlightedAppKey: String? = null,
    onOpenFolder: (LauncherFolder, Rect) -> Unit = { _, _ -> },
    onEditFolder: (LauncherFolder) -> Unit = {},
    onFolderDrag: (LauncherFolder, Rect, Boolean) -> Unit = { _, _, _ -> },
    onFolderDragEnd: (Boolean) -> Unit = {},
    onMediaCommand: (String, MediaCommand) -> Unit = { _, _ -> },
    onDismissMedia: (String, Long) -> Boolean = { _, _ -> false },
) {
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val event = if (uiState.settings.calendarAgenda) nextVisibleEvent(uiState.events, now) else null
    val favorites = uiState.favoriteApps
    val folders = uiState.folders.filter { it.placement == FolderPlacement.Favorites }
    val media = uiState.homeMedia
    val listState = rememberLazyListState()
    // Larger text, notifications and artwork alter the real height. Never disable
    // scrolling based on estimated row heights and strand the last favorite.
    val canScroll by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_content"),
        // The row's 8dp inset keeps icons aligned at 44dp while giving its
        // rounded touch surface breathing room around the icon.
        contentPadding = PaddingValues(start = LauncherLayout.Start, end = LauncherLayout.End, top = topSpace, bottom = 72.dp),
        userScrollEnabled = canScroll,
    ) {
        item(key = "date", contentType = "date") {
            DateHeader(
                now = now,
                event = event,
                onDateClick = onDateClick,
                onClockClick = onClockClick,
                showBattery = uiState.settings.showBatteryPercentage,
                weather = uiState.weather.snapshot?.current.takeIf {
                    uiState.settings.weatherEnabled && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
                },
            )
            Spacer(Modifier.height(12.dp))
        }
        if (media != null) item(key = "media", contentType = "media") {
            HomeMediaPlayer(media, onMediaCommand, onDismiss = onDismissMedia)
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
                notification = uiState.notifications[app.packageName]?.firstOrNull(),
            )
        }
        items(folders, key = { "folder:${it.id}" }, contentType = { "folder" }) { folder ->
            FolderRow(
                folder = folder,
                onOpen = { onOpenFolder(folder, it) },
                onLongClick = { onEditFolder(folder) },
                onDrag = { bounds, expanded -> onFolderDrag(folder, bounds, expanded) },
                onDragEnd = onFolderDragEnd,
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
    showBattery: Boolean,
    weather: WeatherCurrent?,
) {
    val appearance = LocalLauncherAppearance.current
    val battery = if (showBattery) rememberBatteryPercent() else null
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val clockText = formatHomeClock(now, DateFormat.is24HourFormat(context), locale)
    val datePattern = DateFormat.getBestDateTimePattern(locale, "MMMEd")
    val dateText = SimpleDateFormat(datePattern, locale).format(Date.from(now))
    val dateDescription = stringResource(if (weather != null) R.string.weather_agenda_action else R.string.date_calendar_action, dateText)
    val clockDescription = stringResource(R.string.clock_action, clockText)

    Column {
        Text(
            text = clockText.replace(':', ' '),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_clock")
                .semantics { contentDescription = clockDescription }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClockClick)
                .padding(horizontal = LauncherLayout.ContentInset),
            color = appearance.text,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 72.sp, stepSize = 1.sp),
            style = TextStyle(
                fontFamily = LauncherFontFamily,
                fontSize = 72.sp,
                lineHeight = 1.14.em,
                // Body text is 400; request 800. Josefin Sans's closest native
                // weight is 700, selected by the existing font-family resolver.
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-3).sp,
                fontFeatureSettings = "tnum",
                shadow = appearance.textShadow,
            ),
        )
        Spacer(Modifier.height(5.dp))
        val shape = LauncherLayout.RowShape
        // One bounded ripple and accessibility action for the entire compact header.
        // Keep its height content-driven when the optional schedule row is absent.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_date")
                .semantics { contentDescription = dateDescription }
                .clip(shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = appearance.text),
                    role = Role.Button,
                    onClick = onDateClick,
                ),
            shape = shape,
            color = Color.Transparent,
            contentColor = appearance.text,
        ) {
            val dateStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                shadow = appearance.textShadow,
            )
            Column(Modifier.padding(vertical = LauncherLayout.ContentInset)) {
                Row(Modifier.padding(horizontal = LauncherLayout.ContentInset, vertical = 2.dp)) {
                    Text(
                        text = dateText + (battery?.let { "  $it%" } ?: ""),
                        modifier = Modifier.weight(1f, fill = false).alignByBaseline().testTag("home_date_text"),
                        color = appearance.text,
                        style = dateStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (weather?.temperature != null) {
                        Spacer(Modifier.width(10.dp))
                        HomeWeather(weather, appearance.text, dateStyle, Modifier.alignByBaseline())
                    }
                }
                if (event != null) {
                    ScheduleLine(event = event, now = now)
                }
            }
        }
    }
}

@Composable
private fun ScheduleLine(event: ScheduleEvent, now: Instant) {
    val appearance = LocalLauncherAppearance.current
    val remaining = eventRemainingText(event, now)
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Normal,
        shadow = appearance.textShadow,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_line")
            .padding(horizontal = LauncherLayout.ContentInset, vertical = 2.dp),
    ) {
        Text(
            text = event.title,
            // CJK fallback and Latin fonts can have different ascents at the same size.
            modifier = Modifier.weight(1f, fill = false).alignByBaseline().testTag("schedule_title"),
            style = style,
            color = appearance.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "· $remaining",
            modifier = Modifier.alignByBaseline().testTag("schedule_remaining"),
            style = style,
            color = appearance.text.copy(alpha = 0.93f),
            maxLines = 1,
        )
    }
}
