package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf("Alarm", "Calendar", "Camera", "Chrome", "Clock", "Zen").map { label ->
        LauncherApp(ComponentName("test.${label.lowercase()}", "$label.Activity"), label, null)
    }

    private fun showLauncher(events: List<ScheduleEvent> = emptyList()) {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color(0xFF152431))) {
                    LauncherScreen(
                        uiState = LauncherUiState(
                            apps = apps,
                            favoriteKeys = apps.take(5).mapTo(linkedSetOf(), LauncherApp::key),
                            isLoadingApps = false,
                            events = events,
                            scheduleStatus = ScheduleStatus.Ready,
                        ),
                        onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {},
                    )
                }
            }
        }
    }

    @Test
    fun homeShowsOneCompactEventAndOnlyPopulatedLetters() {
        val now = Instant.now()
        showLauncher(listOf(
            ScheduleEvent(1, "Movie night", now.plusSeconds(1680), now.plusSeconds(7200), false, "Cinema", null),
            ScheduleEvent(2, "Second appointment", now.plusSeconds(10800), now.plusSeconds(14400), false, null, null),
        ))
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        compose.onNodeWithText("Movie night", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Second appointment", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Cinema", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Swipe up for all apps").assertDoesNotExist()
        listOf("A", "C", "Z").forEach {
            compose.onNodeWithTag("alphabet:$it").assertIsDisplayed()
        }
        listOf("B", "D", "#").forEach {
            compose.onNodeWithTag("alphabet:$it").assertDoesNotExist()
        }
        saveScreenshot("home-sample.png")
    }

    @Test
    fun oneUnbrokenDragOpensScrubsReversesAndReturnsHome() {
        showLauncher()
        val rail = compose.onNodeWithTag("alphabet_rail")
        // Four cells: home, A, C, Z. Do not release the pointer between pages.
        rail.performTouchInput { down(Offset(centerX, height * 0.375f)) }
        compose.onNodeWithTag("section:A").assertIsDisplayed()

        rail.performTouchInput { moveTo(Offset(centerX, height * 0.625f), delayMillis = 250) }
        compose.onNodeWithTag("section:C").assertIsDisplayed()
        compose.onNodeWithTag("section:A").assertIsNotDisplayed()
        saveScreenshot("drawer-wave.png")

        rail.performTouchInput { moveTo(Offset(centerX, height * 0.875f), delayMillis = 250) }
        compose.onNodeWithTag("section:Z").assertIsDisplayed()
        compose.onNodeWithTag("section:C").assertIsNotDisplayed()

        rail.performTouchInput { moveTo(Offset(centerX, height * 0.125f), delayMillis = 250) }
        compose.onNodeWithTag("home_clock").assertIsDisplayed()

        rail.performTouchInput {
            moveTo(Offset(centerX, height * 0.625f), delayMillis = 250)
            up()
        }
        compose.onNodeWithTag("section:C").assertIsDisplayed()
        listOf("All apps", "Search apps", "Back to home").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        saveScreenshot("drawer-sample.png")
    }

    @Test
    fun emptyAgendaDoesNotLeaveACardOrPermissionPrompt() {
        showLauncher()
        compose.onNodeWithTag("schedule_line").assertDoesNotExist()
        compose.onNodeWithText("Connect calendar").assertDoesNotExist()
        compose.onNodeWithText("Up next").assertDoesNotExist()
        compose.onNodeWithText("You’re all clear").assertDoesNotExist()
        compose.onNodeWithTag("home_date").assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir("ui-verification")!!
        directory.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
