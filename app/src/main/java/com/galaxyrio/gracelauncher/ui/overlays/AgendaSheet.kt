package com.galaxyrio.gracelauncher.ui.overlays

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.agendaDays
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.components.eventRemainingText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

// Keep a modest indent beneath each date, with one shared column for the plus
// and calendar-color marker and another for all agenda text.
private val AgendaItemInset = 4.dp
private val AgendaLeadingWidth = 24.dp
private val AgendaContentGap = 8.dp

@Composable
fun AgendaSheet(uiState: LauncherUiState, actions: LauncherActions, onRequestCalendar: () -> Unit) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val zone = ZoneId.systemDefault()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val today = now.atZone(zone).toLocalDate()
    val groups = remember(uiState.events, today, zone) { agendaDays(uiState.events, today, zone) }
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMEd"), locale) }
    Column(Modifier.fillMaxWidth().height(panelWindowHeight() * 0.66f).padding(horizontal = 30.dp).testTag("agenda_sheet")) {
        PanelTitle(stringResource(R.string.your_agenda))
        when (uiState.scheduleStatus) {
            ScheduleStatus.PermissionRequired -> {
                Text(stringResource(R.string.calendar_permission_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestCalendar) { Text(stringResource(R.string.connect_calendar)) }
                PanelAction(LauncherSymbol.Plus, stringResource(R.string.new_event), "new_event", onClick = actions.newEvent)
            }
            ScheduleStatus.Error -> {
                Text(stringResource(R.string.calendar_error))
                TextButton(onClick = actions.refreshAgenda) { Text(stringResource(R.string.retry)) }
            }
            else -> {
                if (uiState.scheduleStatus == ScheduleStatus.Loading && uiState.events.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
                    groups.forEach { (date, events) ->
                        item(key = "day:$date") {
                            Row(Modifier.padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(date.format(dateFormat), modifier = Modifier.testTag("agenda_date:$date"), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(10.dp))
                                val difference = ChronoUnit.DAYS.between(today, date).toInt()
                                Text(
                                    if (difference == 0) stringResource(R.string.today) else stringResource(R.string.event_in_days, difference),
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (date == today) item(key = "new_event") {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("new_event")
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(role = Role.Button, onClick = actions.newEvent)
                                    .padding(horizontal = AgendaItemInset, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(AgendaLeadingWidth), contentAlignment = Alignment.Center) {
                                    LauncherIcon(LauncherSymbol.Plus, modifier = Modifier.testTag("new_event:icon"), tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(AgendaContentGap))
                                Text(stringResource(R.string.new_event), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        items(events, key = { "$date:${it.id}:${it.startsAt}" }) { event ->
                            AgendaEvent(event, now) { actions.openEvent(event) }
                        }
                        item(key = "space:$date") { Spacer(Modifier.height(20.dp)) }
                    }
                    if (uiState.events.isEmpty() && uiState.scheduleStatus == ScheduleStatus.Ready) item {
                        Text(stringResource(R.string.no_upcoming_events), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaEvent(event: ScheduleEvent, now: Instant, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val clockPattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    val timeFormat = remember(locale, clockPattern) { DateTimeFormatter.ofPattern(clockPattern, locale).withZone(ZoneId.systemDefault()) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (event.isAllDay) 48.dp else 66.dp).testTag("agenda_event:${event.id}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AgendaItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(AgendaLeadingWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.width(5.dp).height(if (event.isAllDay) 22.dp else 40.dp)
                .testTag("agenda_event_indicator:${event.id}")
                .background(event.calendarColor?.let { Color(it).copy(alpha = 1f) } ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
        }
        Spacer(Modifier.width(AgendaContentGap))
        Column(Modifier.weight(1f)) {
            Text(event.title, fontSize = 16.sp)
            if (!event.isAllDay) {
                Text("${timeFormat.format(event.startsAt)}–${timeFormat.format(event.endsAt)} · ${eventRemainingText(event, now)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
