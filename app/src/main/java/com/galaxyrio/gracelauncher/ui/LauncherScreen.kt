package com.galaxyrio.gracelauncher.ui

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ContentUris
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.ui.components.AlphabetRail
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.platform.AppLaunchTransition
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutRevealState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.drawer.AppDrawerScreen
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlay
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlays
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun LauncherRoute(viewModel: LauncherViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launchView = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshSchedule() }
    val homeRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshApps()
    }

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
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        },
        onClockClick = { openSystemApp(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        onLaunchApp = { app ->
            if (!viewModel.launch(app)) {
                Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            }
        },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        actions = LauncherActions(
            requestDefaultHome = {
                val roleManager = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
                if (Build.VERSION.SDK_INT >= 29 && roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    homeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                } else openSystemApp(Intent(Settings.ACTION_HOME_SETTINGS))
            },
            appInfo = { openSystemApp(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.packageName, null))) },
            screenTime = { app ->
                if (Build.VERSION.SDK_INT >= 29) {
                    openSystemApp(Intent(Settings.ACTION_APP_USAGE_SETTINGS).putExtra(Intent.EXTRA_PACKAGE_NAME, app.packageName))
                } else Toast.makeText(context, R.string.action_unavailable, Toast.LENGTH_SHORT).show()
            },
            uninstall = { openSystemApp(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", it.packageName, null))) },
            rename = viewModel::renameApp,
            categorize = viewModel::categorize,
            storePage = { openSystemApp(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${it.packageName}".toUri())) },
            newEvent = {
                openSystemApp(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 3_600_000))
            },
            openEvent = {
                openSystemApp(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it.id))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.startsAt.toEpochMilli())
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.endsAt.toEpochMilli()))
            },
            refreshAgenda = viewModel::refreshSchedule,
            textMode = viewModel::setTextMode,
            themedIcons = viewModel::setThemedIcons,
            shortcuts = viewModel::loadShortcuts,
            cachedShortcuts = viewModel::cachedShortcuts,
            prepareShortcuts = viewModel::prepareShortcuts,
            launchAppAt = { app, bounds ->
                val transition = AppLaunchTransition.fromIcon(launchView, bounds.toAndroidRect())
                if (!viewModel.launch(app, transition?.sourceBounds, transition?.options)) {
                    Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
                }
            },
            launchShortcutAt = { shortcut, bounds ->
                val transition = AppLaunchTransition.fromIcon(launchView, bounds.toAndroidRect())
                if (!viewModel.launchShortcut(shortcut, transition?.sourceBounds, transition?.options)) {
                    Toast.makeText(context, R.string.shortcut_error, Toast.LENGTH_SHORT).show()
                }
            },
            launchShortcut = {
                if (!viewModel.launchShortcut(it)) Toast.makeText(context, R.string.shortcut_error, Toast.LENGTH_SHORT).show()
            },
        ),
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
    actions: LauncherActions = LauncherActions(),
    initialDrawerOpen: Boolean = false,
) {
    var drawerOpen by rememberSaveable { mutableStateOf(initialDrawerOpen) }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf<LauncherOverlay?>(null) }
    val model = remember(uiState.apps) { AppListModel(uiState.apps) }
    val drawerState = rememberLazyListState()
    val sectionState = rememberLazyListState()
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons)
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = appearance.darkText
                isAppearanceLightNavigationBars = appearance.darkText
            }
        }
    }

    LaunchedEffect(returnHomeRequests) {
        returnHomeRequests.collect { drawerOpen = false; selectedLetter = null; overlay = null }
    }
    LaunchedEffect(model.letters) {
        if (selectedLetter !in model.letters) selectedLetter = null
    }
    BackHandler {
        when {
            overlay != null -> overlay = null
            selectedLetter != null -> selectedLetter = null
            else -> drawerOpen = false
        }
    }

    val drawerAlpha = animateFloatAsState(
        targetValue = if (drawerOpen) 1f else 0f,
        animationSpec = tween(110),
        label = "appListFade",
    )
    val scrim = remember(appearance.darkText) {
        val opacity = if (appearance.darkText) 0f else 0.08f
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = opacity),
                Color.Black.copy(alpha = opacity * 0.6f),
                Color.Black.copy(alpha = opacity),
            ),
        )
    }

    val finishScrubbing: () -> Unit = {
        selectedLetter?.let { letter ->
            drawerState.requestScrollToItem(model.indexOfSection(letter))
        }
        selectedLetter = null
    }
    val rowGestures = AppRowGestures(
        onPrepare = actions.prepareShortcuts,
        onLaunchAt = actions.launchAppAt,
        onDrag = { app, bounds, progress ->
            val current = overlay as? LauncherOverlay.Shortcuts
            if (current?.app?.key == app.key) {
                current.reveal.progress = progress
            } else {
                overlay = LauncherOverlay.Shortcuts(app, bounds, ShortcutRevealState(progress, dragging = true))
            }
        },
        onDragEnd = { commit ->
            (overlay as? LauncherOverlay.Shortcuts)?.reveal?.let {
                it.expanded = commit
                it.dragging = false
            }
        },
    )
    val highlightedAppKey = (overlay as? LauncherOverlay.AppDetails)?.app?.key
    CompositionLocalProvider(LocalLauncherAppearance provides appearance) {
      BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(scrim).safeDrawingPadding()
            .then(if ((overlay as? LauncherOverlay.Shortcuts)?.reveal?.dragging == false) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        val homeTop = (maxHeight * if (maxHeight < 600.dp) 0.12f else 0.32f).coerceIn(24.dp, 320.dp)
        val railHeight = ((model.letters.size + 1) * 18).dp.coerceAtMost(maxHeight * 0.65f)
        val railTop = (maxHeight * 0.39f).coerceAtMost(maxHeight - railHeight - 72.dp).coerceAtLeast(0.dp)

        HomeScreen(
            uiState = uiState,
            topSpace = homeTop,
            viewportHeight = maxHeight,
            onLaunchApp = onLaunchApp,
            onAppDetails = { overlay = LauncherOverlay.AppDetails(it) },
            onAppShortcuts = { app, bounds -> overlay = LauncherOverlay.Shortcuts(app, bounds) },
            onDateClick = { overlay = LauncherOverlay.Agenda },
            onClockClick = onClockClick,
            rowGestures = rowGestures,
            highlightedAppKey = highlightedAppKey,
            modifier = Modifier.retainedPage(visible = !drawerOpen)
                .graphicsLayer { alpha = 1f - drawerAlpha.value },
        )

        // A held index temporarily isolates one group; releasing restores the
        // continuous full list at that group's header, not a separate page.
        AppDrawerScreen(
            model = model,
            listState = if (selectedLetter == null) drawerState else sectionState,
            selectedLetter = selectedLetter,
            onLaunchApp = onLaunchApp,
            onAppDetails = { overlay = LauncherOverlay.AppDetails(it) },
            onAppShortcuts = { app, bounds -> overlay = LauncherOverlay.Shortcuts(app, bounds) },
            rowGestures = rowGestures,
            highlightedAppKey = highlightedAppKey,
            modifier = Modifier.retainedPage(visible = drawerOpen)
                .graphicsLayer { alpha = drawerAlpha.value },
        )

        AlphabetRail(
            letters = model.letters,
            selectedLetter = if (drawerOpen) selectedLetter else null,
            height = railHeight,
            onLetterSelected = { letter ->
                if (letter == null) {
                    drawerOpen = false
                    selectedLetter = null
                } else {
                    selectedLetter = letter
                    sectionState.requestScrollToItem(0)
                    drawerOpen = true
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).offset(y = railTop),
            onScrubFinished = finishScrubbing,
        )
        if (!drawerOpen) {
            val settingsDescription = stringResource(R.string.grace_settings)
            FloatingActionButton(
                onClick = { overlay = LauncherOverlay.Settings },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 22.dp).size(54.dp)
                    .testTag("launcher_settings").semantics { contentDescription = settingsDescription },
                shape = CircleShape, containerColor = Color(0xFF9087D3), contentColor = Color.White,
            ) {
                LauncherIcon(LauncherSymbol.Settings, Modifier.size(25.dp))
            }
        }
      }
      LauncherOverlays(
          overlay = overlay, uiState = uiState, actions = actions, onChange = { overlay = it },
          onLaunchApp = onLaunchApp, onToggleFavorite = onToggleFavorite, onRequestCalendar = onDateClick,
      )
    }
}

private fun Rect.toAndroidRect() = android.graphics.Rect(
    left.toInt(), top.toInt(), right.toInt(), bottom.toInt(),
)

private fun Modifier.retainedPage(visible: Boolean): Modifier =
    // A premeasured page must not leak hidden apps into TalkBack's tree.
    then(if (visible) Modifier else Modifier.clearAndSetSemantics {}).layout { measurable, constraints ->
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
