package com.galaxyrio.gracelauncher.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.weather.WeatherCondition

/** Small, code-drawn glyphs stay crisp at both home and forecast sizes. */
@Composable
fun WeatherIcon(
    condition: WeatherCondition,
    isDaylight: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Canvas(modifier.size(24.dp).then(if (description != null) Modifier.semantics {
        contentDescription = description
    } else Modifier)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            when (condition) {
                WeatherCondition.Clear -> celestialBody(isDaylight, Offset(12f, 12f), 8f)
                WeatherCondition.PartlyCloudy -> {
                    celestialBody(isDaylight, Offset(15.5f, 8f), 5.5f)
                    cloud(top = 7f)
                }
                WeatherCondition.Cloudy -> cloud(top = 5f)
                WeatherCondition.Rain, WeatherCondition.Sleet, WeatherCondition.Snow,
                WeatherCondition.Thunderstorm, WeatherCondition.Hail -> {
                    cloud(top = 2f)
                    when (condition) {
                        WeatherCondition.Thunderstorm -> drawPath(Path().apply {
                            moveTo(12f, 13f); lineTo(8f, 19f); lineTo(12f, 19f)
                            lineTo(10.5f, 24f); lineTo(17f, 16f); lineTo(13f, 16f); close()
                        }, Color(0xFFF6CB44))
                        WeatherCondition.Snow -> snowflake(Offset(12f, 19f))
                        WeatherCondition.Hail -> listOf(8f, 13f, 18f).forEach { x ->
                            drawCircle(Color(0xFF96CAE8), 1.5f, Offset(x, 19f))
                        }
                        else -> {
                            listOf(8f, 13f, 18f).forEach { x ->
                                drawLine(Color(0xFF6DAEF1), Offset(x, 17f), Offset(x - 1.5f, 20.5f), 1.8f, StrokeCap.Round)
                            }
                            if (condition == WeatherCondition.Sleet) snowflake(Offset(17.5f, 21f), 1.5f)
                        }
                    }
                }
                WeatherCondition.Fog -> {
                    cloud(top = 2f)
                    listOf(17f, 21f).forEach { y ->
                        drawLine(Color(0xFFA8B3C1), Offset(4f, y), Offset(20f, y), 1.8f, StrokeCap.Round)
                    }
                }
                WeatherCondition.Wind -> {
                    val wind = Color(0xFF9CBBCF)
                    drawLine(wind, Offset(3f, 8f), Offset(16f, 8f), 2f, StrokeCap.Round)
                    drawArc(wind, -90f, 240f, false, Offset(14f, 3f), Size(5f, 5f), style = Stroke(2f, cap = StrokeCap.Round))
                    drawLine(wind, Offset(5f, 13f), Offset(20f, 13f), 2f, StrokeCap.Round)
                    drawLine(wind, Offset(3f, 18f), Offset(14f, 18f), 2f, StrokeCap.Round)
                }
                WeatherCondition.Unknown -> {
                    drawCircle(Color(0xFFA8B3C1), 7.5f, Offset(12f, 12f), style = Stroke(1.6f))
                    drawLine(Color(0xFFA8B3C1), Offset(9f, 12f), Offset(15f, 12f), 1.8f, StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.celestialBody(daylight: Boolean, center: Offset, radius: Float) {
    if (daylight) {
        drawCircle(Color(0xFFFFD34E), radius, center)
    } else {
        val moon = Path().apply {
            moveTo(center.x + radius * 0.3f, center.y - radius)
            cubicTo(center.x - radius * 1.45f, center.y - radius * 0.7f,
                center.x - radius * 1.05f, center.y + radius * 1.45f,
                center.x + radius * 0.55f, center.y + radius * 0.8f)
            cubicTo(center.x - radius * 0.2f, center.y + radius * 0.55f,
                center.x - radius * 0.65f, center.y - radius * 0.25f,
                center.x + radius * 0.3f, center.y - radius)
            close()
        }
        drawPath(moon, Color(0xFFD7E1F7))
    }
}

private fun DrawScope.cloud(top: Float) {
    val shape = Path().apply {
        moveTo(6f, top + 12f)
        cubicTo(0f, top + 12f, 0f, top + 4.5f, 6.5f, top + 4.5f)
        cubicTo(8f, top - 1.5f, 16f, top - 1.5f, 18f, top + 4.5f)
        cubicTo(24f, top + 4.5f, 24f, top + 12f, 18f, top + 12f)
        close()
    }
    drawPath(shape, Color(0xFFD0D9E4))
    drawPath(shape, Color(0xFF8B9AAC).copy(alpha = 0.4f), style = Stroke(0.6f))
}

private fun DrawScope.snowflake(center: Offset, radius: Float = 3f) {
    val color = Color(0xFF8DC6E9)
    drawLine(color, center - Offset(radius, 0f), center + Offset(radius, 0f), 1.5f, StrokeCap.Round)
    drawLine(color, center - Offset(radius * 0.5f, radius * 0.85f), center + Offset(radius * 0.5f, radius * 0.85f), 1.5f, StrokeCap.Round)
    drawLine(color, center - Offset(radius * 0.5f, -radius * 0.85f), center + Offset(radius * 0.5f, -radius * 0.85f), 1.5f, StrokeCap.Round)
}
