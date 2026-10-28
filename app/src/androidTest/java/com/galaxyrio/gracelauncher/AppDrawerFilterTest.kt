package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderId
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.drawer.DrawerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDrawerFilterTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var launched = ""

    @Before
    fun configureWindow() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun app(name: String) = LauncherApp(
        componentName = ComponentName("example.${name.lowercase()}", "example.${name.lowercase()}.Main"),
        label = name,
        icon = null,
    )

    @Test
    fun filtersCaseInsensitivelyClearsShowsEmptyAndLaunchesMatches() {
        val apps = listOf(app("Calculator"), app("Calendar"), app("Camera"))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color(0xFF152431))) {
                    LauncherScreen(
                        uiState = LauncherUiState(apps = apps, isLoadingApps = false,
                            scheduleStatus = ScheduleStatus.Ready, textMode = WallpaperTextMode.Light),
                        onDateClick = {}, onClockClick = {}, onLaunchApp = { launched = it.label },
                        onToggleFavorite = {}, initialDrawerOpen = true,
                    )
                }
            }
        }

        compose.onNodeWithTag("drawer_search").assertDoesNotExist()
        compose.onNodeWithTag("drawer_search_button").performClick()
        compose.onNodeWithTag("drawer_search").assertIsFocused().performTextInput("cal")
        compose.onNodeWithTag("app:${apps[0].key}").assertIsDisplayed()
        compose.onNodeWithTag("app:${apps[1].key}").assertIsDisplayed()
        compose.onNodeWithTag("app:${apps[2].key}").assertDoesNotExist()
        compose.onNodeWithTag("app:${apps[0].key}").performClick()
        compose.runOnIdle { assertEquals("Calculator", launched) }

        // Two matching apps in one section must not produce duplicate lazy keys.
        val model = AppListModel(apps)
        val matches = model.filterApps("cal")
        assertEquals(listOf("Calculator", "Calendar"), matches.filterIsInstance<DrawerItem.App>().map { it.app.label })
        assertEquals(matches.size, matches.map(DrawerItem::key).toSet().size)
        assertEquals(listOf("Camera"), model.filterApps("CAM").filterIsInstance<DrawerItem.App>().map { it.app.label })
        assertEquals(model.items, model.filterApps(" "))

        compose.onNodeWithTag("drawer_search_clear").performClick()
        compose.onNodeWithTag("drawer_search").performTextInput("absent")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.drawer_search_no_results))
            .assertIsDisplayed()
        compose.onNodeWithTag("drawer_search_clear").performClick()
        compose.onNodeWithTag("app:${apps[0].key}").assertIsDisplayed()
        compose.onNodeWithTag("app:${apps[1].key}").assertIsDisplayed()
        compose.onNodeWithTag("app:${apps[2].key}").assertIsDisplayed()

        compose.onNodeWithTag("drawer_search").performTextInput("cam")
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.settings_back))
            .performClick()
        compose.onNodeWithTag("drawer_search").assertDoesNotExist()
        compose.onNodeWithTag("drawer_search_button").assertIsDisplayed().performClick()
        val reopenedSearch = compose.onNodeWithTag("drawer_search").assertIsFocused().fetchSemanticsNode()
        assertEquals("", reopenedSearch.config[SemanticsProperties.EditableText].text)
        apps.forEach { app ->
            compose.onNodeWithTag("app_drawer").performScrollToNode(hasTestTag("app:${app.key}"))
            compose.onNodeWithTag("app:${app.key}").assertIsDisplayed()
        }
    }

    @Test
    fun bottomSearchButtonMorphsIntoTopSearchAndKeepsLastAppReachable() {
        val apps = (1..30).map { app("App ${it.toString().padStart(2, '0')}") }
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color(0xFF152431))) {
                    LauncherScreen(
                        uiState = LauncherUiState(apps = apps, isLoadingApps = false,
                            scheduleStatus = ScheduleStatus.Ready, textMode = WallpaperTextMode.Light),
                        onDateClick = {}, onClockClick = {}, onLaunchApp = {},
                        onToggleFavorite = {}, initialDrawerOpen = true,
                    )
                }
            }
        }

        val drawer = compose.onNodeWithTag("app_drawer").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("drawer_search").assertDoesNotExist()
        val search = compose.onNodeWithTag("drawer_search_button").fetchSemanticsNode().boundsInRoot
        assertTrue("Search button should be on the right", search.center.x > drawer.center.x)
        assertTrue("Search should be positioned in the lower half of the drawer", search.center.y > drawer.center.y)
        assertTrue("Search should remain inside the drawer viewport", search.bottom <= drawer.bottom)

        compose.onNodeWithTag("app_drawer").performScrollToNode(hasTestTag("app:${apps.last().key}"))
        compose.onNodeWithTag("app:${apps.last().key}").assertIsDisplayed()
        val lastApp = compose.onNodeWithTag("app:${apps.last().key}").fetchSemanticsNode().boundsInRoot
        assertTrue("The last app should not be covered by search", lastApp.bottom <= search.top)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawer_search_button").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("drawer_search").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        val expandedSearch = compose.onNodeWithTag("drawer_search").assertIsDisplayed().assertIsFocused()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Expanded search should finish at the top", expandedSearch.center.y < drawer.center.y)
        assertTrue("The bar should expand wider than the button", expandedSearch.width > search.width)
        compose.onNodeWithTag("drawer_search_button").assertDoesNotExist()
    }

    @Test
    fun collapsedPrivateAppsAreNotIncludedInFiltering() {
        val secret = app("SecretNotes")
        val model = AppListModel(
            apps = emptyList(),
            privateFolder = LauncherFolder(PrivateSpaceFolderId, "Private", emptyList(), FolderPlacement.AppList),
            privateExpanded = false,
            privateApps = listOf(secret),
        )

        assertFalse(model.items.any { it is DrawerItem.PrivateApp })
        assertFalse(model.filterApps("Secret").any { it is DrawerItem.PrivateApp })
    }
}
