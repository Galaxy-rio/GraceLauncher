@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.galaxyrio.gracelauncher.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaHourWeatherField
import com.galaxyrio.gracelauncher.data.weather.WeatherHour
import com.galaxyrio.gracelauncher.data.weather.WeatherTemperature
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun hourlyChartValue(hour: WeatherHour, field: AgendaHourWeatherField): Double? = when (field) {
    AgendaHourWeatherField.Temperature -> hour.temperature.celsius()
    AgendaHourWeatherField.FeelsLike -> hour.feelsLike.celsius()
    AgendaHourWeatherField.Humidity -> hour.humidity?.value?.takeIf { it in 0.0..100.0 }
    AgendaHourWeatherField.WindDirection -> hour.wind?.degree?.takeIf { it in 0.0..360.0 }
    AgendaHourWeatherField.AirQuality -> hour.airQuality?.value?.takeIf { it.isFinite() && it >= 0 }
    else -> null
}

private fun WeatherTemperature?.celsius(): Double? = this?.let { temperature ->
    when (temperature.unit) {
        "c" -> temperature.value
        "f" -> (temperature.value - 32) * 5 / 9
        "k" -> temperature.value - 273.15
        else -> null
    }?.takeIf { it.isFinite() && it in -273.15..1000.0 }
}

private val HumidityColor = Color(0xFF2196F3)
// Fixed Celsius anchors: 0 blue, 10 green, 20 yellow, 30 red.
private val TemperatureColors = listOf(0xFF2196F3, 0xFF4CAF50, 0xFFFDD835, 0xFFF44336).map { Color(it) }

// Cubic quarter-waves stay smooth while their centerline follows the humidity data.
private fun Path.waveTo(start: Offset, end: Offset, amplitude: Float, wavelength: Float, originX: Float) {
    val distance = end.x - start.x
    val segments = ceil(distance / (wavelength / 4)).toInt().coerceAtLeast(1)
    val step = distance / segments
    val slope = (end.y - start.y) / distance
    val frequency = 2 * PI / wavelength
    fun phase(x: Float) = (x - originX) * frequency
    fun y(x: Float) = start.y + (x - start.x) * slope + amplitude * sin(phase(x)).toFloat()
    fun tangent(x: Float) = slope + amplitude * frequency.toFloat() * cos(phase(x)).toFloat()
    repeat(segments) { index ->
        val x0 = start.x + index * step
        val x1 = start.x + (index + 1) * step
        cubicTo(x0 + step / 3, y(x0) + tangent(x0) * step / 3,
            x1 - step / 3, y(x1) - tangent(x1) * step / 3, x1, y(x1))
    }
}

// Exact colors sampled from the supplied six-level reference image.
private val AirQualityColors = listOf(0xFF00FF00, 0xFFFFC107, 0xFFCE8500, 0xFFFF0000, 0xFFC41061, 0xFF800080).map { Color(it) }
private val PlumeAqiThresholds = listOf(0, 20, 50, 100, 150, 250)

private fun airQualityColor(value: Double): Color {
    // Both supported sharing contracts use Plume AQI, not US AQI.
    // Breezy: app/src/main/kotlin/org/breezyweather/domain/weather/index/PollutantIndex.kt
    val level = PlumeAqiThresholds.indexOfLast { value >= it }.coerceIn(0, 5)
    return AirQualityColors[level]
}

@Composable
internal fun HourlyWeatherChart(hours: List<WeatherHour>, fields: Set<AgendaHourWeatherField>, height: Dp) {
    if (hours.isEmpty() || fields.isEmpty()) return
    val values = remember(hours, fields) { fields.associateWith { field -> hours.map { hourlyChartValue(it, field) } } }
    val lines = listOf(AgendaHourWeatherField.Temperature, AgendaHourWeatherField.FeelsLike, AgendaHourWeatherField.Humidity)
        .filter { it in fields }
    val feelsLikeColor = MaterialTheme.colorScheme.tertiary
    val temperatures = listOf(AgendaHourWeatherField.Temperature, AgendaHourWeatherField.FeelsLike)
        .flatMap { values[it].orEmpty().filterNotNull() }
    val minimum = temperatures.minOrNull() ?: 0.0
    val maximum = temperatures.maxOrNull() ?: 1.0
    val padding = ((maximum - minimum) * .1).coerceAtLeast(1.0)
    val lower = minimum - padding
    val span = maximum - minimum + padding * 2
    val aqiMaximum = (values[AgendaHourWeatherField.AirQuality].orEmpty().filterNotNull().maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val wind = AgendaHourWeatherField.WindDirection in fields
    val plot = lines.isNotEmpty() || AgendaHourWeatherField.AirQuality in fields
    val density = LocalDensity.current
    val arrowSize = with(density) { 20.dp.toPx() }
    val normalizedArrow = MaterialShapes.Arrow.toPath()
    val arrow = remember(normalizedArrow, arrowSize) {
        // Transform vector geometry before drawing, rather than magnifying a unit-sized path.
        Path().apply {
            addPath(normalizedArrow)
            transform(Matrix().apply { scale(arrowSize, arrowSize) })
        }
    }
    val arrowColor = MaterialTheme.colorScheme.onSurfaceVariant
    val arrowPaint = remember(arrowColor) { Paint().apply { color = arrowColor; isAntiAlias = true } }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val description = stringResource(R.string.weather_chart) + ": " + fields.map { stringResource(it.labelResource()) }.joinToString(", ")
    Canvas(Modifier.width(HourWeatherWidth * hours.size).height(height)
        .testTag("weather_hourly_chart").semantics { contentDescription = description }) {
        val column = HourWeatherWidth.toPx()
        val top = minOf(8.dp.toPx(), size.height * .1f)
        val barWidth = 12.dp.toPx()
        val rotatedArrowExtent = arrowSize * sqrt(2f)
        val arrowCenterY = if (plot) size.height - 4.dp.toPx() - rotatedArrowExtent / 2 else size.height / 2
        // Leave 8 dp between the rounded bar ends and the arrow's full rotated bounds.
        val bottom = (if (wind) arrowCenterY - rotatedArrowExtent / 2 - 8.dp.toPx() - barWidth / 2
            else size.height - 8.dp.toPx()).coerceAtLeast(top + 1f)
        val plotHeight = bottom - top
        fun x(index: Int) = column * (index + .5f)
        fun y(value: Double, humidity: Boolean) = bottom -
            (if (humidity) value / 100 else (value - lower) / span).coerceIn(0.0, 1.0).toFloat() * plotHeight
        fun temperatureY(value: Double) = bottom - ((value - lower) / span).toFloat() * plotHeight
        // Do not clamp these coordinates: the same Celsius value must keep its color in every forecast.
        val temperatureBrush = Brush.verticalGradient(TemperatureColors.reversed(),
            startY = temperatureY(30.0), endY = temperatureY(0.0))
        val waveAmplitude = minOf(2.dp.toPx(), plotHeight * .1f)
        val waveLength = column / 4
        val singlePointHalfWidth = 10.dp.toPx()
        val lineStroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        // AQI bars sit behind all three lines, on the same hour centers.
        values[AgendaHourWeatherField.AirQuality]?.forEachIndexed { index, value ->
            if (value != null) {
                drawLine(trackColor, Offset(x(index), bottom), Offset(x(index), top), barWidth, StrokeCap.Round)
                val barHeight = ((value / aqiMaximum).toFloat() * plotHeight).coerceAtLeast(1.dp.toPx())
                drawLine(airQualityColor(value), Offset(x(index), bottom), Offset(x(index), bottom - barHeight),
                    barWidth, StrokeCap.Round)
            }
        }
        lines.forEach { field ->
            val path = Path()
            val samples = values.getValue(field)
            val humidity = field == AgendaHourWeatherField.Humidity
            samples.forEachIndexed { index, value ->
                if (value != null) {
                    val point = Offset(x(index), y(value, humidity))
                    val previous = samples.getOrNull(index - 1)
                    if (previous != null) {
                        if (humidity) path.waveTo(Offset(x(index - 1), y(previous, true)), point,
                            waveAmplitude, waveLength, column / 2)
                        else path.lineTo(point.x, point.y)
                    } else if (samples.getOrNull(index + 1) == null) {
                        // An isolated reading still has a short stroke; never bridge missing hours.
                        val start = Offset(point.x - singlePointHalfWidth, point.y)
                        val end = Offset(point.x + singlePointHalfWidth, point.y)
                        path.moveTo(start.x, start.y)
                        if (humidity) path.waveTo(start, end, waveAmplitude, waveLength, column / 2)
                        else path.lineTo(end.x, end.y)
                    } else path.moveTo(point.x, point.y)
                }
            }
            if (field == AgendaHourWeatherField.Temperature) drawPath(path, temperatureBrush, style = lineStroke)
            else drawPath(path, if (humidity) HumidityColor else feelsLikeColor, style = lineStroke)
        }
        values[AgendaHourWeatherField.WindDirection]?.forEachIndexed { index, degree ->
            if (degree != null) {
                val arrowTop = arrowCenterY - arrowSize / 2
                withTransform({
                    translate(x(index) - arrowSize / 2, arrowTop)
                    // Meteorological bearings describe where wind comes from; the arrow points where it blows.
                    rotate(degree.toFloat() + 180f, Offset(arrowSize / 2, arrowSize / 2))
                }) { drawIntoCanvas { it.drawPath(arrow, arrowPaint) } }
            }
        }
    }
}
