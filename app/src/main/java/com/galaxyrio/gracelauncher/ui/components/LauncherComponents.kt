package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance

@Composable
fun AppIcon(app: LauncherApp, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    if (LocalLauncherAppearance.current.themedIcons && app.monochromeIcon != null) {
        Box(modifier.size(size).clip(CircleShape).background(Color(0xFFCAC5FF)), contentAlignment = Alignment.Center) {
            Image(
                bitmap = app.monochromeIcon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color(0xFF332774)),
                // Adaptive monochrome drawables include the platform's safe-zone inset.
                modifier = Modifier.size(size).graphicsLayer { scaleX = 1.4f; scaleY = 1.4f },
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
fun LauncherAppRow(
    app: LauncherApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeRight: (Rect) -> Unit = {},
    gestures: AppRowGestures = AppRowGestures(),
    highlighted: Boolean = false,
) {
    val appearance = LocalLauncherAppearance.current
    val currentSwipe by rememberUpdatedState(onSwipeRight)
    val currentGestures by rememberUpdatedState(gestures)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var iconBounds by remember { mutableStateOf(Rect.Zero) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val highlight by animateColorAsState(
        // Press feedback comes from Material ripple. Only a selected details
        // row retains a subtle state layer after the pointer has been released.
        if (highlighted) Color(0xFFCAC5FF).copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(100), label = "appPressSurface",
    )
    val shortcutsDescription = stringResource(R.string.app_shortcuts)
    val detailsDescription = stringResource(R.string.app_actions)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("app:${app.key}")
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clip(RoundedCornerShape(18.dp))
            .background(highlight)
            .pointerInput(app.key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentGestures.onPrepare(app)
                }
            }
            .pointerInput(app.key) {
                val distance = 72.dp.toPx()
                val commitDistance = 32.dp.toPx()
                var revealed = false
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset = 0f; revealed = false },
                    onDragCancel = {
                        if (revealed) currentGestures.onDragEnd(false)
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        if (revealed) currentGestures.onDragEnd(dragOffset >= commitDistance)
                        dragOffset = 0f
                    },
                ) { change, amount ->
                    change.consume()
                    // Preserve overshoot so a small reversal after a long swipe
                    // does not close a panel while the finger is still far right.
                    dragOffset += amount
                    if (dragOffset > 0f || revealed) {
                        revealed = true
                        currentGestures.onDrag(app, bounds, (dragOffset / distance).coerceIn(0f, 1f))
                    }
                }
            }
            .semantics {
                selected = pressed || highlighted
                customActions = listOf(
                    CustomAccessibilityAction(shortcutsDescription) { currentSwipe(bounds); true },
                    CustomAccessibilityAction(detailsDescription) { onLongClick(); true },
                )
            }
            .combinedClickable(
                interactionSource = interactionSource, indication = ripple(bounded = true, color = appearance.text),
                onClick = { currentGestures.onLaunchAt?.invoke(app, iconBounds) ?: onClick() },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, modifier = Modifier.onGloballyPositioned { iconBounds = it.boundsInWindow() }, size = 40.dp)
        Spacer(Modifier.width(20.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp, shadow = appearance.textShadow),
            ),
            color = appearance.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

data class AppRowGestures(
    val onPrepare: (LauncherApp) -> Unit = {},
    val onDrag: (LauncherApp, Rect, Float) -> Unit = { _, _, _ -> },
    val onDragEnd: (Boolean) -> Unit = {},
    val onLaunchAt: ((LauncherApp, Rect) -> Unit)? = null,
)
