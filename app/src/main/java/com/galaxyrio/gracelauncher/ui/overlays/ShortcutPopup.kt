package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.ShortcutStatus
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import kotlin.math.roundToInt

@Stable
class ShortcutRevealState(progress: Float = 1f, dragging: Boolean = false) {
    var progress by mutableFloatStateOf(progress)
    var dragging by mutableStateOf(dragging)
    var expanded by mutableStateOf(true)
}

/** Same-window overlay: adding a popup window during DOWN would cancel the row's drag. */
@Composable
fun ShortcutPopup(
    app: LauncherApp,
    anchor: Rect,
    hasAccess: Boolean,
    actions: LauncherActions,
    onLaunchApp: () -> Unit,
    onDismiss: () -> Unit,
    reveal: ShortcutRevealState = remember { ShortcutRevealState() },
    notifications: List<AppNotification> = emptyList(),
) {
    var retry by remember { mutableIntStateOf(0) }
    val loadShortcuts by rememberUpdatedState(actions.shortcuts)
    val initialResult = remember(app.key, hasAccess) { actions.cachedShortcuts(app) ?: ShortcutResult(ShortcutStatus.Loading) }
    val result by produceState(initialResult, app.key, hasAccess, retry) {
        // Keep cached content while refreshing; never insert a progress indicator.
        value = loadShortcuts(app)
    }
    SwipeRevealPanel(
        anchor = anchor,
        reveal = reveal,
        panelTag = "shortcut_popup",
        title = stringResource(R.string.app_shortcuts),
        onDismiss = onDismiss,
    ) { maxListHeight ->
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("shortcut_header")
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onLaunchApp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIcon(LauncherSymbol.Launch, Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.heightIn(max = maxListHeight).testTag("shortcut_list")) {
            items(notifications, key = { "notification:${it.key}" }) { notification ->
                NotificationPopupItem(
                    app, notification,
                    onOpen = { if (actions.openNotification(notification.key, notification.revision)) onDismiss() },
                    onDismiss = { actions.dismissNotification(notification.key, notification.revision) },
                    modifier = Modifier.animateItem(),
                    gesturesEnabled = !reveal.dragging && reveal.expanded,
                )
            }
        when (result.status) {
            // Cold apps may need one binder query; keep the layout quiet.
            ShortcutStatus.Loading -> if (notifications.isEmpty()) item { Spacer(Modifier.height(56.dp)) }
            ShortcutStatus.DefaultLauncherRequired -> item {
                Column {
                Text(stringResource(R.string.shortcut_permission), Modifier.padding(horizontal = 12.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = actions.requestDefaultHome) { Text(stringResource(R.string.set_default_launcher)) }
                }
            }
            ShortcutStatus.Error -> item {
                Column {
                Text(stringResource(R.string.shortcut_error), Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
            }
            ShortcutStatus.Ready -> {
                if (result.shortcuts.isEmpty() && notifications.isEmpty()) item {
                    Text(stringResource(R.string.no_shortcuts), Modifier.padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                }
                    items(result.shortcuts, key = { "shortcut:${it.id}" }) { shortcut ->
                        var iconBounds by remember { mutableStateOf(Rect.Zero) }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("shortcut:${shortcut.id}")
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    actions.launchShortcutAt?.invoke(shortcut, iconBounds) ?: actions.launchShortcut(shortcut)
                                    onDismiss()
                                }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp).onGloballyPositioned { iconBounds = it.boundsInWindow() }) {
                                if (shortcut.icon != null) {
                                    Image(shortcut.icon, null, Modifier.fillMaxSize())
                                } else {
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                        LauncherIcon(LauncherSymbol.Launch, Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                            Spacer(Modifier.width(22.dp))
                            Text(shortcut.label, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
            }
        }
        }
    }
}

/** One same-window reveal/positioning implementation for app shortcuts and folders. */
@Composable
internal fun SwipeRevealPanel(
    anchor: Rect,
    reveal: ShortcutRevealState,
    panelTag: String,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(maxListHeight: Dp) -> Unit,
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val margin = with(density) { 32.dp.roundToPx() }
    val maxListHeight = with(density) { windowSize.height.toDp() } * 0.55f
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val settledProgress by animateFloatAsState(
        targetValue = if (reveal.dragging) reveal.progress else if (reveal.expanded) 1f else 0f,
        animationSpec = if (reveal.dragging) snap() else spring(dampingRatio = 0.86f, stiffness = 600f),
        label = "swipePanelReveal",
        finishedListener = { if (!reveal.dragging && !reveal.expanded) currentOnDismiss() },
    )
    // Draw direct pointer progress instead of chasing a sequence of animations.
    val progress = if (reveal.dragging) reveal.progress else settledProgress
    LaunchedEffect(reveal.dragging, reveal.expanded) {
        if (!reveal.dragging && !reveal.expanded && reveal.progress == 0f) currentOnDismiss()
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }) {
        Box(Modifier.matchParentSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = onDismiss,
        ).clearAndSetSemantics {})
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                LauncherPanelTheme {
                    Surface(
                        modifier = Modifier.testTag(panelTag).semantics {
                            paneTitle = title
                            dismiss { onDismiss(); true }
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        },
                        shape = SwipeRevealShape(progress),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 16.dp * progress,
                    ) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp)) {
                            content(maxListHeight)
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val panelWidth = (constraints.maxWidth - margin * 2).coerceIn(1, 440.dp.roundToPx())
            val panel = measurables.single().measure(Constraints(
                minWidth = panelWidth, maxWidth = panelWidth,
                maxHeight = (constraints.maxHeight - margin * 2).coerceAtLeast(1),
            ))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val top = (anchor.center.y - origin.y - panel.height / 2f).roundToInt()
                    .coerceIn(margin, (constraints.maxHeight - panel.height - margin).coerceAtLeast(margin))
                panel.placeRelative((constraints.maxWidth - panel.width) / 2, top)
            }
        }
    }
}

/** Reveal full-size content under a bowed right edge, without stretching icons/text. */
private class SwipeRevealShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val fraction = progress.coerceIn(0f, 1f)
        val right = size.width * fraction
        val radius = with(density) { 24.dp.toPx() }.coerceAtMost(right / 2f).coerceAtMost(size.height / 2f)
        if (fraction >= 0.999f) return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
        val bow = (size.width * 0.2f * (1f - fraction)).coerceAtMost(right * 0.4f)
        val shoulder = (right - bow).coerceAtLeast(radius)
        return Outline.Generic(Path().apply {
            moveTo(radius, 0f)
            lineTo(shoulder, 0f)
            cubicTo(right + bow / 3f, size.height * 0.23f, right + bow / 3f, size.height * 0.77f, shoulder, size.height)
            lineTo(radius, size.height)
            quadraticTo(0f, size.height, 0f, size.height - radius)
            lineTo(0f, radius)
            quadraticTo(0f, 0f, radius, 0f)
            close()
        })
    }
}
