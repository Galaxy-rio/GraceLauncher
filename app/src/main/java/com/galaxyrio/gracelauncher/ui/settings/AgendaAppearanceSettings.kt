@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.PopupWidgetControls
import com.galaxyrio.gracelauncher.ui.overlays.PopupWidgetItem
import com.galaxyrio.gracelauncher.ui.overlays.panelWindowHeight
import kotlinx.coroutines.launch

private val AgendaAppearanceSaver = Saver<AgendaAppearance, String>(save = { it.encode() }, restore = { AgendaAppearance.decode(it) })
private enum class AgendaContentPage { Weather, Calendar, Tasks, Hourly, Undated }
private class AgendaControl(val key: String, val content: @Composable (Int, Int) -> Unit)

@Composable
internal fun AgendaAppearanceSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = AgendaAppearanceSaver) { mutableStateOf(uiState.settings.agendaAppearance.normalized()) }
    var baseline by rememberSaveable(stateSaver = AgendaAppearanceSaver) { mutableStateOf(draft) }
    val savedWidget = uiState.popups[AgendaWidgetKey].orEmpty().firstNotNullOfOrNull { it.widget }?.encode()
    val newWidget = uiState.popups[AgendaWidgetDraftKey].orEmpty().firstNotNullOfOrNull { it.widget }
    var baselineWidget by rememberSaveable { mutableStateOf(savedWidget) }
    var widgetJson by rememberSaveable { mutableStateOf(savedWidget) }
    var widgetEdited by rememberSaveable { mutableStateOf(false) }
    var observedWidgetId by rememberSaveable { mutableIntStateOf(-1) }
    var editingWidget by rememberSaveable { mutableStateOf(false) }
    var resizingWidget by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf<AgendaContentPage?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(savedWidget) {
        if (!widgetEdited) { baselineWidget = savedWidget; widgetJson = savedWidget }
    }
    LaunchedEffect(newWidget?.widgetId) {
        if (newWidget != null && newWidget.widgetId != observedWidgetId) {
            observedWidgetId = newWidget.widgetId
            widgetJson = newWidget.encode()
            widgetEdited = true
            editingWidget = false
        }
    }
    val widget = remember(widgetJson) { widgetJson?.let { HomeLayout.decode(it) }?.takeIf { it.hasWidget } }
    // The settings snapshot and widget snapshot arrive independently.
    val enabled = uiState.itemRevision > 0 && !saving && LocalSettingsStorageState.current.canEdit
    val weatherEnabled = enabled && uiState.settings.weatherEnabled
    val calendarEnabled = enabled && uiState.settings.calendarAgenda
    val tasksEnabled = enabled && uiState.settings.calendar.tasksOrg.enabled
    val haptics = uiState.settings.allowHapticFeedback
    val dirty = draft != baseline || widgetJson != baselineWidget
    fun changeWidget(layout: HomeLayout) { widgetJson = layout.encode(); widgetEdited = true }
    fun saveAndExit() {
        if (!enabled) return
        val applied = draft.normalized()
        saving = true
        scope.launch {
            try {
                if (actions.applyAgendaAppearance(applied, widget)) {
                    baseline = applied; baselineWidget = widgetJson; confirmExit = false; onBack()
                } else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }
    fun discardAndExit() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                if (actions.discardAgendaWidgetDraft()) { confirmExit = false; onBack() }
                else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }
    val back: () -> Unit = {
        if (!saving) when {
            page != null -> page = null
            editingWidget -> editingWidget = false
            dirty -> confirmExit = true
            else -> discardAndExit()
        }
    }
    BackHandler(onBack = back)
    if (confirmExit) AlertDialog(
        onDismissRequest = { if (!saving) confirmExit = false },
        title = { Text(stringResource(R.string.icon_designer_unsaved_title)) },
        text = { Text(stringResource(R.string.agenda_unsaved_message)) },
        confirmButton = { TextButton(::saveAndExit, enabled = enabled) { Text(stringResource(R.string.save)) } },
        dismissButton = { Row {
            TextButton({ confirmExit = false }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
            TextButton(::discardAndExit, enabled = !saving) { Text(stringResource(R.string.icon_designer_discard)) }
        } }, modifier = Modifier.testTag("agenda_appearance_unsaved"),
    )
    SettingsPreviewScaffold(uiState, stringResource(R.string.settings_agenda), "agenda_appearance", back,
        previewEnabled = false,
        actions = { SettingsApplyAction(stringResource(R.string.save), enabled = enabled,
            modifier = Modifier.testTag("agenda_appearance_apply"), onClick = ::saveAndExit) },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .padding(horizontal = 16.dp)
        val contentPage = page
        if (contentPage != null) key(contentPage) {
            AgendaContentSettings(contentPage, draft, enabled, weatherEnabled, calendarEnabled, tasksEnabled, haptics,
                onChange = { draft = it }, onClose = { page = null }, modifier = contentModifier.padding(vertical = 16.dp))
        } else LazyColumn(contentModifier.testTag("agenda_appearance_controls"),
            userScrollEnabled = !saving && !resizingWidget,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            contentPadding = PaddingValues(bottom = 24.dp + LocalSettingsBottomInset.current),
        ) {
            item("title_heading") { SettingsHeading(stringResource(R.string.agenda_title)) }
            clockSetting("title_choices") {
                OrderedIconChoices(tag = "agenda_title", order = AgendaTitle.entries, selected = setOf(draft.title),
                    enabled = enabled, hapticsEnabled = haptics,
                    labelFor = { it.label() }, iconFor = { when (it) {
                        AgendaTitle.Time -> R.drawable.ms_schedule
                        AgendaTitle.Quote -> R.drawable.ms_format_quote
                        AgendaTitle.Text -> R.drawable.ms_text_fields
                    } }, onSelect = { choice, checked -> if (checked) draft = draft.copy(title = choice) })
            }
            if (draft.title == AgendaTitle.Text) clockSetting("title_text") {
                OutlinedTextField(draft.titleText, { draft = draft.copy(titleText = it.take(500)) }, enabled = enabled,
                    label = { Text(stringResource(R.string.agenda_title_text)) }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("agenda_title_text"))
            }
            if (draft.title == AgendaTitle.Quote) clockSetting("title_quote") {
                Text(stringResource(R.string.settings_coming_soon), Modifier.padding(16.dp))
            }
            item("widget_heading") { SettingsHeading(stringResource(R.string.agenda_widget_region)) }
            clockSetting("widget_add") {
                SettingsActionItem(stringResource(R.string.settings_add_widget), null, 0, 1, "agenda_widget_add",
                    enabled = enabled && widget == null, onClick = actions.addAgendaWidget)
            }
            if (widget != null) {
                clockSetting("widget_preview:${widget.widgetId}") {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        PopupWidgetItem(widget, editingWidget, enabled, haptics,
                            maxHeight = panelWindowHeight().value.toInt(), onChange = { layout, _ -> changeWidget(layout) },
                            onDragStart = { editingWidget = true }, onDrag = {}, onDragEnd = {},
                            onResizing = { resizingWidget = it }, horizontalInset = 0.dp)
                    }
                }
                if (editingWidget) clockSetting("widget_controls") {
                    PopupWidgetControls(widget, onChange = { layout, _ -> changeWidget(layout) }, onDone = { editingWidget = false })
                }
                clockSetting("widget_remove") {
                    SettingsActionItem(stringResource(R.string.widget_remove), null, 0, 1, "agenda_widget_remove", enabled = enabled) {
                        widgetJson = null; widgetEdited = true; editingWidget = false
                    }
                }
            }
            item("daily_heading") { SettingsHeading(stringResource(R.string.agenda_daily)) }
            clockSetting("daily_choices") {
                val order = draft.headerOrder.filterNot { it in draft.headerRightAligned } + listOf(null) +
                    draft.headerOrder.filter { it in draft.headerRightAligned }
                OrderedIconChoices(tag = "agenda_header", order = order, selected = draft.headerSelected,
                    enabled = enabled, hapticsEnabled = haptics,
                    disabledElements = if (weatherEnabled) emptySet() else setOf(AgendaHeaderField.Weather),
                    labelFor = { it.label() }, iconFor = { when (it) {
                        AgendaHeaderField.Date -> R.drawable.ms_calendar_month
                        AgendaHeaderField.Countdown -> R.drawable.ms_hourglass_empty
                        AgendaHeaderField.Weather -> R.drawable.ms_partly_cloudy_day
                    } },
                    onSelect = { field, selected -> draft = draft.copy(headerSelected = if (selected)
                        draft.headerSelected + field else draft.headerSelected - field) },
                    onReorder = { reordered -> draft = draft.copy(headerOrder = reordered.filterNotNull(),
                        headerRightAligned = reordered.drop(reordered.indexOf(null) + 1).filterNotNull().toSet()) })
            }
            agendaControls(buildList {
                if (AgendaHeaderField.Weather in draft.headerSelected) add(AgendaControl("weather_content") { index, count ->
                    SettingsActionItem(stringResource(R.string.clock_weather_contents), null, index, count, "agenda_weather_content",
                        enabled = weatherEnabled) { page = AgendaContentPage.Weather }
                })
                add(AgendaControl("task_content") { index, count ->
                    SettingsActionItem(stringResource(R.string.clock_task_contents), null, index, count, "agenda_task_content",
                        enabled = tasksEnabled) { page = AgendaContentPage.Tasks }
                })
                add(AgendaControl("calendar_content") { index, count ->
                    SettingsActionItem(stringResource(R.string.agenda_calendar_content), null, index, count, "agenda_calendar_content",
                        enabled = calendarEnabled) { page = AgendaContentPage.Calendar }
                })
                add(AgendaControl("tasks_first") { index, count ->
                    SettingsToggleItem(stringResource(R.string.agenda_tasks_first), null, draft.tasksFirst, index, count,
                        "agenda_tasks_first", enabled = tasksEnabled && calendarEnabled) { draft = draft.copy(tasksFirst = it) }
                })
            })
            item("today_heading") { SettingsHeading(stringResource(R.string.today)) }
            agendaControls(buildList {
                add(AgendaControl("hourly_weather") { index, count ->
                    SettingsToggleItem(stringResource(R.string.agenda_hourly_weather), null, draft.showHourlyWeather, index, count,
                        "agenda_hourly_weather", enabled = weatherEnabled) { draft = draft.copy(showHourlyWeather = it) }
                })
                if (draft.showHourlyWeather) add(AgendaControl("hourly_content") { index, count ->
                    SettingsActionItem(stringResource(R.string.agenda_hourly_content), null, index, count,
                        "agenda_hourly_content", enabled = weatherEnabled) { page = AgendaContentPage.Hourly }
                })
                add(AgendaControl("undated_tasks") { index, count ->
                    SettingsToggleItem(stringResource(R.string.agenda_undated_tasks), null, draft.showUndatedTasks, index, count,
                        "agenda_undated_tasks", enabled = tasksEnabled) { draft = draft.copy(showUndatedTasks = it) }
                })
                if (draft.showUndatedTasks) add(AgendaControl("undated_content") { index, count ->
                    SettingsActionItem(stringResource(R.string.agenda_undated_content), null, index, count,
                        "agenda_undated_content", enabled = tasksEnabled) { page = AgendaContentPage.Undated }
                })
                add(AgendaControl("new_event") { index, count ->
                    SettingsToggleItem(stringResource(R.string.agenda_new_event_button), null, draft.showNewEvent, index, count,
                        "agenda_new_event", enabled = calendarEnabled) { draft = draft.copy(showNewEvent = it) }
                })
                add(AgendaControl("new_task") { index, count ->
                    SettingsToggleItem(stringResource(R.string.agenda_new_task_button), null, draft.showNewTask, index, count,
                        "agenda_new_task", enabled = tasksEnabled) { draft = draft.copy(showNewTask = it) }
                })
            })
        }
    }
}

private fun LazyListScope.agendaControls(controls: List<AgendaControl>) {
    controls.forEachIndexed { index, control -> clockSetting(control.key) { control.content(index, controls.size) } }
}

@Composable
private fun AgendaContentSettings(page: AgendaContentPage, draft: AgendaAppearance, enabled: Boolean,
    weatherEnabled: Boolean, calendarEnabled: Boolean, tasksEnabled: Boolean, haptics: Boolean,
    onChange: (AgendaAppearance) -> Unit, onClose: () -> Unit, modifier: Modifier,
) {
    when (page) {
        AgendaContentPage.Weather -> ClockContentPicker(stringResource(R.string.clock_weather_contents),
            draft.dayWeatherOrder, draft.dayWeatherSelected, enabled && weatherEnabled, haptics, "agenda_day_weather_field",
            label = { stringResource(when (it) {
                AgendaDayWeatherField.Icon -> R.string.clock_weather_field_icon
                AgendaDayWeatherField.HighLow -> R.string.clock_weather_field_high_low
                AgendaDayWeatherField.Description -> R.string.clock_weather_field_description
            }) },
            onSelect = { field, checked -> onChange(draft.copy(dayWeatherSelected = if (checked)
                draft.dayWeatherSelected + field else draft.dayWeatherSelected - field)) },
            onReorder = { onChange(draft.copy(dayWeatherOrder = it)) }, onClose = onClose, modifier = modifier)
        AgendaContentPage.Hourly -> ClockContentPicker(stringResource(R.string.agenda_hourly_content),
            draft.hourWeatherOrder, draft.hourWeatherSelected, enabled && weatherEnabled, haptics, "agenda_hour_weather_field",
            label = { stringResource(when (it) {
                AgendaHourWeatherField.Time -> R.string.clock_agenda_field_time
                AgendaHourWeatherField.Icon -> R.string.clock_weather_field_icon
                AgendaHourWeatherField.Temperature -> R.string.clock_weather_field_temperature
                AgendaHourWeatherField.Description -> R.string.clock_weather_field_description
            }) },
            onSelect = { field, checked -> onChange(draft.copy(hourWeatherSelected = if (checked)
                draft.hourWeatherSelected + field else draft.hourWeatherSelected - field)) },
            onReorder = { onChange(draft.copy(hourWeatherOrder = it)) }, onClose = onClose, modifier = modifier)
        AgendaContentPage.Calendar -> {
            val content = draft.calendarContent
            fun change(value: ClockAgenda) = onChange(draft.copy(calendarContent = value))
            ClockContentPicker(stringResource(R.string.agenda_calendar_content), content.order, content.selected,
                calendarEnabled, haptics, "agenda_calendar_field", label = { it.label() },
                onSelect = { field, checked -> change(content.copy(selected = if (checked) content.selected + field else content.selected - field)) },
                onReorder = { change(content.copy(order = it)) }, onClose = onClose, modifier = modifier,
                rightAligned = content.rightAligned,
                onAlignedReorder = { order, aligned -> change(content.copy(order = order, rightAligned = aligned)) },
                onReset = { change(AgendaAppearance.defaultCalendarContent()) })
        }
        AgendaContentPage.Tasks, AgendaContentPage.Undated -> {
            val content = if (page == AgendaContentPage.Tasks) draft.taskContent else draft.undatedTaskContent
            fun change(value: ClockTasks) = onChange(if (page == AgendaContentPage.Tasks) draft.copy(taskContent = value)
                else draft.copy(undatedTaskContent = value))
            ClockContentPicker(stringResource(if (page == AgendaContentPage.Tasks) R.string.clock_task_contents else R.string.agenda_undated_content),
                content.order, content.selected, tasksEnabled, haptics, "agenda_${page.name.lowercase()}_field", label = { it.label() },
                onSelect = { field, checked -> change(content.copy(selected = if (checked) content.selected + field else content.selected - field)) },
                onReorder = { change(content.copy(order = it)) }, onClose = onClose, modifier = modifier,
                rightAligned = content.rightAligned,
                onAlignedReorder = { order, aligned -> change(content.copy(order = order, rightAligned = aligned)) },
                onReset = { change(AgendaAppearance.defaultTaskContent()) })
        }
    }
}

@Composable
private fun AgendaTitle.label(): String = stringResource(when (this) {
    AgendaTitle.Time -> R.string.agenda_title_time
    AgendaTitle.Quote -> R.string.agenda_title_quote
    AgendaTitle.Text -> R.string.agenda_title_text
})

@Composable
private fun AgendaHeaderField.label(): String = stringResource(when (this) {
    AgendaHeaderField.Date -> R.string.clock_agenda_field_date
    AgendaHeaderField.Countdown -> R.string.clock_agenda_field_countdown
    AgendaHeaderField.Weather -> R.string.settings_weather
})
