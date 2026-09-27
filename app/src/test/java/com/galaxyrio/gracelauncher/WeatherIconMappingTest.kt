package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.weather.WeatherCondition
import com.galaxyrio.gracelauncher.ui.weather.weatherIconResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherIconMappingTest {
    @Test fun clearAndPartlyCloudyKeepDayAndNightIndependentOfTheme() {
        for (dark in listOf(false, true)) {
            for (condition in listOf(WeatherCondition.Clear, WeatherCondition.PartlyCloudy)) {
                assertNotEquals(weatherIconResource(condition, true, dark), weatherIconResource(condition, false, dark))
            }
        }
        assertEquals(R.drawable.weather_google_sunny_light, weatherIconResource(WeatherCondition.Clear, true, false))
        assertEquals(R.drawable.weather_google_clear_night_dark, weatherIconResource(WeatherCondition.Clear, false, true))
    }

    @Test fun everySupportedConditionHasOriginalLightAndDarkResources() {
        val supported = WeatherCondition.entries - setOf(WeatherCondition.Fog, WeatherCondition.Unknown)
        for (condition in supported) {
            for (daylight in listOf(false, true)) {
                val light = weatherIconResource(condition, daylight, false)
                val dark = weatherIconResource(condition, daylight, true)
                assertNotNull(light)
                assertNotNull(dark)
                assertNotEquals(light, dark)
            }
        }
    }

    @Test fun hailAndSleetUseTheCombinedSourceArtwork() {
        assertEquals(R.drawable.weather_google_sleet_hail_light, weatherIconResource(WeatherCondition.Hail, true, false))
        assertEquals(R.drawable.weather_google_sleet_hail_dark, weatherIconResource(WeatherCondition.Sleet, false, true))
    }

    @Test fun missingArtworkNeverInventsAWeatherCondition() {
        for (condition in listOf(WeatherCondition.Fog, WeatherCondition.Unknown)) {
            for (daylight in listOf(false, true)) {
                for (dark in listOf(false, true)) assertNull(weatherIconResource(condition, daylight, dark))
            }
        }
    }
}
