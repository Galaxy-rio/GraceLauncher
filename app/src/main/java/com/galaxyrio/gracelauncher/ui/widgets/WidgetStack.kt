package com.galaxyrio.gracelauncher.ui.widgets

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.widgetStackIndicatorHeight
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LocalLauncherInputEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** The editor and every placement use the same live RemoteViews and page geometry. */
@Composable
internal fun WidgetStack(
    folder: LauncherFolder,
    uiState: LauncherUiState,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    onLongPress: () -> Unit = {},
    enabled: Boolean = true,
    gridManaged: Boolean = false,
    selectedKey: String? = null,
    onSelectedKeyChange: ((String) -> Unit)? = null,
) {
    val style = folder.widgetStack ?: return
    val widgets = uiState.popups[folder.key].orEmpty().filter { it.widget?.hasWidget == true }
    val host = LocalWidgetHost.current ?: rememberWidgetHost()
    val pager = rememberPagerState(initialPage = widgets.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)) { widgets.size }
    val select by rememberUpdatedState(onSelectedKeyChange)
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    val pageAnimationSpec = remember { spring<Float>(dampingRatio = .85f, stiffness = Spring.StiffnessMediumLow) }
    LaunchedEffect(selectedKey, widgets.map { it.key }) {
        settle?.cancel()
        val index = widgets.indexOfFirst { it.key == selectedKey }
        if (index >= 0 && (index != pager.currentPage || pager.currentPageOffsetFraction != 0f)) {
            // A list selection replaces any in-flight swipe and uses the same transition.
            // Include the offset so selecting the current page mid-animation returns smoothly.
            pager.animateScrollToPage(index, animationSpec = pageAnimationSpec)
        }
    }
    val inputEnabled = enabled && LocalLauncherInputEnabled.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    val scheme = MaterialTheme.colorScheme
    val height = style.height.coerceIn(80, 1200)
    val indicatorHeight = widgetStackIndicatorHeight(widgets.size)

    DisposableEffect(Unit) { onDispose { settle?.cancel() } }
    CompositionLocalProvider(LocalWidgetHost provides host) {
        Column(modifier.fillMaxWidth().height(style.layoutHeight(widgets.size).dp)
            .testTag("widget_stack:${folder.id}")
            .semantics { contentDescription = folder.name }
            .pointerInput(widgets.map { it.key }, inputEnabled, direction) {
                if (!inputEnabled || widgets.size < 2) return@pointerInput
                // Observe before AndroidView's interop handler. Once a horizontal drag wins,
                // consuming it cancels the provider's touch stream, including pending clicks.
                // Vertical gestures remain available to calendars and other scrollable widgets.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.isConsumed) return@awaitEachGesture
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    var previous = down.position
                    var dragging = false
                    var vertical = false
                    var released = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // A grid editor or another ancestor can claim this stream first.
                        if (change.isConsumed) break
                        if (event.changes.count { it.pressed } > 1) break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val travel = change.position - down.position
                        if (!dragging && !vertical) {
                            if (abs(travel.y) > viewConfiguration.touchSlop && abs(travel.y) > abs(travel.x)) vertical = true
                            else if (abs(travel.x) > viewConfiguration.touchSlop && abs(travel.x) > abs(travel.y)) {
                                // Taps and vertical scrolling must not strand a list-selection
                                // animation between pages. Only a horizontal drag takes over.
                                settle?.cancel()
                                scope.launch { pager.stopScroll() }
                                dragging = true
                                pager.dispatchRawDelta(direction * (travel.x - sign(travel.x) * viewConfiguration.touchSlop))
                            }
                        } else if (dragging) {
                            pager.dispatchRawDelta(direction * (change.position.x - previous.x))
                        }
                        previous = change.position
                        if (dragging) change.consume()
                        if (!change.pressed) { released = true; break }
                    }
                    if (dragging) {
                        val speed = direction * velocity.calculateVelocity().x
                        val offset = pager.currentPageOffsetFraction
                        val step = when {
                            released && abs(speed) > 500f && sign(speed) == sign(offset) -> sign(speed).toInt()
                            released && abs(offset) > .25f -> sign(offset).toInt()
                            else -> 0
                        }
                        val target = (pager.currentPage + step).coerceIn(0, widgets.lastIndex)
                        settle = scope.launch {
                            pager.animateScrollToPage(target, animationSpec = pageAnimationSpec)
                            widgets.getOrNull(target)?.key?.let { select?.invoke(it) }
                        }
                    }
                }
            }) {
            // Clip only the swiping content. Pagination must neither consume its
            // height nor extend the rounded clipping boundary below the widgets.
            Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(24.dp))
                .testTag("widget_stack_content:${folder.id}")) {
                if (widgets.isEmpty()) {
                    Box(Modifier.fillMaxSize().combinedClickable(
                        enabled = inputEnabled && !preview && !gridManaged,
                        onClick = {}, onLongClick = onLongPress), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.widget_stack_empty), Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    HorizontalPager(pager, Modifier.fillMaxSize(),
                        userScrollEnabled = false, key = { widgets[it].key }) { page ->
                        val layout = widgets[page].widget!!
                        val hosted = rememberHostedWidget(layout)
                        Box(Modifier.fillMaxSize().background(layout.widgetBackgroundColor())) {
                            HomeWidget(layout, hosted, height, editing = false,
                                enabled = inputEnabled, hapticsEnabled = uiState.settings.allowHapticFeedback,
                                onLongPress = onLongPress, editGesturesEnabled = !preview && !gridManaged,
                                contentScale = layout.popupContentScale / 100f,
                                previewOnly = preview)
                            if (preview) Box(Modifier.matchParentSize().pointerInput(Unit) {
                                // Keep the native child out of preview hit testing without consuming
                                // the parent pager's horizontal or tall-preview vertical gestures.
                                awaitPointerEventScope { while (true) awaitPointerEvent() }
                            })
                        }
                    }
                }
            }
            if (widgets.size > 1) {
                Row(Modifier.fillMaxWidth().height(indicatorHeight.dp)
                    .testTag("widget_stack_indicator:${folder.id}"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    val firstDot = (pager.currentPage - 3).coerceIn(0, (widgets.size - 7).coerceAtLeast(0))
                    repeat(widgets.size.coerceAtMost(7)) { index ->
                        val page = firstDot + index
                        Box(Modifier.size(if (page == pager.currentPage) 6.dp else 4.dp)
                            .background(scheme.primary.copy(alpha = if (page == pager.currentPage) 1f else .4f), CircleShape))
                    }
                }
            }
        }
    }
}
