package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import kotlinx.coroutines.isActive

@Composable
internal fun FavoritesSheet(
    uiState: LauncherUiState,
    onToggle: (LauncherApp) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    val selected = uiState.favoriteApps.map(LauncherApp::key)
    val apps = uiState.visibleApps.associateBy(LauncherApp::key)
    val list = rememberLazyListState()
    val commit by rememberUpdatedState(onReorder)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val reorder = remember(list) {
        FavoritesReorderState(list, selected, { commit(it) }) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(selected) { reorder.synchronize(selected) }
    val density = LocalDensity.current
    LaunchedEffect(reorder.draggingKey, density) {
        if (reorder.draggingKey == null) return@LaunchedEffect
        val edge = with(density) { 64.dp.toPx() }
        val speed = with(density) { 640.dp.toPx() }
        var previous = withFrameNanos { it }
        while (isActive && reorder.draggingKey != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previous) / 1_000_000_000f).coerceIn(0f, 0.032f)
            previous = frame
            reorder.autoScroll(edge, speed * seconds)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).testTag("favorites_sheet")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PanelTitle(stringResource(R.string.edit_favorites)) }
            TextButton(onClick = onDone, modifier = Modifier.testTag("favorites_done")) { Text(stringResource(R.string.done)) }
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = panelWindowHeight() * 0.65f).testTag("favorites_list"),
            state = list,
            contentPadding = PaddingValues(bottom = 16.dp),
            userScrollEnabled = reorder.draggingKey == null,
        ) {
            item(key = "selected_header", contentType = "header") {
                FavoritesHeading(stringResource(R.string.favorites_selected), "favorites_selected")
            }
            if (reorder.keys.isEmpty()) item(key = "empty") {
                Text(stringResource(R.string.favorites_empty), Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(reorder.keys.mapNotNull(apps::get), key = { FavoritesReorderState.itemKey(it.key) }, contentType = { "selected" }) { app ->
                val dragging = reorder.draggingKey == app.key
                val offset by animateFloatAsState(
                    if (dragging) reorder.translation else 0f,
                    animationSpec = if (dragging) snap() else spring(), label = "favoriteDrop",
                )
                val background by animateColorAsState(
                    if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                    label = "favoriteLift",
                )
                val index = reorder.keys.indexOf(app.key)
                val moveUp = stringResource(R.string.favorites_move_up)
                val moveDown = stringResource(R.string.favorites_move_down)
                FavoriteChoice(
                    app, checked = true, tag = "favorite:${app.key}", onToggle = onToggle,
                    enabled = reorder.draggingKey == null,
                    modifier = Modifier
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .zIndex(if (dragging || offset != 0f) 1f else 0f)
                        .graphicsLayer {
                            // Layout can move this item to its new slot before an
                            // animateFloatAsState(snap()) updates on the next frame.
                            // Read the compensating offset in the draw phase so it
                            // never flashes over a neighbor during a reorder.
                            translationY = if (dragging) reorder.translation else offset
                        }
                        .clip(RoundedCornerShape(16.dp)).background(background)
                        .semantics {
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { reorder.moveBy(app.key, -1) })
                                if (index < reorder.keys.lastIndex) add(CustomAccessibilityAction(moveDown) { reorder.moveBy(app.key, 1) })
                            }
                        },
                ) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menu = true },
                            modifier = Modifier.testTag("favorite_drag:${app.key}").pointerInput(reorder, app.key) {
                                detectDragGestures(
                                    onDragStart = { reorder.start(app.key) },
                                    onDragEnd = { reorder.finish(cancelled = false) },
                                    onDragCancel = { reorder.finish(cancelled = true) },
                                ) { change, amount -> change.consume(); reorder.drag(amount.y) }
                            },
                        ) {
                            Icon(painterResource(R.drawable.ms_drag_indicator), stringResource(R.string.favorites_reorder, app.label),
                                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(moveUp) }, enabled = index > 0,
                                onClick = { reorder.moveBy(app.key, -1); menu = false })
                            DropdownMenuItem(text = { Text(moveDown) }, enabled = index < reorder.keys.lastIndex,
                                onClick = { reorder.moveBy(app.key, 1); menu = false })
                        }
                    }
                }
            }
            item(key = "all_header", contentType = "header") {
                Spacer(Modifier.height(16.dp))
                FavoritesHeading(stringResource(R.string.favorites_all_apps), "favorites_all_apps")
            }
            items(uiState.visibleApps, key = { "all:${it.key}" }, contentType = { "app" }) { app ->
                FavoriteChoice(app, app.key in uiState.favoriteKeys, "favorite_all:${app.key}", onToggle,
                    enabled = reorder.draggingKey == null, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun FavoritesHeading(text: String, tag: String) {
    Text(text, Modifier.fillMaxWidth().testTag(tag).semantics { heading() }.padding(horizontal = 8.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FavoriteChoice(
    app: LauncherApp,
    checked: Boolean,
    tag: String,
    onToggle: (LauncherApp) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(app) })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        AppIcon(app, size = 36.dp)
        Spacer(Modifier.width(16.dp))
        Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}
