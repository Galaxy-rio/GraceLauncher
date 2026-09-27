package com.galaxyrio.gracelauncher

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.IconPackStatus
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.IconPackPicker
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconPackUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun choosingInstalledPureUpdatesHomeAndCanReturnToSystemIcons() {
        val repository = IconPackRepository(context)
        val packs = runBlocking { repository.installedPacks() }
        assumeTrue(packs.any { it.packageName == IconPackRepositoryTest.PurePackage })
        val apps = AppRepository(context, repository)
        val system = runBlocking { apps.loadApps() }
        val pure = runBlocking { apps.loadApps(IconPackRepositoryTest.PurePackage) }
        val favorites = system.filter { it.packageName in setOf("com.android.chrome", "com.google.android.gm", "com.google.android.calendar") }
            .map { it.key }.toSet()
        var state by mutableStateOf(LauncherUiState(apps = system, favoriteKeys = favorites,
            isLoadingApps = false, iconPacks = packs, themedIcons = true))
        var edits = 0
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                LauncherScreen(state, onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {},
                    actions = LauncherActions(updateSettings = { change ->
                        edits++
                        val settings = change(state.settings)
                        state = state.copy(settings = settings,
                            apps = if (settings.iconPackPackage == null) system else pure,
                            iconPackStatus = if (settings.iconPackPackage == null) IconPackStatus.System else IconPackStatus.Ready)
                    }))
            }
        }
        openPickerFromHome()
        compose.onNodeWithTag("icon_pack:system").assertIsSelected()
        compose.onNodeWithTag("icon_pack:${IconPackRepositoryTest.PurePackage}").assertIsNotSelected()
        screenshot("icon-pack-picker.png")
        compose.onNodeWithTag("icon_pack:${IconPackRepositoryTest.PurePackage}").performClick()
        compose.onNodeWithTag("icon_pack_picker").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, edits)
            assertEquals(IconPackRepositoryTest.PurePackage, state.settings.iconPackPackage)
            assertTrue(state.favoriteApps.all { it.iconPackPackage == IconPackRepositoryTest.PurePackage })
        }
        returnHome()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        screenshot("icon-pack-pure-home.png")
        openPickerFromHome()
        compose.onNodeWithTag("icon_pack:${IconPackRepositoryTest.PurePackage}").assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.settings_cancel)).performClick()
        compose.runOnIdle { assertEquals("Cancel must not change the pack", 1, edits) }
        clickSetting("settings_icon_pack")
        compose.onNodeWithTag("icon_pack:system").performClick()
        compose.runOnIdle {
            assertEquals(2, edits)
            assertNull(state.settings.iconPackPackage)
            assertTrue(state.apps.all { it.iconPackPackage == null })
        }
        returnHome()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        screenshot("icon-pack-system-home.png")
    }

    @Test fun emptyPickerExplainsInstallationAndCancelDoesNotMutateSettings() {
        var selections = 0
        var dismissals = 0
        compose.setContent {
            GraceLauncherTheme {
                IconPackPicker(LauncherUiState(), onSelect = { selections++ }, onRefresh = {}, onDismiss = { dismissals++ })
            }
        }
        compose.onNodeWithTag("icon_pack:system").assertIsSelected()
        compose.onNodeWithTag("icon_pack_empty").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, selections); assertEquals(1, dismissals) }
    }

    @Test fun missingPackKeepsSelectionUntilUserChoosesSystemAndScanFailuresCanRetry() {
        val missing = "grace.uninstalled.iconpack"
        var selected: String? = missing
        var refreshes = 0
        compose.setContent {
            GraceLauncherTheme {
                IconPackPicker(LauncherUiState(settings = LauncherSettings(iconPackPackage = missing),
                    iconPackStatus = IconPackStatus.Unavailable, iconPacksLoadFailed = true),
                    onSelect = { selected = it }, onRefresh = { refreshes++ }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("icon_pack:system").assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.icon_pack_missing, missing)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.licenses_retry)).performClick()
        compose.runOnIdle { assertEquals(1, refreshes); assertEquals(missing, selected) }
        compose.onNodeWithTag("icon_pack:system").performClick()
        compose.runOnIdle { assertNull(selected) }
    }

    private fun openPickerFromHome() {
        compose.onNodeWithTag("launcher_fab").performTouchInput { longClick() }
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
        clickSetting("settings_category_themes")
        clickSetting("settings_icon_pack")
        compose.onNodeWithTag("icon_pack_picker").assertIsDisplayed()
    }

    private fun clickSetting(tag: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun returnHome() {
        compose.onNodeWithTag("settings_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings_back").performClick()
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
