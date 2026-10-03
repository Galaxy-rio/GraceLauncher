@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.icons.IconLayers
import com.galaxyrio.gracelauncher.data.icons.ItemIconStore
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherTypography
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance

@Composable
internal fun IconDesignerPreview(
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
