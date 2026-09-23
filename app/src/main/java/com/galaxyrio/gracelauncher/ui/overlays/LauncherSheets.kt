package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol

sealed interface LauncherOverlay {
    data class Shortcuts(val app: LauncherApp, val anchor: Rect, val reveal: ShortcutRevealState = ShortcutRevealState()) : LauncherOverlay
    data class AppDetails(val app: LauncherApp) : LauncherOverlay
    data class Categories(val app: LauncherApp) : LauncherOverlay
    data class CategoryApps(val name: String) : LauncherOverlay
    data object Agenda : LauncherOverlay
    data object Favorites : LauncherOverlay
    data object Settings : LauncherOverlay
}

@Composable
internal fun LauncherPanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF5F5891), onPrimary = Color.White,
            primaryContainer = Color(0xFFE6E0FF), onPrimaryContainer = Color(0xFF302952),
            surface = Color(0xFFFFFBFF), onSurface = Color(0xFF211F29),
            onSurfaceVariant = Color(0xFF706C76), surfaceContainerHigh = Color(0xFFF0EBF5),
        ),
        content = content,
    )
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
) {
    if (overlay == null) return
    if (overlay is LauncherOverlay.Shortcuts) {
        ShortcutPopup(
            app = overlay.app, anchor = overlay.anchor, hasAccess = uiState.hasShortcutAccess, actions = actions,
            reveal = overlay.reveal,
            onLaunchApp = { onChange(null); onLaunchApp(overlay.app) }, onDismiss = { onChange(null) },
        )
        return
    }
    val maxHeight = panelWindowHeight() * 0.9f
    LauncherPanelTheme {
        ModalBottomSheet(
            onDismissRequest = { onChange(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("launcher_sheet"),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = Color.Black.copy(alpha = 0.6f),
            dragHandle = null,
            properties = ModalBottomSheetProperties(isAppearanceLightStatusBars = false, isAppearanceLightNavigationBars = true),
        ) {
          // Limit the content, not the dialog's anchoring window: the sheet must
          // always meet the physical bottom edge, including on tall displays.
          Box(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            when (overlay) {
                is LauncherOverlay.AppDetails -> AppDetailsSheet(
                    app = uiState.apps.firstOrNull { it.key == overlay.app.key } ?: overlay.app,
                    actions = actions, onChange = onChange,
                )
                LauncherOverlay.Agenda -> AgendaSheet(uiState, actions, onRequestCalendar)
                LauncherOverlay.Favorites -> FavoritesSheet(uiState, onToggleFavorite) { onChange(null) }
                is LauncherOverlay.Categories -> CategoryPicker(overlay.app, uiState, actions) { onChange(LauncherOverlay.AppDetails(overlay.app)) }
                is LauncherOverlay.CategoryApps -> CategoryAppsSheet(overlay.name, uiState) { onChange(null); onLaunchApp(it) }
                LauncherOverlay.Settings -> SettingsSheet(uiState, actions, onChange)
                is LauncherOverlay.Shortcuts -> Unit
            }
          }
        }
    }
}

@Composable
private fun AppDetailsSheet(app: LauncherApp, actions: LauncherActions, onChange: (LauncherOverlay?) -> Unit) {
    var advanced by remember(app.key) { mutableStateOf(false) }
    var rename by remember(app.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = 22.dp, bottom = 12.dp).testTag("app_details")) {
        Row(Modifier.padding(bottom = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, size = 34.dp)
            Spacer(Modifier.width(20.dp))
            Text(app.label, fontSize = 25.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        PanelAction(LauncherSymbol.Star, stringResource(R.string.edit_favorites), "edit_favorites") { onChange(LauncherOverlay.Favorites) }
        PanelAction(LauncherSymbol.Info, stringResource(R.string.app_info)) { onChange(null); actions.appInfo(app) }
        PanelAction(LauncherSymbol.Hourglass, stringResource(R.string.screen_time)) { onChange(null); actions.screenTime(app) }
        PanelAction(LauncherSymbol.Category, stringResource(R.string.add_to_category)) { onChange(LauncherOverlay.Categories(app)) }
        PanelAction(LauncherSymbol.Delete, stringResource(R.string.uninstall)) { onChange(null); actions.uninstall(app) }
        PanelAction(LauncherSymbol.Chevron, stringResource(R.string.advanced), "advanced") { advanced = !advanced }
        AnimatedVisibility(advanced) {
            Column(Modifier.padding(start = 8.dp)) {
                PanelAction(LauncherSymbol.Edit, stringResource(R.string.rename_app)) { rename = true }
                PanelAction(LauncherSymbol.Launch, stringResource(R.string.store_page)) { onChange(null); actions.storePage(app) }
                Text(app.packageName, Modifier.padding(start = 46.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 8.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        PanelAction(LauncherSymbol.Settings, stringResource(R.string.grace_settings), "grace_settings") { onChange(LauncherOverlay.Settings) }
    }
    if (rename) TextEntryDialog(
        title = stringResource(R.string.rename_app), initial = app.label,
        onDismiss = { rename = false }, onSave = { actions.rename(app, it); rename = false },
        onReset = { actions.rename(app, ""); rename = false },
    )
}

@Composable
internal fun PanelAction(symbol: LauncherSymbol, label: String, tag: String = label, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(symbol, Modifier.size(23.dp))
        Spacer(Modifier.width(23.dp))
        Text(label, fontSize = 16.sp, letterSpacing = 0.2.sp)
    }
}

@Composable
internal fun PanelTitle(title: String) {
    Text(title, Modifier.padding(top = 26.dp, bottom = 24.dp), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun FavoritesSheet(uiState: LauncherUiState, onToggle: (LauncherApp) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).testTag("favorites_sheet")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PanelTitle(stringResource(R.string.edit_favorites)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
        LazyColumn(Modifier.heightIn(max = panelWindowHeight() * 0.65f)) {
            items(uiState.apps, key = LauncherApp::key) { app ->
                val checked = app.key in uiState.favoriteKeys
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("favorite:${app.key}")
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(app) })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, size = 34.dp)
                    Spacer(Modifier.width(18.dp))
                    Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(12.dp))
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryPicker(app: LauncherApp, uiState: LauncherUiState, actions: LauncherActions, onDone: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val categories = uiState.categories.values.distinct().sorted()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
        PanelTitle(stringResource(R.string.add_to_category))
        Text(app.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        categories.forEach { category ->
            PanelAction(if (uiState.categories[app.key] == category) LauncherSymbol.Check else LauncherSymbol.Category, category) {
                actions.categorize(app, category); onDone()
            }
        }
        PanelAction(LauncherSymbol.Plus, stringResource(R.string.new_category)) { creating = true }
        if (app.key in uiState.categories) PanelAction(LauncherSymbol.Delete, stringResource(R.string.remove_category)) {
            actions.categorize(app, null); onDone()
        }
        Spacer(Modifier.height(24.dp))
    }
    if (creating) TextEntryDialog(stringResource(R.string.new_category), "", { creating = false }, {
        actions.categorize(app, it); creating = false; onDone()
    })
}

@Composable
private fun CategoryAppsSheet(name: String, uiState: LauncherUiState, onLaunch: (LauncherApp) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        PanelTitle(name)
        LazyColumn(Modifier.heightIn(max = panelWindowHeight() * 0.65f)) {
            items(uiState.apps.filter { uiState.categories[it.key] == name }, key = LauncherApp::key) { app ->
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
private fun SettingsSheet(uiState: LauncherUiState, actions: LauncherActions, onChange: (LauncherOverlay?) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
        PanelTitle(stringResource(R.string.grace_settings))
        PanelAction(LauncherSymbol.Apps, stringResource(R.string.set_default_launcher)) { actions.requestDefaultHome() }
        PanelAction(LauncherSymbol.Star, stringResource(R.string.edit_favorites)) { onChange(LauncherOverlay.Favorites) }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.wallpaper_text), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperTextMode.entries.forEach { mode ->
                val label = when (mode) {
                    WallpaperTextMode.Auto -> R.string.text_auto
                    WallpaperTextMode.Light -> R.string.text_light
                    WallpaperTextMode.Dark -> R.string.text_dark
                }
                FilterChip(selected = uiState.textMode == mode, onClick = { actions.textMode(mode) }, label = { Text(stringResource(label)) })
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .toggleable(value = uiState.themedIcons, role = Role.Switch, onValueChange = actions.themedIcons)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.themed_icons))
                Text(stringResource(R.string.themed_icons_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = uiState.themedIcons, onCheckedChange = null)
        }
        val categories = uiState.apps.mapNotNull { uiState.categories[it.key] }.distinct().sorted()
        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleSmall)
            categories.forEach { category -> PanelAction(LauncherSymbol.Category, category) { onChange(LauncherOverlay.CategoryApps(category)) } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TextEntryDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onReset: (() -> Unit)? = null) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
