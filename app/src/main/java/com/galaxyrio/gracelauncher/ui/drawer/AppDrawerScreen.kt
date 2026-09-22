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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.WallpaperTextShadow

@Composable
fun AppDrawerScreen(
    model: AppListModel,
    listState: LazyListState,
    topSpace: Dp,
    viewportHeight: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onToggleFavorite: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 72.dp, top = topSpace)
            .testTag("app_drawer"),
        // Enough trailing space to bring even the last section to the same reading position.
        contentPadding = PaddingValues(bottom = (viewportHeight - topSpace - 96.dp).coerceAtLeast(24.dp)),
    ) {
        items(
            items = model.items,
            key = DrawerItem::key,
            contentType = { if (it is DrawerItem.Header) "header" else "app" },
        ) { item ->
            when (item) {
                is DrawerItem.Header -> Text(
                    text = item.section,
                    modifier = Modifier
                        .height(36.dp)
                        .padding(top = 6.dp)
                        .testTag("section:${item.section}"),
                    color = Color.White,
                    style = TextStyle(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Normal,
                        shadow = WallpaperTextShadow,
                    ),
                )
                is DrawerItem.App -> LauncherAppRow(
                    app = item.app,
                    onClick = { onLaunchApp(item.app) },
                    onLongClick = { onToggleFavorite(item.app) },
                )
            }
        }
    }
}
