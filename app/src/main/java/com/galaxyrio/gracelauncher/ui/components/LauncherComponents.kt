package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance

// Retained desktop lists keep their scroll position under full-screen pages,
// but must cancel the long press that opened the page before being unplaced.
internal val LocalLauncherInputEnabled = staticCompositionLocalOf { true }

@Composable
fun AppIcon(app: LauncherApp, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    if (LocalLauncherAppearance.current.themedIcons && app.monochromeIcon != null) {
        Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Image(
                bitmap = app.monochromeIcon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                // Adaptive monochrome drawables include the platform's safe-zone inset.
                modifier = Modifier.size(size).graphicsLayer { scaleX = app.monochromeScale; scaleY = app.monochromeScale },
            )
        }
    } else if (app.icon != null) {
        Image(bitmap = app.icon, contentDescription = null, modifier = modifier.size(size))
    } else {
        val colors = listOf(
            Color(0xFF65D5BE), Color(0xFFFFB3A7), Color(0xFFAEC6FF),
            Color(0xFFFFD18A), Color(0xFFD4B7FF),
        )
        val color = remember(app.key) { colors[(app.key.hashCode() and Int.MAX_VALUE) % colors.size] }
        Box(
            modifier = modifier.size(size).background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.firstOrNull()?.uppercase().orEmpty(),
                color = Color(0xFF13201E),
                fontSize = (size.value * 0.43f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun FolderIcon(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(
            LauncherSymbol.Folder,
            modifier = Modifier.size(size * 0.6f),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun LauncherAppRow(
    app: LauncherApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeRight: (Rect) -> Unit = {},
    gestures: AppRowGestures = AppRowGestures(),
    highlighted: Boolean = false,
    notification: AppNotification? = null,
) {
    LauncherRow(
        rowKey = "app:${app.key}",
        label = app.label,
        onClick = { _, iconBounds -> gestures.onLaunchAt?.invoke(app, iconBounds) ?: onClick() },
        onLongClick = onLongClick,
        onOpen = onSwipeRight,
        onPrepare = { gestures.onPrepare(app) },
        onDrag = { bounds, progress -> gestures.onDrag(app, bounds, progress) },
        onDragEnd = gestures.onDragEnd,
        openDescription = stringResource(R.string.app_shortcuts),
        detailsDescription = stringResource(R.string.app_actions),
        modifier = modifier,
        highlighted = highlighted,
        notification = notification,
    ) { iconModifier -> AppIcon(app, modifier = iconModifier, size = 40.dp) }
}

@Composable
fun FolderRow(
    folder: LauncherFolder,
    onOpen: (Rect) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (Rect, Float) -> Unit = { _, _ -> },
    onDragEnd: (Boolean) -> Unit = {},
    highlighted: Boolean = false,
) {
    LauncherRow(
        rowKey = "folder:${folder.id}",
        label = folder.name,
        onClick = { bounds, _ -> onOpen(bounds) },
        onLongClick = onLongClick,
        onOpen = onOpen,
        onPrepare = {},
        onDrag = onDrag,
        onDragEnd = onDragEnd,
        openDescription = stringResource(R.string.open_folder),
        detailsDescription = stringResource(R.string.folder_actions),
        modifier = modifier,
        highlighted = highlighted,
    ) { iconModifier -> FolderIcon(modifier = iconModifier) }
}

/** Apps and folders share hit targets, ripple clipping, and one swipe recognizer. */
@Composable
private fun LauncherRow(
    rowKey: String,
    label: String,
    onClick: (rowBounds: Rect, iconBounds: Rect) -> Unit,
    onLongClick: () -> Unit,
    onOpen: (Rect) -> Unit,
    onPrepare: () -> Unit,
    onDrag: (Rect, Float) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    openDescription: String,
    detailsDescription: String,
    modifier: Modifier,
    highlighted: Boolean,
    notification: AppNotification? = null,
    icon: @Composable (Modifier) -> Unit,
) {
    val appearance = LocalLauncherAppearance.current
    val inputEnabled = LocalLauncherInputEnabled.current
    val currentPrepare by rememberUpdatedState(onPrepare)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var iconBounds by remember { mutableStateOf(Rect.Zero) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val highlight by animateColorAsState(
        // Press feedback comes from Material ripple. Only a selected details
        // row retains a subtle state layer after the pointer has been released.
        if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(100), label = "launcherRowPressSurface",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(rowKey)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clip(RoundedCornerShape(18.dp))
            .background(highlight)
            .pointerInput(rowKey, inputEnabled) {
                if (!inputEnabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentPrepare()
                }
            }
            .pointerInput(rowKey, inputEnabled) {
                if (!inputEnabled) return@pointerInput
                val distance = 72.dp.toPx()
                val commitDistance = 32.dp.toPx()
                var revealed = false
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset = 0f; revealed = false },
                    onDragCancel = {
                        if (revealed) currentDragEnd(false)
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        if (revealed) currentDragEnd(dragOffset >= commitDistance)
                        dragOffset = 0f
                    },
                ) { change, amount ->
                    change.consume()
                    // Preserve overshoot so a small reversal after a long swipe
                    // does not close a panel while the finger is still far right.
                    dragOffset += amount
                    if (dragOffset > 0f || revealed) {
                        revealed = true
                        currentDrag(bounds, (dragOffset / distance).coerceIn(0f, 1f))
                    }
                }
            }
            .semantics {
                selected = pressed || highlighted
                customActions = listOf(
                    CustomAccessibilityAction(openDescription) { onOpen(bounds); true },
                    CustomAccessibilityAction(detailsDescription) { onLongClick(); true },
                )
            }
            .combinedClickable(
                enabled = inputEnabled,
                interactionSource = interactionSource, indication = ripple(bounded = true, color = appearance.text),
                onClick = { onClick(bounds, iconBounds) },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(Modifier.onGloballyPositioned { iconBounds = it.boundsInWindow() })
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label + (notification?.let { " · ${notificationAge(it.postedAt)}" } ?: ""),
                style = MaterialTheme.typography.bodyLarge.merge(
                    TextStyle(fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp, shadow = appearance.textShadow),
                ),
                color = appearance.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification != null) {
                Spacer(Modifier.height(2.dp))
                listOf(notification.title, notification.text.replace('\n', ' ')).filter { it.isNotBlank() }.forEach { line ->
                    Text(line, color = appearance.text.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = appearance.textShadow),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (notification != null) {
            IconButton(
                onClick = { onPrepare(); onOpen(bounds) },
                enabled = inputEnabled,
                modifier = Modifier.size(48.dp).testTag("notification_arrow:$rowKey")
                    .semantics { contentDescription = openDescription },
            ) {
                LauncherIcon(LauncherSymbol.Chevron,
                    Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Ltr) -90f else 90f),
                    tint = appearance.text)
            }
        }
    }
}

data class AppRowGestures(
    val onPrepare: (LauncherApp) -> Unit = {},
    val onDrag: (LauncherApp, Rect, Float) -> Unit = { _, _, _ -> },
    val onDragEnd: (Boolean) -> Unit = {},
    val onLaunchAt: ((LauncherApp, Rect) -> Unit)? = null,
)
