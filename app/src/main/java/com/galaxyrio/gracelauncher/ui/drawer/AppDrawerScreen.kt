package com.galaxyrio.gracelauncher.ui.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.FolderRow
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LauncherFontFamily

@Composable
fun AppDrawerScreen(
    model: AppListModel,
    listState: LazyListState,
    selectedLetter: String?,
    topSpace: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppDetails: (LauncherApp) -> Unit,
    onAppShortcuts: (LauncherApp, Rect) -> Unit,
    modifier: Modifier = Modifier,
    rowGestures: AppRowGestures = AppRowGestures(),
    highlightedAppKey: String? = null,
    onOpenFolder: (LauncherFolder, Rect) -> Unit = { _, _ -> },
    onEditFolder: (LauncherFolder) -> Unit = {},
    onFolderDrag: (LauncherFolder, Rect, Boolean) -> Unit = { _, _, _ -> },
    onFolderDragEnd: (Boolean) -> Unit = {},
    notifications: Map<String, List<AppNotification>> = emptyMap(),
) {
    val appearance = LocalLauncherAppearance.current
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("app_drawer"),
        // Scrollable leading space, not padding on the viewport: earlier groups
        // may occupy this area after jumping to a later letter. The small, real
        // bottom inset lets LazyColumn naturally clamp sections near the end.
        contentPadding = PaddingValues(start = LauncherLayout.Start, end = LauncherLayout.End, top = topSpace, bottom = 24.dp),
        userScrollEnabled = selectedLetter == null,
    ) {
        items(
            items = model.items,
            key = DrawerItem::key,
            contentType = { when (it) { is DrawerItem.Header -> "header"; is DrawerItem.App -> "app"; is DrawerItem.Folder -> "folder" } },
        ) { item ->
            // Keep every item's key and measured height. Hiding a group must
            // not change scroll bounds or move the selected header on release.
            Box(Modifier.retainItemSpace(selectedLetter == null || item.section == selectedLetter)) {
                when (item) {
                    is DrawerItem.Header -> Text(
                        text = if (item.section == FolderSection) stringResource(R.string.drawer_folders) else item.section,
                        modifier = Modifier
                            .height(44.dp)
                            .padding(start = LauncherLayout.ContentInset, end = LauncherLayout.ContentInset, top = 12.dp)
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
                        notification = notifications[item.app.packageName]?.firstOrNull(),
                    )
                    is DrawerItem.Folder -> FolderRow(
                        folder = item.folder,
                        onOpen = { onOpenFolder(item.folder, it) },
                        onLongClick = { onEditFolder(item.folder) },
                        onDrag = { bounds, expanded -> onFolderDrag(item.folder, bounds, expanded) },
                        onDragEnd = onFolderDragEnd,
                    )
                }
            }
        }
    }
}

private fun Modifier.retainItemSpace(visible: Boolean): Modifier =
    // Unlike alpha=0, not placing a child also removes its invisible hit targets.
    // Clear descendants from TalkBack while retaining the complete list geometry.
    then(if (visible) Modifier else Modifier.clearAndSetSemantics {}).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            if (visible) placeable.placeRelative(0, 0)
        }
    }
