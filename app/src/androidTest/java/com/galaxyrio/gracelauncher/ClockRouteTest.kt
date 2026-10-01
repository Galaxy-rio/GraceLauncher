package com.galaxyrio.gracelauncher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.provider.AlarmClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.ui.LauncherRoute
import com.galaxyrio.gracelauncher.ui.LauncherViewModel
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Test the real clock-to-picker wiring without launching another app or changing stored settings. */
@RunWith(AndroidJUnit4::class)
class ClockRouteTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[LauncherViewModel::class.java]
    private var standardLaunches = 0

    @After fun disposeViewModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    private fun showHome(hasHandler: Boolean) {
        compose.setContent {
            val context = LocalContext.current
            val clockContext = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) {
                    assertEquals(AlarmClock.ACTION_SHOW_ALARMS, intent.action)
                    standardLaunches++
                    if (!hasHandler) throw ActivityNotFoundException("Test missing clock handler")
                }
            }
            CompositionLocalProvider(LocalContext provides clockContext) {
                GraceLauncherTheme(darkTheme = false, dynamicColor = false) { LauncherRoute(viewModel) }
            }
        }
    }

    @Test fun successfulStandardIntentDoesNotOpenThePicker() {
        showHome(hasHandler = true)
        compose.onNodeWithTag("home_clock").performClick()
        compose.onNodeWithTag("clock_app_picker").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, standardLaunches) }
    }

    @Test fun missingHandlerOpensThePickerAndHomeDismissesIt() {
        showHome(hasHandler = false)
        compose.onNodeWithTag("home_clock").performClick()
        compose.onNodeWithTag("clock_app_picker").assertIsDisplayed()
        compose.waitUntil(10_000) { !viewModel.uiState.value.isLoadingApps }
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = requireNotNull(app.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        File(directory, "clock-app-picker.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        compose.runOnIdle { assertEquals(1, standardLaunches); viewModel.requestReturnHome() }
        compose.onNodeWithTag("clock_app_picker").assertDoesNotExist()
        compose.onNodeWithTag("home_clock").performClick()
        compose.onNodeWithTag("clock_app_picker").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.cancel)).performClick()
        compose.onNodeWithTag("clock_app_picker").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, standardLaunches) }
    }
}
