package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

const val AgendaWidgetKey = "com.galaxyrio.gracelauncher/agenda-widget"
const val AgendaWidgetDraftKey = "com.galaxyrio.gracelauncher/agenda-widget-draft"

enum class AgendaTitle { Time, Quote, Text }
enum class AgendaHeaderField { Date, Countdown, Weather }
enum class AgendaDayWeatherField { Icon, HighLow, Description }
enum class AgendaHourWeatherField { Time, Icon, Temperature, Description }

data class AgendaAppearance(
    val title: AgendaTitle = AgendaTitle.Text,
    val titleText: String = "Your Agenda",
    val headerOrder: List<AgendaHeaderField> = AgendaHeaderField.entries,
    val headerSelected: Set<AgendaHeaderField> = AgendaHeaderField.entries.toSet(),
    val headerRightAligned: Set<AgendaHeaderField> = setOf(AgendaHeaderField.Weather),
    val dayWeatherOrder: List<AgendaDayWeatherField> = AgendaDayWeatherField.entries,
    val dayWeatherSelected: Set<AgendaDayWeatherField> = setOf(AgendaDayWeatherField.Icon, AgendaDayWeatherField.HighLow),
    val calendarContent: ClockAgenda = defaultCalendarContent(),
    val taskContent: ClockTasks = defaultTaskContent(),
    val tasksFirst: Boolean = true,
    val showHourlyWeather: Boolean = true,
    val hourWeatherOrder: List<AgendaHourWeatherField> = AgendaHourWeatherField.entries,
    val hourWeatherSelected: Set<AgendaHourWeatherField> = setOf(
        AgendaHourWeatherField.Time, AgendaHourWeatherField.Icon, AgendaHourWeatherField.Temperature,
    ),
    val showUndatedTasks: Boolean = true,
    val undatedTaskContent: ClockTasks = defaultTaskContent(),
    val showNewEvent: Boolean = true,
    val showNewTask: Boolean = true,
) {
    fun normalized() = copy(
        titleText = titleText.take(500),
        headerOrder = (headerOrder + AgendaHeaderField.entries).distinct(),
        dayWeatherOrder = (dayWeatherOrder + AgendaDayWeatherField.entries).distinct(),
        hourWeatherOrder = (hourWeatherOrder + AgendaHourWeatherField.entries).distinct(),
        calendarContent = calendarContent.normalized(),
        taskContent = taskContent.normalized(),
        undatedTaskContent = undatedTaskContent.normalized(),
    )

    fun encode(): String = normalized().let { value -> JSONObject().apply {
        put("title", value.title.name)
        put("titleText", value.titleText)
        put("headerOrder", JSONArray(value.headerOrder.map { it.name }))
        put("headerSelected", JSONArray(value.headerSelected.map { it.name }))
        put("headerRightAligned", JSONArray(value.headerRightAligned.map { it.name }))
        put("dayWeatherOrder", JSONArray(value.dayWeatherOrder.map { it.name }))
        put("dayWeatherSelected", JSONArray(value.dayWeatherSelected.map { it.name }))
        put("calendarContent", value.calendarContent.json())
        put("taskContent", value.taskContent.json())
        put("tasksFirst", value.tasksFirst)
        put("showHourlyWeather", value.showHourlyWeather)
        put("hourWeatherOrder", JSONArray(value.hourWeatherOrder.map { it.name }))
        put("hourWeatherSelected", JSONArray(value.hourWeatherSelected.map { it.name }))
        put("showUndatedTasks", value.showUndatedTasks)
        put("undatedTaskContent", value.undatedTaskContent.json())
        put("showNewEvent", value.showNewEvent)
        put("showNewTask", value.showNewTask)
    }.toString() }

    companion object {
        fun defaultCalendarContent() = ClockAgenda(
            selected = setOf(ClockAgendaField.Title, ClockAgendaField.Time, ClockAgendaField.Countdown),
            order = listOf(ClockAgendaField.Title, ClockAgendaField.Time, ClockAgendaField.Countdown) +
                ClockAgendaField.entries.filterNot { it in setOf(ClockAgendaField.Title, ClockAgendaField.Time, ClockAgendaField.Countdown) },
            typography = ClockRowTypography(size = 14, fontShadow = 0),
        )

        fun defaultTaskContent() = ClockTasks(
            selected = setOf(ClockTaskField.Title, ClockTaskField.DueTime, ClockTaskField.Countdown, ClockTaskField.List),
            order = listOf(ClockTaskField.Title, ClockTaskField.DueTime, ClockTaskField.Countdown, ClockTaskField.List) +
                ClockTaskField.entries.filterNot { it in setOf(ClockTaskField.Title, ClockTaskField.DueTime, ClockTaskField.Countdown, ClockTaskField.List) },
            typography = ClockRowTypography(size = 14, fontShadow = 0),
        )

        fun decode(value: String?): AgendaAppearance = runCatching {
            val data = JSONObject(value ?: "{}")
            val defaults = AgendaAppearance()
            fun <T : Enum<T>> fields(key: String, values: List<T>): List<T>? = data.optJSONArray(key)?.let { array ->
                (0 until array.length()).mapNotNull { index -> values.firstOrNull { it.name == array.optString(index) } }.distinct()
            }
            AgendaAppearance(
                title = AgendaTitle.entries.firstOrNull { it.name == data.optString("title") } ?: defaults.title,
                titleText = data.optString("titleText", defaults.titleText),
                headerOrder = fields("headerOrder", AgendaHeaderField.entries) ?: defaults.headerOrder,
                headerSelected = fields("headerSelected", AgendaHeaderField.entries)?.toSet() ?: defaults.headerSelected,
                headerRightAligned = fields("headerRightAligned", AgendaHeaderField.entries)?.toSet() ?: defaults.headerRightAligned,
                dayWeatherOrder = fields("dayWeatherOrder", AgendaDayWeatherField.entries) ?: defaults.dayWeatherOrder,
                dayWeatherSelected = fields("dayWeatherSelected", AgendaDayWeatherField.entries)?.toSet() ?: defaults.dayWeatherSelected,
                calendarContent = data.optJSONObject("calendarContent")?.let { ClockAgenda.decode(it) } ?: defaults.calendarContent,
                taskContent = data.optJSONObject("taskContent")?.let { ClockTasks.decode(it) } ?: defaults.taskContent,
                tasksFirst = data.optBoolean("tasksFirst", defaults.tasksFirst),
                showHourlyWeather = data.optBoolean("showHourlyWeather", defaults.showHourlyWeather),
                hourWeatherOrder = fields("hourWeatherOrder", AgendaHourWeatherField.entries) ?: defaults.hourWeatherOrder,
                hourWeatherSelected = fields("hourWeatherSelected", AgendaHourWeatherField.entries)?.toSet() ?: defaults.hourWeatherSelected,
                showUndatedTasks = data.optBoolean("showUndatedTasks", defaults.showUndatedTasks),
                undatedTaskContent = data.optJSONObject("undatedTaskContent")?.let { ClockTasks.decode(it) } ?: defaults.undatedTaskContent,
                showNewEvent = data.optBoolean("showNewEvent", defaults.showNewEvent),
                showNewTask = data.optBoolean("showNewTask", defaults.showNewTask),
            ).normalized()
        }.getOrDefault(AgendaAppearance())
    }
}
