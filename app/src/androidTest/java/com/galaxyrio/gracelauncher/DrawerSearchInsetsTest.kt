package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.drawer.AppDrawerScreen
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerSearchInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchAndLastAppStayAboveNavigationAndKeyboard() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val apps = (1..40).map { index ->
            LauncherApp(ComponentName("test.insets", "test.insets.App$index"), "App ${index.toString().padStart(2, '0')}", null)
        }
        val model = AppListModel(apps)
        var searchExpanded by mutableStateOf(false)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                AppDrawerScreen(
                    model = model,
                    listState = rememberLazyListState(),
                    selectedLetter = null,
                    topSpace = 0.dp,
                    onLaunchApp = {}, onAppDetails = {}, onAppShortcuts = { _, _ -> },
                    searchState = rememberTextFieldState(),
                    searchExpanded = searchExpanded,
                    onSearchExpandedChange = { searchExpanded = it },
                )
            }
        }

        fun drawerBottom() = compose.onNodeWithTag("app_drawer").fetchSemanticsNode().boundsInRoot.bottom
        fun assertListAndSearchFit() {
            compose.onNodeWithTag("app_drawer").performScrollToIndex(model.items.lastIndex)
            val lastApp = compose.onNodeWithTag("app:${apps.last().key}").fetchSemanticsNode().boundsInRoot
            val search = compose.onNodeWithTag(if (searchExpanded) "drawer_search" else "drawer_search_button").fetchSemanticsNode().boundsInRoot
            if (searchExpanded) {
                assertTrue("Search must be at the top", search.center.y < drawerBottom() / 2)
                assertTrue("Last app must stay below the top search bar", lastApp.top >= search.bottom)
            } else assertTrue("Last app must scroll above search button", lastApp.bottom <= search.top)
            assertTrue("Search must fit in the safe viewport", search.bottom <= drawerBottom())
        }
        val decor = compose.activity.window.decorView
        val navigation = compose.runOnIdle {
            WindowInsetsCompat.toWindowInsetsCompat(decor.rootWindowInsets).getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        }
        val closedBottom = drawerBottom()
        assertTrue("Drawer must avoid system navigation", closedBottom <= decor.height - navigation + 1f)
        assertListAndSearchFit()

        compose.onNodeWithTag("drawer_search_button").performClick()
        compose.onNodeWithTag("drawer_search").assertIsFocused()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.runOnIdle {
                WindowInsetsCompat.toWindowInsetsCompat(decor.rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime())
            }
        }
        compose.waitForIdle()
        assertTrue("Drawer viewport must shrink above the keyboard", drawerBottom() < closedBottom)
        val keyboard = compose.runOnIdle {
            WindowInsetsCompat.toWindowInsetsCompat(decor.rootWindowInsets).getInsets(WindowInsetsCompat.Type.ime()).bottom
        }
        assertTrue("Drawer must avoid the keyboard", drawerBottom() <= decor.height - keyboard + 1f)
        assertListAndSearchFit()

        compose.runOnIdle {
            WindowInsetsControllerCompat(compose.activity.window, decor).hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.runOnIdle {
                !WindowInsetsCompat.toWindowInsetsCompat(decor.rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime())
            } && kotlin.math.abs(closedBottom - drawerBottom()) <= 1f
        }
        compose.waitForIdle()
        assertEquals("Closing the keyboard must restore the viewport", closedBottom, drawerBottom(), 1f)
        assertListAndSearchFit()
    }
}
