package com.galaxyrio.gracelauncher.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.ui.components.AlphabetRail
import com.galaxyrio.gracelauncher.ui.drawer.AppDrawerScreen
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun LauncherRoute(viewModel: LauncherViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshSchedule() }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(60_000)
                viewModel.refreshSchedule()
            }
        }
    }

    val openSystemApp: (Intent) -> Unit = { intent ->
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    LauncherScreen(
        uiState = uiState,
        returnHomeRequests = viewModel.returnHomeRequests,
        onDateClick = {
            if (uiState.scheduleStatus == ScheduleStatus.PermissionRequired) {
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            } else {
                val uri = CalendarContract.CONTENT_URI.buildUpon()
                    .appendPath("time")
                    .appendPath(System.currentTimeMillis().toString())
                    .build()
                openSystemApp(Intent(Intent.ACTION_VIEW, uri))
            }
        },
        onClockClick = { openSystemApp(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        onLaunchApp = { app ->
            if (!viewModel.launch(app)) {
                Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            }
        },
        onToggleFavorite = { app ->
            val added = viewModel.toggleFavorite(app)
            Toast.makeText(
                context,
                if (added) R.string.added_to_favorites else R.string.removed_from_favorites,
                Toast.LENGTH_SHORT,
            ).show()
        },
    )
}

@Composable
internal fun LauncherScreen(
    uiState: LauncherUiState,
    returnHomeRequests: Flow<Unit> = emptyFlow(),
    onDateClick: () -> Unit,
    onClockClick: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onToggleFavorite: (LauncherApp) -> Unit,
    initialDrawerOpen: Boolean = false,
) {
    var drawerOpen by rememberSaveable { mutableStateOf(initialDrawerOpen) }
    val model = remember(uiState.apps) { AppListModel(uiState.apps) }
    val drawerState = rememberLazyListState()
    val visibleSection by remember(model, drawerState) {
        derivedStateOf { model.items.getOrNull(drawerState.firstVisibleItemIndex)?.section }
    }

    LaunchedEffect(returnHomeRequests) {
        returnHomeRequests.collect { drawerOpen = false }
    }
    BackHandler { drawerOpen = false }

    val drawerAlpha = animateFloatAsState(
        targetValue = if (drawerOpen) 1f else 0f,
        animationSpec = tween(110),
        label = "appListFade",
    )
    val scrim = remember {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.16f),
                Color.Black.copy(alpha = 0.18f),
                Color.Black.copy(alpha = 0.24f),
            ),
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(scrim).safeDrawingPadding(),
    ) {
        val homeTop = (maxHeight * 0.15f).coerceIn(24.dp, 136.dp)
        val listTop = (maxHeight * 0.27f).coerceIn(24.dp, 224.dp)
        val railHeight = ((model.letters.size + 1) * 18).dp.coerceAtMost(maxHeight * 0.65f)
        val railTop = (maxHeight * 0.31f).coerceAtMost(maxHeight - railHeight - 20.dp)

        HomeScreen(
            uiState = uiState,
            topSpace = homeTop,
            viewportHeight = maxHeight,
            onOpenDrawer = {
                drawerState.requestScrollToItem(0)
                drawerOpen = true
            },
            onLaunchApp = onLaunchApp,
            onToggleFavorite = onToggleFavorite,
            onDateClick = onDateClick,
            onClockClick = onClockClick,
            modifier = Modifier.retainedPage(visible = !drawerOpen)
                .graphicsLayer { alpha = 1f - drawerAlpha.value },
        )

        // The list is measured ahead of time, and its scroll state survives closing.
        // Only placement is gated; selecting an index never waits for a new list.
        AppDrawerScreen(
            model = model,
            listState = drawerState,
            topSpace = listTop,
            viewportHeight = maxHeight,
            onLaunchApp = onLaunchApp,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier.retainedPage(visible = drawerOpen)
                .graphicsLayer { alpha = drawerAlpha.value },
        )

        AlphabetRail(
            letters = model.letters,
            selectedLetter = if (drawerOpen) visibleSection else null,
            height = railHeight,
            onLetterSelected = { letter ->
                if (letter == null) {
                    drawerOpen = false
                } else {
                    model.headerIndices[letter]?.let { index ->
                        // Coalesces rapid changes in the next measure pass, cancels
                        // an in-flight fling, and never queues scroll coroutines.
                        drawerState.requestScrollToItem(index)
                        drawerOpen = true
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).offset(y = railTop),
        )
    }
}

private fun Modifier.retainedPage(visible: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (visible) placeable.placeRelative(0, 0)
    }
}

private fun previewState(): LauncherUiState {
    val labels = listOf("Clock", "Chrome", "Calendar", "Camera", "Gmail", "Signal", "Discord", "Todoist", "Reddit")
    val apps = labels.map { label ->
        LauncherApp(ComponentName("preview.${label.lowercase()}", "$label.Activity"), label, null)
    }
    return LauncherUiState(
        apps = apps,
        favoriteKeys = apps.drop(4).mapTo(linkedSetOf(), LauncherApp::key),
        isLoadingApps = false,
        events = listOf(
            ScheduleEvent(
                id = 1L,
                title = "Movie night",
                startsAt = Instant.now().plus(Duration.ofMinutes(28)),
                endsAt = Instant.now().plus(Duration.ofHours(3)),
                isAllDay = false,
                location = null,
                calendarColor = null,
            ),
        ),
        scheduleStatus = ScheduleStatus.Ready,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF102430, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    GraceLauncherTheme(dynamicColor = false) {
        LauncherScreen(
            uiState = previewState(),
            onDateClick = {},
            onClockClick = {},
            onLaunchApp = {},
            onToggleFavorite = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF102430, widthDp = 390, heightDp = 844)
@Composable
private fun DrawerPreview() {
    GraceLauncherTheme(dynamicColor = false) {
        LauncherScreen(
            uiState = previewState(),
            onDateClick = {},
            onClockClick = {},
            onLaunchApp = {},
            onToggleFavorite = {},
            initialDrawerOpen = true,
        )
    }
}
