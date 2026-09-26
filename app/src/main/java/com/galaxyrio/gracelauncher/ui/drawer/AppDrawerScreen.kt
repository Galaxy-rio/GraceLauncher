package com.galaxyrio.gracelauncher.ui.drawer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LauncherFontFamily

@Composable
fun AppDrawerScreen(
    model: AppListModel,
    listState: LazyListState,
    selectedLetter: String?,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppDetails: (LauncherApp) -> Unit,
    onAppShortcuts: (LauncherApp, Rect) -> Unit,
    modifier: Modifier = Modifier,
    rowGestures: AppRowGestures = AppRowGestures(),
    highlightedAppKey: String? = null,
) {
    val appearance = LocalLauncherAppearance.current
    val visibleItems = remember(model, selectedLetter) { model.itemsFor(selectedLetter) }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 56.dp)
            .testTag("app_drawer"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        items(
            items = visibleItems,
            key = DrawerItem::key,
            contentType = { if (it is DrawerItem.Header) "header" else "app" },
        ) { item ->
            when (item) {
                is DrawerItem.Header -> Text(
                    text = item.section,
                    modifier = Modifier
                        .height(44.dp)
                        .padding(start = 8.dp, end = 8.dp, top = 12.dp)
                        .testTag("section:${item.section}"),
                    color = appearance.text,
                    style = TextStyle(
                        fontFamily = LauncherFontFamily,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Normal,
                        shadow = appearance.textShadow,
                    ),
                )
                is DrawerItem.App -> LauncherAppRow(
                    app = item.app,
                    onClick = { onLaunchApp(item.app) },
                    onLongClick = { onAppDetails(item.app) },
                    onSwipeRight = { onAppShortcuts(item.app, it) },
                    gestures = rowGestures,
                    highlighted = highlightedAppKey == item.app.key,
                )
            }
        }
    }
}
