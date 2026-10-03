package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.search.AppSearchScreen
import java.util.UUID

sealed interface LauncherOverlay {
    data class Shortcuts(val app: LauncherApp, val anchor: Rect, val reveal: ShortcutRevealState = ShortcutRevealState()) : LauncherOverlay
    data class AppDetails(val app: LauncherApp, val popupOwner: LauncherApp? = null) : LauncherOverlay
    data class EditIcon(val app: LauncherApp, val popupOwner: LauncherApp? = null) : LauncherOverlay
    data class EditPopup(val app: LauncherApp) : LauncherOverlay
    data class Folder(val folder: LauncherFolder, val anchor: Rect, val reveal: ShortcutRevealState = ShortcutRevealState()) : LauncherOverlay
    data class FolderSettings(val folderId: String) : LauncherOverlay
    data class Categories(val app: LauncherApp) : LauncherOverlay
    data class CategoryApps(val name: String) : LauncherOverlay
    data object Agenda : LauncherOverlay
    data object Favorites : LauncherOverlay
    data object Settings : LauncherOverlay
    data class SettingsDestination(val page: String) : LauncherOverlay
    data object HomeWidgetMenu : LauncherOverlay
    data object CustomWidgetMenu : LauncherOverlay
    data object Search : LauncherOverlay
}

@Composable
internal fun LauncherPanelTheme(content: @Composable () -> Unit) {
    // Panels share the chosen dynamic/custom scheme and dark mode with settings.
    content()
}

@Composable
internal fun panelWindowHeight(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherOverlays(
    overlay: LauncherOverlay?,
    uiState: LauncherUiState,
    actions: LauncherActions,
    onChange: (LauncherOverlay?) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onToggleFavorite: (LauncherApp) -> Unit,
    onRequestCalendar: () -> Unit,
    searchBackProgress: Float = 0f,
) {
    if (overlay == null) return
    if (overlay == LauncherOverlay.Favorites) {
        FavoritesScreen(uiState, onToggleFavorite, actions.reorderFavorites) { onChange(null) }
        return
    }
    if (overlay is LauncherOverlay.EditIcon) {
        IconEditorScreen(uiState.findItem(overlay.app.key) ?: overlay.app, uiState, actions) {
            onChange(LauncherOverlay.AppDetails(overlay.app, overlay.popupOwner))
        }
        return
    }
    if (overlay is LauncherOverlay.EditPopup) {
        val back = { onChange(LauncherOverlay.AppDetails(overlay.app)) }
        BackHandler(onBack = back)
        PopupEditorScreen(uiState.findItem(overlay.app.key) ?: overlay.app, uiState, actions, back)
        return
    }
    if (overlay == LauncherOverlay.Settings || overlay is LauncherOverlay.FolderSettings || overlay is LauncherOverlay.SettingsDestination) {
        LauncherSettingsScreen(
            uiState = uiState, actions = actions, onBack = { onChange(null) },
            initialPage = when (overlay) {
                is LauncherOverlay.FolderSettings -> "folders"
                is LauncherOverlay.SettingsDestination -> overlay.page
                else -> null
            },
            initialFolderId = (overlay as? LauncherOverlay.FolderSettings)?.folderId,
        )
        return
    }
    if (overlay == LauncherOverlay.Search) {
        AppSearchScreen(
            uiState, actions,
            onLaunch = { onChange(null); onLaunchApp(it) },
            onDetails = { onChange(LauncherOverlay.AppDetails(it)) },
            onDismiss = { onChange(null) },
            backProgress = searchBackProgress,
        )
        return
    }
    if (overlay is LauncherOverlay.Folder) {
        val folder = uiState.folders.firstOrNull { it.id == overlay.folder.id }
        if (folder == null) {
            LaunchedEffect(overlay.folder.id) { onChange(null) }
            return
        }
        val members = folder.appKeys.mapNotNull { key -> uiState.visibleApps.firstOrNull { it.key == key } }
        FolderPopup(
            folder = folder, apps = members, anchor = overlay.anchor, reveal = overlay.reveal,
            onDismiss = { onChange(null) },
            onLaunchApp = { app, bounds ->
                onChange(null)
                actions.launchAppAt?.invoke(app, bounds) ?: onLaunchApp(app)
            },
            onEdit = { onChange(LauncherOverlay.FolderSettings(folder.id)) },
        )
        return
    }
    if (overlay is LauncherOverlay.Shortcuts) {
        ShortcutPopup(
            app = overlay.app, anchor = overlay.anchor, hasAccess = uiState.hasShortcutAccess, actions = actions,
            reveal = overlay.reveal,
            notifications = if (overlay.app.shortcut == null) uiState.notifications[overlay.app.packageName].orEmpty() else emptyList(),
            uiState = uiState,
            onEdit = { onChange(LauncherOverlay.EditPopup(overlay.app)) },
            onDetails = { onChange(LauncherOverlay.AppDetails(it, overlay.app)) },
            onLaunchItem = { item, bounds ->
                onChange(null)
                actions.launchAppAt?.invoke(item, bounds) ?: onLaunchApp(item)
            },
            onLaunchApp = { onChange(null); onLaunchApp(overlay.app) }, onDismiss = { onChange(null) },
        )
        return
    }
    val maxHeight = panelWindowHeight() * 0.9f
    LauncherPanelTheme {
        val sheetColor = if (overlay == LauncherOverlay.Agenda) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.surface
        ModalBottomSheet(
            onDismissRequest = { onChange(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("launcher_sheet"),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            containerColor = sheetColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = Color.Black.copy(alpha = 0.6f),
            dragHandle = null,
            properties = ModalBottomSheetProperties(
                isAppearanceLightStatusBars = false,
                isAppearanceLightNavigationBars = sheetColor.luminance() > 0.5f,
            ),
        ) {
          // Limit the content, not the dialog's anchoring window: the sheet must
          // always meet the physical bottom edge, including on tall displays.
          Box(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            when (overlay) {
                is LauncherOverlay.AppDetails -> AppDetailsSheet(
                    app = uiState.findItem(overlay.app.key) ?: overlay.app,
                    actions = actions, onChange = onChange, popupOwner = overlay.popupOwner, uiState = uiState,
                )
                LauncherOverlay.Agenda -> AgendaSheet(uiState, actions, onRequestCalendar)
                LauncherOverlay.HomeWidgetMenu -> HomeWidgetSheet(uiState, actions, onChange, custom = false)
                LauncherOverlay.CustomWidgetMenu -> HomeWidgetSheet(uiState, actions, onChange, custom = true)
                is LauncherOverlay.Categories -> CategoryPicker(overlay.app, uiState, actions) { onChange(LauncherOverlay.AppDetails(overlay.app)) }
                is LauncherOverlay.CategoryApps -> CategoryAppsSheet(overlay.name, uiState) { onChange(null); onLaunchApp(it) }
                LauncherOverlay.Settings, LauncherOverlay.Search, is LauncherOverlay.Shortcuts,
                LauncherOverlay.Favorites, is LauncherOverlay.EditIcon, is LauncherOverlay.EditPopup,
                is LauncherOverlay.Folder, is LauncherOverlay.FolderSettings, is LauncherOverlay.SettingsDestination -> Unit
            }
          }
        }
    }
}

private val DetailsContentInset = 12.dp
private val DetailsIconColumnWidth = 34.dp
private val DetailsIconTextSpacing = 20.dp

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AppDetailsSheet(app: LauncherApp, actions: LauncherActions, onChange: (LauncherOverlay?) -> Unit,
    popupOwner: LauncherApp?, uiState: LauncherUiState) {
    var advanced by remember(app.key) { mutableStateOf(false) }
    val advancedRotation by animateFloatAsState(
        targetValue = if (advanced) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "advancedArrow",
    )
    var rename by remember(app.key) { mutableStateOf(false) }
    val editIconDescription = stringResource(R.string.edit_icon)
    // Extend touch surfaces into the gutter, keeping their inset content on the
    // same two columns as the header (icon center and text leading edge).
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp).testTag("app_details")) {
        Row(Modifier.padding(horizontal = DetailsContentInset).padding(bottom = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, modifier = Modifier.testTag("app_details_icon").clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = editIconDescription }
                .clickable(role = Role.Button, onClickLabel = editIconDescription) { onChange(LauncherOverlay.EditIcon(app, popupOwner)) }, size = DetailsIconColumnWidth)
            Spacer(Modifier.width(DetailsIconTextSpacing))
            Text(app.label, modifier = Modifier.weight(1f).testTag("app_details_title")
                .clickable(onClickLabel = stringResource(R.string.rename_app)) { rename = true }.padding(vertical = 8.dp),
                fontSize = 25.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val owner = popupOwner ?: app
        if (app.shortcut != null) {
            val shown = uiState.apps.any { it.key == app.key }
            PanelAction(LauncherSymbol.Apps, stringResource(R.string.popup_show_in_app_list), "show_in_app_list",
                iconColumnWidth = DetailsIconColumnWidth, iconTextSpacing = DetailsIconTextSpacing,
                trailing = { Switch(shown, onCheckedChange = null) }) { actions.showShortcutInAppList(app, !shown) }
        }
        DetailsAction(LauncherSymbol.Star, stringResource(R.string.edit_favorites), "edit_favorites") { onChange(LauncherOverlay.Favorites) }
        DetailsAction(LauncherSymbol.Info, stringResource(R.string.app_info)) { onChange(null); actions.appInfo(app) }
        DetailsAction(LauncherSymbol.Hourglass, stringResource(R.string.screen_time)) { onChange(null); actions.screenTime(app) }
        DetailsAction(LauncherSymbol.Folder, stringResource(R.string.add_to_folder)) { onChange(LauncherOverlay.Categories(app)) }
        if (app.shortcut == null) DetailsAction(LauncherSymbol.Delete, stringResource(R.string.uninstall)) { onChange(null); actions.uninstall(app) }
        DetailsAction(LauncherSymbol.Chevron, stringResource(R.string.advanced), "advanced", iconRotation = advancedRotation) { advanced = !advanced }
        AnimatedVisibility(advanced) {
            Column {
                DetailsAction(LauncherSymbol.Edit, stringResource(R.string.rename_app)) { rename = true }
                DetailsAction(LauncherSymbol.Palette, stringResource(R.string.edit_icon)) { onChange(LauncherOverlay.EditIcon(app, popupOwner)) }
                DetailsAction(LauncherSymbol.Launch, stringResource(R.string.edit_app_popup, owner.label)) { onChange(LauncherOverlay.EditPopup(owner)) }
                DetailsAction(LauncherSymbol.Launch, stringResource(R.string.store_page)) { onChange(null); actions.storePage(app) }
                Text(app.packageName, Modifier.padding(start = DetailsContentInset + DetailsIconColumnWidth + DetailsIconTextSpacing, end = DetailsContentInset, bottom = 14.dp).testTag("app_details_package"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = DetailsContentInset).padding(top = 6.dp, bottom = 8.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        DetailsAction(LauncherSymbol.Settings, stringResource(R.string.grace_settings), "grace_settings") { onChange(LauncherOverlay.Settings) }
    }
    if (rename) TextEntryDialog(
        title = stringResource(R.string.rename_app), initial = app.label,
        onDismiss = { rename = false }, onSave = { actions.rename(app, it); rename = false },
        onReset = { actions.rename(app, ""); rename = false },
    )
}

@Composable
private fun DetailsAction(symbol: LauncherSymbol, label: String, tag: String = label, iconRotation: Float = 0f, onClick: () -> Unit) {
    PanelAction(symbol, label, tag, iconColumnWidth = DetailsIconColumnWidth, iconTextSpacing = DetailsIconTextSpacing,
        iconRotation = iconRotation, onClick = onClick)
}

@Composable
internal fun PanelAction(
    symbol: LauncherSymbol,
    label: String,
    tag: String = label,
    iconColumnWidth: Dp = 23.dp,
    iconTextSpacing: Dp = 23.dp,
    summary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(iconColumnWidth), contentAlignment = Alignment.Center) {
            LauncherIcon(symbol, Modifier.size(23.dp).rotate(iconRotation).testTag("$tag:icon"))
        }
        Spacer(Modifier.width(iconTextSpacing))
        Column(Modifier.weight(1f)) {
            Text(label, Modifier.testTag("$tag:label"), fontSize = 16.sp, letterSpacing = 0.2.sp)
            if (summary != null) Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (trailing != null) trailing()
    }
}

@Composable
internal fun PanelTitle(title: String) {
    Text(title, Modifier.padding(top = 26.dp, bottom = 24.dp), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun CategoryPicker(app: LauncherApp, uiState: LauncherUiState, actions: LauncherActions, onDone: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
        PanelTitle(stringResource(R.string.add_to_folder))
        Text(app.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        uiState.folders.forEach { folder ->
            val isMember = app.key in folder.appKeys
            PanelAction(if (isMember) LauncherSymbol.Check else LauncherSymbol.Folder, folder.name) {
                actions.saveFolder(folder.copy(appKeys = if (isMember) folder.appKeys - app.key else folder.appKeys + app.key))
                onDone()
            }
        }
        PanelAction(LauncherSymbol.Plus, stringResource(R.string.create_app_folder)) { creating = true }
        Spacer(Modifier.height(24.dp))
    }
    if (creating) TextEntryDialog(stringResource(R.string.create_app_folder), "", { creating = false }, {
        actions.saveFolder(LauncherFolder(UUID.randomUUID().toString(), it, listOf(app.key), FolderPlacement.AppList))
        creating = false; onDone()
    })
}

@Composable
private fun CategoryAppsSheet(name: String, uiState: LauncherUiState, onLaunch: (LauncherApp) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        PanelTitle(name)
        LazyColumn(Modifier.heightIn(max = panelWindowHeight() * 0.65f)) {
            items(uiState.visibleApps.filter { uiState.categories[it.key] == name }, key = LauncherApp::key) { app ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onLaunch(app) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, size = 36.dp)
                    Spacer(Modifier.width(20.dp))
                    Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TextEntryDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onReset: (() -> Unit)? = null) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(40.dp),
        title = { Text(title) },
        text = { OutlinedTextField(value, onValueChange = { value = it.take(80) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onReset != null) TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
