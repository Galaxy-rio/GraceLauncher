package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutRevealState
import com.galaxyrio.gracelauncher.ui.overlays.SwipeRevealPanel
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutsFoldersTest {
    @get:Rule val compose = createComposeRule()
    private val owner = LauncherApp(ComponentName("fixture.owner", "Activity"), "Owner", null)
    private val target = LauncherApp(ComponentName("fixture.target", "Activity"), "Target", null)
    private val folder = LauncherFolder("tools", "Tools", listOf(target.key), FolderPlacement.Favorites)

    @Test fun firstItemUsesDefaultOrCustomOrderAndResolvesWidgetApplication() {
        val shortcut = LauncherShortcut("first", owner.packageName, "Shortcut", null).asApp(owner)
        val state = LauncherUiState(apps = listOf(owner, target), folders = listOf(folder))
        assertEquals(shortcut, state.firstPopupApp(owner, listOf(shortcut)))
        assertEquals(target, state.firstPopupApp(folder.asApp(), emptyList()))
        fun withEntries(vararg entries: PopupItem) = state.copy(popups = mapOf(owner.key to entries.toList()))
        assertEquals(target, withEntries(PopupItem(target.key), PopupItem(shortcut.key)).firstPopupApp(owner, listOf(shortcut)))
        assertEquals(target, withEntries(PopupItem("widget:1", HomeLayout(widgetId = 1,
            widgetProvider = "fixture.target/Widget"))).firstPopupApp(owner, emptyList()))
        assertNull(withEntries().firstPopupApp(owner, listOf(shortcut)))
        assertNull(withEntries(PopupItem("missing/Activity"), PopupItem(target.key)).firstPopupApp(owner, listOf(shortcut)))
    }

    @Test fun hiddenSilentNotificationDoesNotDisplaceTheLatestVisiblePreview() {
        val ordinary = AppNotification("ordinary", owner.packageName, "Ordinary", "Body", 1, 1, true, true)
        val silent = ordinary.copy(key = "silent", postedAt = 2, silent = true)
        val state = LauncherUiState(notifications = mapOf(owner.packageName to listOf(silent, ordinary)))
        assertEquals(listOf(ordinary), state.notificationsFor(owner, expanded = false))
        val expandedOnly = state.copy(settings = state.settings.copy(shortcutsFolders = ShortcutsFoldersSettings(
            silentNotifications = NotificationDisplay.ExpandedOnly, normalNotifications = NotificationDisplay.Hidden)))
        assertTrue(expandedOnly.notificationsFor(owner, expanded = false).isEmpty())
        assertEquals(listOf(silent), expandedOnly.notificationsFor(owner, expanded = true))
    }

    @Test fun permissionPromptMovesIntoNewSettingsPageAndModesAreSingleChoice() {
        var state by mutableStateOf(LauncherUiState())
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(updateSettings = { change -> state = state.copy(settings = change(state.settings)) }),
                    onBack = {}, initialPage = "Productivity")
            }
        }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_shortcuts_folders"))
        compose.onNodeWithTag("notifications_access").assertDoesNotExist()
        compose.onNodeWithTag("settings_shortcuts_folders").performClick()
        compose.onNodeWithTag("shortcuts_folders_enabled").assertIsOn()
        compose.onNodeWithTag("notifications_access").assertIsDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("popup_silent:expanded"))
        compose.onNodeWithTag("popup_silent:hidden").assertIsOn()
            .assert(hasAnyAncestor(hasTestTag("popup_silent")))
        compose.onNodeWithTag("popup_silent:expanded").performClick().assertIsOn()
        compose.onNodeWithTag("popup_silent:hidden").assertIsOff()
        // Clicking the active button must not leave a single-choice group empty.
        compose.onNodeWithTag("popup_silent:expanded").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(NotificationDisplay.ExpandedOnly, state.settings.shortcutsFolders.silentNotifications) }
    }

    @Test fun masterSwitchDisablesAppPopupButDoesNotHideOrDisableFolders() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherScreen(LauncherUiState(apps = listOf(owner, target), favoriteKeys = setOf(owner.key),
                    folders = listOf(folder), isLoadingApps = false, settings = LauncherSettings(
                        shortcutsFolders = ShortcutsFoldersSettings(enabled = false))),
                    onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {})
            }
        }
        compose.onNodeWithTag("app:${owner.key}").performTouchInput { swipeRight() }
        compose.onNodeWithTag("shortcut_popup").assertDoesNotExist()
        compose.onNodeWithTag("folder:${folder.id}").assertIsDisplayed().performTouchInput { swipeRight() }
        compose.onNodeWithTag("folder_popup").assertIsDisplayed()
    }

    @Test fun openingAndClosingEmitOneHapticEachIncludingDirectDismissal() {
        val feedback = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback += hapticFeedbackType }
        }
        var visible by mutableStateOf(true)
        var reveal by mutableStateOf(ShortcutRevealState())
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    if (visible) SwipeRevealPanel(Rect(20f, 80f, 60f, 120f), reveal, "panel", "Panel", { visible = false }) { Text("Item") }
                }
            }
        }
        compose.runOnIdle { assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), feedback); reveal.expanded = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate, HapticFeedbackType.GestureEnd), feedback)
            reveal = ShortcutRevealState(); visible = true
        }
        compose.runOnIdle { visible = false }
        compose.runOnIdle { assertEquals(List(2) { listOf(HapticFeedbackType.GestureThresholdActivate, HapticFeedbackType.GestureEnd) }.flatten(), feedback) }
    }
}
