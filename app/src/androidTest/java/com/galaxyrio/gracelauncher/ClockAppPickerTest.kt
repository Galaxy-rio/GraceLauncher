package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.overlays.ClockAppPicker
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockAppPickerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = LauncherApp(ComponentName("test.clock", "ClockActivity"), "时钟", null)
    private val renamed = LauncherApp(ComponentName("test.alarm", "AlarmActivity"), "Morning", null,
        originalLabel = "Alarm")
    private val ownApp get() = LauncherApp(ComponentName(context.packageName, "SettingsActivity"), "Grace", null)
    private var selected: LauncherApp? = null
    private var retries = 0
    private var dismissed = false

    private fun showPicker(apps: List<LauncherApp> = listOf(clock, renamed, ownApp), failed: Boolean = false) {
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                ClockAppPicker(apps, isLoading = false, loadFailed = failed,
                    onSelect = { selected = it }, onRetry = { retries++ }, onDismiss = { dismissed = true })
            }
        }
    }

    @Test fun offersLaunchableAppsWithoutGuessingClockLabelsAndExcludesGrace() {
        showPicker()
        compose.onNodeWithTag("clock_app:${clock.key}").assertIsDisplayed()
        compose.onNodeWithTag("clock_app:${renamed.key}").assertIsDisplayed()
        compose.onNodeWithTag("clock_app:${ownApp.key}").assertDoesNotExist()
        compose.onNodeWithTag("clock_app:${clock.key}").performClick()
        compose.runOnIdle { assertEquals(clock, selected) }
    }

    @Test fun searchesOriginalNamesAndShowsAnEmptyResult() {
        showPicker()
        compose.onNodeWithTag("clock_app_query").performTextReplacement("ALARM")
        compose.onNodeWithTag("clock_app:${renamed.key}").assertExists()
        compose.onNodeWithTag("clock_app:${clock.key}").assertDoesNotExist()
        compose.onNodeWithTag("clock_app_query").performTextReplacement("no-such-app")
        compose.onNodeWithText(context.getString(R.string.search_no_results)).assertExists()
        compose.runOnIdle { assertNull(selected) }
    }

    @Test fun cancelDoesNotLaunchAnything() {
        showPicker()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(true, dismissed); assertNull(selected) }
    }

    @Test fun loadingFailureCanBeRetried() {
        showPicker(apps = emptyList(), failed = true)
        compose.onNodeWithText(context.getString(R.string.clock_apps_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun appUninstallUpdatesTheOpenPicker() {
        val apps = mutableStateOf(listOf(clock, renamed))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                ClockAppPicker(apps.value, false, false,
                    onSelect = { selected = it }, onRetry = {}, onDismiss = {})
            }
        }
        compose.onNodeWithTag("clock_app:${clock.key}").assertExists()
        compose.runOnIdle { apps.value = listOf(renamed) }
        compose.onNodeWithTag("clock_app:${clock.key}").assertDoesNotExist()
        compose.onNodeWithTag("clock_app:${renamed.key}").performClick()
        compose.runOnIdle { assertEquals(renamed, selected) }
    }
}
