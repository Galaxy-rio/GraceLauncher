package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.data.media.MediaSnapshot
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.data.weather.WeatherCondition
import com.galaxyrio.gracelauncher.data.weather.WeatherCurrent
import com.galaxyrio.gracelauncher.data.weather.WeatherDay
import com.galaxyrio.gracelauncher.data.weather.WeatherHour
import com.galaxyrio.gracelauncher.data.weather.WeatherLocation
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import com.galaxyrio.gracelauncher.data.weather.WeatherTemperature
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneId
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in asset capture using production composables and fictional, local sample data. */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun captureEnglishStoreListing() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeScreenshots") == "true")
        check(context.resources.configuration.locales[0].language == "en") {
            "Use an English emulator to capture the store screenshots."
        }
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            )
        }

        // Existing Apache-2.0 Material Symbols serve as a small demo icon pack.
        val apps = listOf(
            "Browser" to R.drawable.ms_link,
            "Calculator" to R.drawable.ms_apps,
            "Calendar" to R.drawable.ms_category,
            "Camera" to R.drawable.ms_palette,
            "Clock" to R.drawable.ms_hourglass_empty,
            "Contacts" to R.drawable.ms_person,
            "Files" to R.drawable.ms_folder,
            "Gallery" to R.drawable.ms_palette,
            "Mail" to R.drawable.ms_link,
            "Messages" to R.drawable.ms_edit,
            "Music" to R.drawable.ms_music_note,
            "Notes" to R.drawable.ms_edit,
            "Phone" to R.drawable.ms_person,
            "Settings" to R.drawable.ms_settings,
            "Terminal" to R.drawable.ms_code,
            "Weather" to R.drawable.ms_palette,
        ).map { (label, resource) ->
            val icon = requireNotNull(ContextCompat.getDrawable(context, resource))
                .toBitmap(96, 96).asImageBitmap()
            LauncherApp(
                ComponentName("store.sample", "store.sample.$label"), label,
                icon = icon, monochromeIcon = icon, monochromeScale = 0.65f,
            )
        }
        val favorites = listOf("Phone", "Messages", "Browser", "Camera", "Music", "Notes")
            .map { label -> apps.single { it.label == label }.key }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val tomorrow = today.plusDays(1)
        fun event(id: Long, title: String, start: Instant, minutes: Long, color: Int) =
            ScheduleEvent(id, title, start, start.plusSeconds(minutes * 60), false, null, color)
        val events = listOf(
            event(1, "Design review", now.plusSeconds(30 * 60), 45, 0xFF81C7B4.toInt()),
            event(2, "Coffee with Alex", now.plusSeconds(2 * 60 * 60), 30, 0xFFB7B8F0.toInt()),
            event(3, "Morning walk", tomorrow.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant(), 45, 0xFF81C7B4.toInt()),
            event(4, "Read a chapter", tomorrow.atTime(19, 0).atZone(ZoneId.systemDefault()).toInstant(), 30, 0xFFE9BD8C.toInt()),
        )
        fun temperature(value: Int) = WeatherTemperature(value.toDouble(), "c")
        val weather = WeatherSnapshot(
            location = WeatherLocation("store-demo", "San Francisco", zone),
            current = WeatherCurrent(
                temperature(24), WeatherCondition.PartlyCloudy,
                now.atZone(zone).hour in 6..18, "Partly cloudy",
            ),
            hourly = (1..12).map { hour ->
                val at = now.plusSeconds(hour * 3600L)
                WeatherHour(
                    at, temperature(listOf(25, 26, 25, 24, 23, 22, 21, 20, 19, 18, 18, 17)[hour - 1]),
                    if (hour <= 3) WeatherCondition.Clear else WeatherCondition.PartlyCloudy,
                    at.atZone(zone).hour in 6..18, null,
                )
            },
            daily = listOf(
                WeatherCondition.PartlyCloudy, WeatherCondition.Clear, WeatherCondition.Rain,
                WeatherCondition.Cloudy, WeatherCondition.Clear,
            ).mapIndexed { day, condition ->
                WeatherDay(today.plusDays(day.toLong()), temperature(26 - day), temperature(18 - day / 2), condition, null)
            },
            updatedAt = now.minusSeconds(5 * 60),
            attribution = "Sample forecast",
        )
        var state by mutableStateOf(LauncherUiState(
            apps = apps, favoriteKeys = favorites.toSet(), favoriteOrder = favorites,
            isLoadingApps = false, isDefaultHome = true,
            textMode = WallpaperTextMode.Light,
            scheduleStatus = ScheduleStatus.Ready, events = events,
            weather = WeatherState(WeatherStatus.Ready, weather, listOf(weather.location)),
            settings = LauncherSettings(
                showBatteryPercentage = false, useDynamicColors = false,
                weatherEnabled = true, weatherForecastDays = 5,
            ),
        ))
        compose.setContent {
            GraceLauncherTheme(darkTheme = true, dynamicColor = false, seedColor = Color(0xFF82CDBB)) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Color(0xFF101D26), Color(0xFF203D42), Color(0xFF3B625B),
                )))) {
                    LauncherScreen(state, onDateClick = {}, onClockClick = {},
                        onLaunchApp = {}, onToggleFavorite = {})
                }
            }
        }

        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        compose.onNodeWithTag("home_weather", useUnmergedTree = true).assertIsDisplayed()
        save("01-home.png")

        compose.onNodeWithTag("home_date").performClick()
        compose.onNodeWithTag("agenda_sheet").assertIsDisplayed()
        compose.onNodeWithTag("weather_hourly").assertIsDisplayed()
        compose.onNodeWithTag("weather_location").assertIsDisplayed()
        compose.onNodeWithTag("agenda_event:1").assertIsDisplayed()
        compose.onNodeWithTag("agenda_event:2").assertIsDisplayed()
        save("02-agenda.png")
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()

        compose.runOnIdle {
            state = state.copy(media = MediaSnapshot(true, NowPlaying(
                sessionId = "store-demo", playerName = "Music", title = "Morning Light",
                artist = "The Quiet Hours", playing = true,
                canToggle = true, canPrevious = true, canNext = true,
            )))
        }
        compose.onNodeWithTag("home_media_player").assertIsDisplayed()
        save("03-music.png")

        val letters = AppListModel(apps).letters
        val rail = compose.onNodeWithTag("alphabet_rail")
        rail.performTouchInput {
            down(Offset(centerX, height * (letters.indexOf("C") + 1.5f) / (letters.size + 1)))
            moveBy(Offset(-35f, 0f), delayMillis = 250)
        }
        try {
            compose.onNodeWithTag("section:C").assertIsDisplayed()
            compose.onNodeWithTag("alphabet_indicator", useUnmergedTree = true).assertIsDisplayed()
            save("04-app-list-c.png")
        } finally {
            rail.performTouchInput { cancel() }
        }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        // Allow platform drawing and window transitions to settle after Compose
        // publishes its updated semantics, before capturing the displayed frame.
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        // The shell captures real dialogs and insets. Downloads survives the test
        // runner uninstalling the test APK, unlike app-scoped external storage.
        val directory = "/sdcard/Download/grace-launcher-store-screenshots"
        listOf("mkdir -p $directory", "screencap -p $directory/$name").forEach { command ->
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                val output = FileInputStream(descriptor.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
                check(output.isBlank()) { output }
            }
        }
    }
}
