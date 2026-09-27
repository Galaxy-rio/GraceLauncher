package com.galaxyrio.gracelauncher.ui.weather

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.weather.WeatherCondition

/** Original Google Weather set-5 artwork, converted losslessly to local vector resources. */
@Composable
fun WeatherIcon(
    condition: WeatherCondition,
    isDaylight: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    darkBackground: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
) {
    val resource = weatherIconResource(condition, isDaylight, darkBackground)
    if (resource != null) {
        // Image deliberately preserves the source palette instead of tinting its colored paths.
        Image(painterResource(resource), contentDescription = description, modifier = modifier.size(24.dp))
    } else {
        // Set-5 has no fog or unavailable artwork. Do not misrepresent either as clear/cloudy.
        Box(
            modifier.size(24.dp).clearAndSetSemantics {
                if (description != null) contentDescription = description
            },
            contentAlignment = Alignment.Center,
        ) {
            Text("—", fontSize = 18.sp, color = if (darkBackground) Color(0xFFBDC1C6) else Color(0xFF70757A))
        }
    }
}

@DrawableRes
internal fun weatherIconResource(
    condition: WeatherCondition,
    isDaylight: Boolean,
    darkBackground: Boolean,
): Int? {
    val glyph = when (condition) {
        WeatherCondition.Clear -> if (isDaylight) GoogleWeatherGlyph.Sunny else GoogleWeatherGlyph.ClearNight
        WeatherCondition.PartlyCloudy -> if (isDaylight) GoogleWeatherGlyph.PartlyCloudy else GoogleWeatherGlyph.PartlyCloudyNight
        WeatherCondition.Cloudy -> GoogleWeatherGlyph.Cloudy
        // Breezy's shared model has no precipitation intensity; choose the light-precipitation art.
        WeatherCondition.Rain -> GoogleWeatherGlyph.Drizzle
        WeatherCondition.Snow -> GoogleWeatherGlyph.Flurries
        WeatherCondition.Thunderstorm -> GoogleWeatherGlyph.Thunderstorms
        WeatherCondition.Wind -> GoogleWeatherGlyph.Windy
        WeatherCondition.Hail, WeatherCondition.Sleet -> GoogleWeatherGlyph.SleetHail
        WeatherCondition.Fog, WeatherCondition.Unknown -> return null
    }
    return if (darkBackground) glyph.dark else glyph.light
}

private enum class GoogleWeatherGlyph(@param:DrawableRes val light: Int, @param:DrawableRes val dark: Int) {
    Sunny(R.drawable.weather_google_sunny_light, R.drawable.weather_google_sunny_dark),
    ClearNight(R.drawable.weather_google_clear_night_light, R.drawable.weather_google_clear_night_dark),
    PartlyCloudy(R.drawable.weather_google_partly_cloudy_light, R.drawable.weather_google_partly_cloudy_dark),
    PartlyCloudyNight(R.drawable.weather_google_partly_cloudy_night_light, R.drawable.weather_google_partly_cloudy_night_dark),
    Cloudy(R.drawable.weather_google_cloudy_light, R.drawable.weather_google_cloudy_dark),
    Drizzle(R.drawable.weather_google_drizzle_light, R.drawable.weather_google_drizzle_dark),
    Flurries(R.drawable.weather_google_flurries_light, R.drawable.weather_google_flurries_dark),
    Thunderstorms(R.drawable.weather_google_thunderstorms_light, R.drawable.weather_google_thunderstorms_dark),
    Windy(R.drawable.weather_google_windy_light, R.drawable.weather_google_windy_dark),
    SleetHail(R.drawable.weather_google_sleet_hail_light, R.drawable.weather_google_sleet_hail_dark),
}
