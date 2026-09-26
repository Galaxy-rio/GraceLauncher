package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol

/** Folder contents use exactly the same swipe reveal surface as app shortcuts. */
@Composable
fun FolderPopup(
    folder: LauncherFolder,
    apps: List<LauncherApp>,
    anchor: Rect,
    onDismiss: () -> Unit,
    onLaunchApp: (LauncherApp, Rect) -> Unit,
    onEdit: () -> Unit,
    reveal: ShortcutRevealState = remember { ShortcutRevealState() },
) {
    val members = remember(folder.appKeys, apps) {
        val available = apps.associateBy(LauncherApp::key)
        folder.appKeys.distinct().mapNotNull(available::get)
    }
    val editDescription = stringResource(R.string.edit_folder)
    SwipeRevealPanel(
        anchor = anchor,
        reveal = reveal,
        panelTag = "folder_popup",
        title = folder.name,
        onDismiss = onDismiss,
    ) { maxListHeight ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("folder_header")
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIcon(LauncherSymbol.Folder, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                folder.name,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("folder_edit").semantics { contentDescription = editDescription },
            ) {
                LauncherIcon(LauncherSymbol.Edit, Modifier.size(20.dp))
            }
        }
        if (members.isEmpty()) {
            Text(
                stringResource(R.string.empty_folder),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp).testTag("folder_empty"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.heightIn(max = maxListHeight).testTag("folder_members")) {
            items(members, key = LauncherApp::key) { app ->
                var iconBounds by remember { mutableStateOf(Rect.Zero) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("folder_app:${app.key}")
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            onLaunchApp(app, iconBounds)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(
                        app,
                        modifier = Modifier.onGloballyPositioned { iconBounds = it.boundsInWindow() },
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(22.dp))
                    Text(app.label, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
