@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaAppearance
import com.galaxyrio.gracelauncher.data.AgendaHourWeatherField
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import com.galaxyrio.gracelauncher.ui.weather.labelResource

@Composable
internal fun AgendaHourlyWeatherSettings(draft: AgendaAppearance, snapshot: WeatherSnapshot?, enabled: Boolean,
    haptics: Boolean, onChange: (AgendaAppearance) -> Unit, modifier: Modifier = Modifier,
) {
    val defaults = remember { AgendaAppearance() }
    BoxWithConstraints(modifier) {
        val previewHeight = minOf(320.dp, maxHeight * .4f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AgendaHourlyWeatherPreview(snapshot, draft,
                Modifier.fillMaxWidth().heightIn(max = previewHeight)
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()))
            Column(Modifier.weight(1f).padding(bottom = LocalSettingsBottomInset.current)) {
                ClockFieldOrder(
                    draft.hourWeatherOrder.filter { it != AgendaHourWeatherField.Chart || draft.hourWeatherChartFields.isNotEmpty() },
                    draft.hourWeatherSelected, enabled, haptics, "agenda_hour_weather_field",
                    label = { stringResource(it.labelResource()) },
                    onSelect = { field, checked -> onChange(draft.copy(hourWeatherSelected = if (checked)
                        draft.hourWeatherSelected + field else draft.hourWeatherSelected - field)) },
                    onReorder = { order ->
                        val complete = if (AgendaHourWeatherField.Chart in order) order else order.toMutableList().apply {
                            add(draft.hourWeatherOrder.indexOf(AgendaHourWeatherField.Chart).coerceIn(0, size), AgendaHourWeatherField.Chart)
                        }
                        onChange(draft.copy(hourWeatherOrder = complete))
                    }, modifier = Modifier.weight(1f),
                    chartShown = { if (it.supportsChart) it in draft.hourWeatherChartFields else null },
                    onChartChange = { field, shown -> onChange(draft.copy(hourWeatherChartFields =
                        if (shown) draft.hourWeatherChartFields + field else draft.hourWeatherChartFields - field)) },
                    positionIcons = mapOf(AgendaHourWeatherField.Chart to R.drawable.ms_bar_chart),
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                    beforeFields = {
                        item("chart_options") {
                            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                SettingsToggleItem(stringResource(R.string.weather_chart_always_expanded), null,
                                    draft.hourWeatherChartsExpanded, 0, 2, "agenda_hour_charts_expanded", enabled) {
                                    onChange(draft.copy(hourWeatherChartsExpanded = it))
                                }
                                ClockSlider(stringResource(R.string.weather_chart_height), draft.hourWeatherChartHeight,
                                    defaults.hourWeatherChartHeight, 60..240, 10, 1, 2, "agenda_hour_chart_height", enabled,
                                    valueLabel = stringResource(R.string.weather_chart_height_value, draft.hourWeatherChartHeight)) {
                                    onChange(draft.copy(hourWeatherChartHeight = it))
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    },
                )
                TextButton(onClick = { onChange(draft.copy(hourWeatherOrder = defaults.hourWeatherOrder,
                    hourWeatherSelected = defaults.hourWeatherSelected, hourWeatherChartFields = defaults.hourWeatherChartFields,
                    hourWeatherChartsExpanded = defaults.hourWeatherChartsExpanded, hourWeatherChartHeight = defaults.hourWeatherChartHeight)) },
                    enabled = enabled, modifier = Modifier.testTag("agenda_hour_weather_field_reset")) {
                    Text(stringResource(R.string.reset))
                }
            }
        }
    }
}
