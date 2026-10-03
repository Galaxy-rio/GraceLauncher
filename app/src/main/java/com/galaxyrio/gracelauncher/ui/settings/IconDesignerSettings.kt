@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.isBulkIconDesignEligible
import com.galaxyrio.gracelauncher.data.icons.IconLayers
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.ItemIconStore
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherTypography
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import kotlinx.coroutines.launch

@Composable
internal fun IconDesignerSettings(uiState: LauncherUiState, actions: LauncherActions, selectedKey: String?, onBack: () -> Unit, onChooseApp: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { IconPackRepository(context) }
    val store = remember(context) { ItemIconStore(context, repository) }
    val scope = rememberCoroutineScope()
    var all by rememberSaveable { mutableStateOf(false) }
    var drafts by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    var bulk by rememberSaveable { mutableStateOf((uiState.settings.iconDesign ?: ItemIcon.Theme).encode()) }
    var pendingImages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var sourcePicker by rememberSaveable { mutableStateOf(false) }
    var colorPicker by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val selectedApp = selectedKey?.let(uiState::findItem)
    val initial = selectedApp?.let { app ->
        uiState.itemIcons[app.key] ?: uiState.settings.iconDesign?.takeIf { isBulkIconDesignEligible(app, null) }?.let {
            if (it.kind == "pack" && app.iconPackPackage == it.source) it.copy(design = IconDesign()) else it
        }
    } ?: ItemIcon.Theme
    val encoded = if (all) bulk else drafts[selectedKey] ?: initial.encode()
    val choice = remember(encoded) { ItemIcon.decode(encoded) ?: ItemIcon.Theme }
    val design = choice.design ?: IconDesign()
    fun change(value: ItemIcon) {
        if (all) bulk = value.encode() else if (selectedKey != null) drafts = drafts + (selectedKey to value.encode())
    }
    fun changeDesign(value: IconDesign) { change(choice.copy(design = value.normalized())) }
    fun close() {
        if (saving) return
        scope.launch {
            pendingImages.forEach { store.deleteImage(ItemIcon("image", it)) }
            pendingImages = emptyList()
            onBack()
        }
    }
    val back = { if (sourcePicker) sourcePicker = false else if (colorPicker != null) colorPicker = null else close() }
    BackHandler(enabled = sourcePicker || colorPicker != null || pendingImages.isNotEmpty(), onBack = back)
    if (sourcePicker) {
        IconDesignerSourceSettings(uiState, all, repository, store,
            onSelect = { change(it.copy(design = design)); sourcePicker = false },
            onImport = { pendingImages = pendingImages + it.source }, onBack = { sourcePicker = false })
        return
    }
    val layers by produceState<IconLayers?>(null, selectedApp?.key, choice.kind, choice.source, choice.name,
        uiState.settings.enabledIconPackPackages, all) {
        value = null
        if (!all && selectedApp != null) value = store.layers(selectedApp, choice, uiState.settings)
    }
    val dynamicColors = store.dynamicColors(uiState.settings)
    val sourceLabel = when (choice.kind) {
        "pack" -> uiState.iconPacks.firstOrNull { it.packageName == choice.source }?.label ?: choice.source
        "image" -> stringResource(R.string.icon_edit_image)
        "system" -> stringResource(R.string.icon_pack_system)
        else -> stringResource(R.string.icon_edit_follow_theme)
    }
    SettingsScaffold(stringResource(R.string.icon_designer_title), "icon_designer", back, fixedCollapsed = true,
        actions = {
            Button(onClick = {
                saving = true
                scope.launch {
                    try {
                        val saved = choice.copy(design = design.normalized())
                        val success = if (all) actions.applyIconDesign(saved) else selectedApp?.let { actions.setItemIcon(it, saved) } == true
                        if (success) {
                            if (saved.kind == "image") pendingImages = pendingImages - saved.source
                            pendingImages.forEach { store.deleteImage(ItemIcon("image", it)) }
                            pendingImages = emptyList()
                            onBack()
                        } else Toast.makeText(context, R.string.icon_edit_failed, Toast.LENGTH_LONG).show()
                    } finally { saving = false }
                }
            }, enabled = !saving && (all || selectedApp != null),
                modifier = Modifier.padding(end = 8.dp).testTag("icon_designer_save")) { Text(stringResource(R.string.icon_designer_save)) }
        }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            val previewHeight = (maxHeight * 0.40f).coerceIn(168.dp, 264.dp)
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                IconDesignerPreview(uiState, selectedApp, choice, design, layers, store, dynamicColors, all, !saving, onChooseApp, ::changeDesign,
                    Modifier.padding(top = 8.dp, bottom = 16.dp).height(previewHeight))
                val color = colorPicker
                if (color != null) key(color) {
                    val background = color == "background"
                    IconDesignerColorPicker(stringResource(if (background) {
                        if (all || layers?.layered == true) R.string.icon_designer_tray_color else R.string.icon_designer_gradient_start
                    } else if (all || layers?.layered == true) R.string.icon_designer_symbol_color else R.string.icon_designer_gradient_end),
                        if (background) design.background else design.foreground, if (background) dynamicColors.first else dynamicColors.second,
                        onChange = { if (background) changeDesign(design.copy(background = it)) else changeDesign(design.copy(foreground = it)) },
                        onClose = { colorPicker = null }, modifier = Modifier.weight(1f))
                } else LazyColumn(
                    Modifier.weight(1f).testTag("icon_designer_controls"),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    iconDesignerControls(all, !saving && (all || selectedApp != null), selectedApp?.label, sourceLabel, design,
                        layers?.layered ?: selectedApp?.isAdaptiveIcon == true, dynamicColors, onChooseApp, { sourcePicker = true },
                        { colorPicker = if (it) "background" else "foreground" }, ::changeDesign)
                }
            }
            if (colorPicker == null) HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceBright,
                ),
                expandedShadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = FloatingToolbarDefaults.ScreenOffset)
                    .testTag("icon_designer_toolbar").selectableGroup(),
            ) {
                DesignerMode(stringResource(R.string.icon_designer_special), !all, "icon_designer_special") { all = false }
                DesignerMode(stringResource(R.string.icon_designer_all), all, "icon_designer_all") { all = true }
            }
        }
    }
}

@Composable
private fun DesignerMode(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 104.dp).testTag(tag)
            .semantics { this.selected = selected; role = Role.Tab },
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(label) }
}

@Composable
private fun IconDesignerPreview(
    uiState: LauncherUiState, selectedApp: LauncherApp?, choice: ItemIcon, design: IconDesign, layers: IconLayers?,
    store: ItemIconStore, dynamicColors: Pair<Int, Int>, all: Boolean, enabled: Boolean, onChooseApp: () -> Unit,
    onChange: (IconDesign) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons)
    val shape = ListItemDefaults.segmentedShapes(0, 1).shape
    Box(modifier.fillMaxWidth().testTag("icon_designer_preview").drawWithContent {
        // Match Clock style's window onto the real wallpaper, including live wallpapers.
        drawOutline(shape.createOutline(size, layoutDirection, this), Color.Transparent, blendMode = BlendMode.Clear)
        drawContent()
    }, contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalLauncherAppearance provides appearance) {
            MaterialTheme(typography = LocalLauncherTypography.current) {
                if (all) AllIconsPreview(uiState, choice, store, dynamicColors)
                else if (selectedApp == null) {
                    val description = stringResource(R.string.icon_designer_choose_app)
                    Box(
                        Modifier.size(80.dp).testTag("icon_designer_choose_app").clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(role = Role.Button, onClick = onChooseApp).semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        LauncherIcon(LauncherSymbol.Plus, Modifier.size(32.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                } else DesignerPreviewIcon(selectedApp, layers, design, dynamicColors, 128.dp,
                    Modifier.testTag("icon_designer_selected_app"), draggable = enabled, onChange = onChange)
            }
        }
    }
}

/** Rows reflect the original icon type even when a pack or a per-app design changes the artwork. */
internal fun iconDesignerPreviewRows(apps: List<LauncherApp>): List<List<LauncherApp>> {
    val activities = apps.filter { it.shortcut == null }
    return listOf(
        activities.filter { it.isSystemApp }.take(3),
        activities.filter { !it.isSystemApp && it.isAdaptiveIcon }.take(3),
        activities.filter { !it.isSystemApp && !it.isAdaptiveIcon }.take(3),
    )
}

@Composable
private fun AllIconsPreview(uiState: LauncherUiState, choice: ItemIcon, store: ItemIconStore, dynamicColors: Pair<Int, Int>) {
    val rows = remember(uiState.apps) { iconDesignerPreviewRows(uiState.apps) }
    val categories = listOf(stringResource(R.string.icon_designer_system_apps), stringResource(R.string.icon_designer_adaptive_apps),
        stringResource(R.string.icon_designer_legacy_apps))
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp).testTag("icon_designer_grid")) {
        val iconSize = ((maxHeight / 3 - 28.dp).coerceAtLeast(20.dp)).coerceAtMost(52.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            rows.forEachIndexed { row, entries ->
                Row(Modifier.fillMaxWidth().weight(1f).testTag("icon_designer_row:$row"), verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { column ->
                        val app = entries.getOrNull(column)
                        val label = app?.label ?: stringResource(R.string.icon_designer_sample, row * 3 + column + 1)
                        Column(
                            Modifier.weight(1f).testTag("icon_designer_icon:$row:$column")
                                .semantics(mergeDescendants = true) { contentDescription = "${categories[row]} · $label" },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (app != null) DesignerBulkPreviewIcon(app, uiState, choice, store, dynamicColors, iconSize)
                            else DesignerSamplePreviewIcon(row, column, choice, store, uiState.settings, dynamicColors, iconSize)
                            Text(label, color = LocalLauncherAppearance.current.text,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
