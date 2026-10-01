package com.galaxyrio.gracelauncher.ui

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import androidx.core.content.ContextCompat
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.ui.components.AlphabetRail
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LocalLauncherInputEnabled
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.components.statusBarContentFade
import com.galaxyrio.gracelauncher.platform.AppLaunchTransition
import com.galaxyrio.gracelauncher.platform.DefaultHome
import com.galaxyrio.gracelauncher.platform.ClockLauncher
import com.galaxyrio.gracelauncher.platform.ClockLaunchResult
import com.galaxyrio.gracelauncher.data.media.MediaAccess
import com.galaxyrio.gracelauncher.data.weather.BreezyWeatherRepository
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutRevealState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.drawer.AppDrawerScreen
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlay
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlays
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherHaptics
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun LauncherRoute(
    viewModel: LauncherViewModel,
    settingsOnly: Boolean = false,
    onCloseSettings: () -> Unit = {},
) {
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
    val mediaAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshApps()
    }
    val breezyRepository = remember(context) { BreezyWeatherRepository(context) }
    var weatherPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val weatherPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshWeather()
    }
    LaunchedEffect(uiState.settingsSaveFailed, uiState.settingsLoadFailed) {
        val error = when {
            uiState.settingsLoadFailed -> R.string.settings_storage_load_error
            uiState.settingsSaveFailed -> R.string.settings_storage_save_error
            else -> null
        }
        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(60_000)
                viewModel.refreshSchedule()
                viewModel.refreshWeather(force = false)
            }
        }
    }

    val openSystemApp: (Intent) -> Unit = { intent ->
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    val actions = LauncherActions(
        refreshWeather = { viewModel.refreshWeather() },
        requestWeatherAccess = {
            when {
                breezyRepository.installedPackage() == null -> viewModel.refreshWeather()
                ContextCompat.checkSelfPermission(context, BreezyWeatherRepository.READ_PERMISSION) == PackageManager.PERMISSION_GRANTED ->
                    viewModel.refreshWeather()
                weatherPermissionRequested && (context as? Activity)?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, BreezyWeatherRepository.READ_PERMISSION)
                } != true -> openSystemApp(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)))
                else -> {
                    weatherPermissionRequested = true
                    weatherPermissionLauncher.launch(BreezyWeatherRepository.READ_PERMISSION)
                }
            }
        },
        openBreezyWeather = {
            val intent = context.packageManager.getLaunchIntentForPackage(BreezyWeatherRepository.PACKAGE_NAME)
            if (intent != null) openSystemApp(intent)
            else Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        },
        installBreezyWeather = {
            openSystemApp(Intent(Intent.ACTION_VIEW, "https://github.com/breezy-weather/breezy-weather/releases".toUri()))
        },
        dismissMedia = viewModel::dismissMedia,
        dismissNotification = viewModel::dismissNotification,
        openNotification = { key, revision ->
            viewModel.openNotification(key, revision).also { opened ->
                if (!opened) Toast.makeText(context, R.string.notification_unavailable, Toast.LENGTH_SHORT).show()
            }
        },
        requestMediaAccess = {
            runCatching { mediaAccessLauncher.launch(MediaAccess.settingsIntent(context)) }
                .onFailure { openSystemApp(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        },
        controlMedia = { sessionId, command ->
            if (!viewModel.controlMedia(sessionId, command)) {
                Toast.makeText(context, R.string.media_control_unavailable, Toast.LENGTH_SHORT).show()
            }
        },
        requestDefaultHome = {
            runCatching { homeRoleLauncher.launch(DefaultHome.requestIntent(context)) }
                .onFailure { openSystemApp(Intent(Settings.ACTION_HOME_SETTINGS)) }
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
        refreshIconPacks = viewModel::refreshApps,
        refreshApps = viewModel::refreshApps,
        updateSettings = { change ->
            val enableCalendar = change(uiState.settings).calendarAgenda && !uiState.settings.calendarAgenda
            viewModel.updateSettings(change)
            if (enableCalendar) {
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        },
        setHiddenApps = viewModel::setHiddenApps,
        saveFolder = viewModel::saveFolder,
        deleteFolder = viewModel::deleteFolder,
        shortcuts = viewModel::loadShortcuts,
        cachedShortcuts = viewModel::cachedShortcuts,
        prepareShortcuts = viewModel::prepareShortcuts,
        reorderFavorites = viewModel::reorderFavorites,
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
    )
    if (settingsOnly) {
        // Do not register a root back callback here: Android owns the predictive
        // cross-task/back-to-home animation of this regular settings Activity.
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowInsetsControllerCompat(window, launchView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        LauncherSettingsScreen(uiState, actions, onCloseSettings, handleRootBack = false)
    } else {
        LauncherScreen(
            uiState = uiState,
            returnHomeRequests = viewModel.returnHomeRequests,
            onDateClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
            onClockClick = {
                when (ClockLauncher.open(context, uiState.settings.clockAppKey)) {
                    ClockLaunchResult.Opened -> Unit
                    ClockLaunchResult.NoHandler -> {
                        Toast.makeText(context,
                            if (uiState.settings.clockAppKey == null) R.string.clock_default_unavailable else R.string.clock_app_unavailable,
                            Toast.LENGTH_LONG).show()
                    }
                    ClockLaunchResult.Failed -> Toast.makeText(context, R.string.clock_app_unavailable, Toast.LENGTH_LONG).show()
                }
            },
            onLaunchApp = { app ->
                if (!viewModel.launch(app)) Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            },
            onToggleFavorite = viewModel::toggleFavorite,
            actions = actions,
        )
    }
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
    val visibleApps = uiState.visibleApps
    val model = remember(visibleApps, uiState.folders) { AppListModel(visibleApps, uiState.folders) }
    val drawerState = rememberLazyListState()
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons)
    val view = LocalView.current
    val context = LocalContext.current
    val haptics = rememberLauncherHaptics(uiState.settings.allowHapticFeedback)
    val fullScreen = overlay == LauncherOverlay.Settings || overlay == LauncherOverlay.Search || overlay is LauncherOverlay.FolderSettings
    val backProgress = remember { Animatable(0f) }
    val darkSystemIcons = if (fullScreen) MaterialTheme.colorScheme.surface.luminance() > 0.5f else appearance.darkText
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = darkSystemIcons
                isAppearanceLightNavigationBars = darkSystemIcons
            }
        }
    }

    LaunchedEffect(returnHomeRequests) {
        returnHomeRequests.collect { drawerOpen = false; selectedLetter = null; overlay = null }
    }
    LaunchedEffect(model.letters) {
        if (selectedLetter !in model.letters) selectedLetter = null
    }
    BackHandler(enabled = overlay != LauncherOverlay.Search && !(overlay == null && drawerOpen)) {
        when {
            overlay != null -> overlay = null
            selectedLetter != null -> selectedLetter = null
            else -> drawerOpen = false
        }
    }
    PredictiveBackHandler(enabled = overlay == LauncherOverlay.Search || (overlay == null && drawerOpen)) { events ->
        val fromSearch = overlay == LauncherOverlay.Search
        try {
            events.collect { backProgress.snapTo(it.progress) }
            backProgress.animateTo(1f, tween(180))
            if (fromSearch) overlay = null else drawerOpen = false
            selectedLetter = null
            backProgress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { backProgress.animateTo(0f, tween(180)) }
            throw cancelled
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
        // The list is already positioned. Only remove the temporary mask;
        // scrolling here would cause a jump between the held and released views.
        selectedLetter = null
    }
    val rowGestures = AppRowGestures(
        onPrepare = actions.prepareShortcuts,
        onLaunchAt = actions.launchAppAt,
        onDrag = { app, bounds, expanded ->
            val current = overlay as? LauncherOverlay.Shortcuts
            if (current?.app?.key == app.key) {
                current.reveal.expanded = expanded
            } else {
                overlay = LauncherOverlay.Shortcuts(app, bounds, ShortcutRevealState(expanded, dragging = true))
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
    val openFolder: (LauncherFolder, Rect) -> Unit = { folder, bounds -> overlay = LauncherOverlay.Folder(folder, bounds) }
    val editFolder: (LauncherFolder) -> Unit = { overlay = LauncherOverlay.FolderSettings(it.id) }
    val dragFolder: (LauncherFolder, Rect, Boolean) -> Unit = { folder, bounds, expanded ->
        val current = overlay as? LauncherOverlay.Folder
        if (current?.folder?.id == folder.id) current.reveal.expanded = expanded
        else overlay = LauncherOverlay.Folder(folder, bounds, ShortcutRevealState(expanded, dragging = true))
    }
    val endFolderDrag: (Boolean) -> Unit = { commit ->
        (overlay as? LauncherOverlay.Folder)?.reveal?.let { it.expanded = commit; it.dragging = false }
    }
    val popupReveal = when (val current = overlay) {
        is LauncherOverlay.Shortcuts -> current.reveal
        is LauncherOverlay.Folder -> current.reveal
        else -> null
    }
    // Once a predictive pop commits, keep HOME fully visible instead of fading
    // it in a second time while the old drawer's exit alpha finishes settling.
    val drawerVisibility = if (drawerOpen) {
        drawerAlpha.value * if (overlay == null) (1f - backProgress.value) else 1f
    } else 0f
    CompositionLocalProvider(LocalLauncherAppearance provides appearance, LocalHapticFeedback provides haptics) {
      CompositionLocalProvider(LocalLauncherInputEnabled provides (!fullScreen && backProgress.value == 0f)) {
      BoxWithConstraints(
        // Settings can reveal this retained page during a predictive root back.
        // It remains non-interactive and absent from accessibility while covered.
        modifier = Modifier.fillMaxSize()
            .retainedPage(visible = !fullScreen || overlay == LauncherOverlay.Settings || overlay is LauncherOverlay.FolderSettings || backProgress.value > 0f)
            .then(if (fullScreen || backProgress.value > 0f) Modifier.clearAndSetSemantics {} else Modifier)
            .background(scrim)
            // The lists extend behind the status bar. Insets belong to their
            // scrollable content, not a parent that clips the whole viewport.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .then(if (popupReveal?.dragging == false) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        val statusBarHeight = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val safeHeight = (maxHeight - statusBarHeight).coerceAtLeast(0.dp)
        val regularHomeTop = (safeHeight * if (safeHeight < 600.dp) 0.12f else 0.32f).coerceIn(24.dp, 320.dp)
        // Make room above the favorites for the transparent now-playing row.
        val homeTop = statusBarHeight + (regularHomeTop - if (uiState.homeMedia != null) 128.dp else 0.dp).coerceAtLeast(24.dp)
        val drawerTop = statusBarHeight + safeHeight * 0.28f
        val railHeight = ((model.letters.size + 1) * 18).dp.coerceAtMost(safeHeight * 0.65f)
        val railTop = statusBarHeight + (safeHeight * 0.39f).coerceAtMost(safeHeight - railHeight - 72.dp).coerceAtLeast(0.dp)

        HomeScreen(
            uiState = uiState,
            topSpace = homeTop,
            onLaunchApp = onLaunchApp,
            onAppDetails = { overlay = LauncherOverlay.AppDetails(it) },
            onAppShortcuts = { app, bounds -> overlay = LauncherOverlay.Shortcuts(app, bounds) },
            onDateClick = {
                actions.refreshWeather()
                actions.refreshAgenda()
                overlay = LauncherOverlay.Agenda
            },
            onClockClick = onClockClick,
            rowGestures = rowGestures,
            highlightedAppKey = highlightedAppKey,
            onOpenFolder = openFolder,
            onEditFolder = editFolder,
            onFolderDrag = dragFolder,
            onFolderDragEnd = endFolderDrag,
            onMediaCommand = actions.controlMedia,
            onDismissMedia = actions.dismissMedia,
            modifier = Modifier.retainedPage(visible = !drawerOpen || (overlay == null && backProgress.value > 0f))
                .graphicsLayer { alpha = 1f - drawerVisibility }.statusBarContentFade(statusBarHeight),
        )

        // One complete list and one scroll state for both held and released
        // views. Content padding anchors headings without reserving a viewport.
        AppDrawerScreen(
            model = model,
            notifications = uiState.notifications,
            listState = drawerState,
            selectedLetter = selectedLetter,
            topSpace = drawerTop,
            onLaunchApp = onLaunchApp,
            onAppDetails = { overlay = LauncherOverlay.AppDetails(it) },
            onAppShortcuts = { app, bounds -> overlay = LauncherOverlay.Shortcuts(app, bounds) },
            rowGestures = rowGestures,
            highlightedAppKey = highlightedAppKey,
            onOpenFolder = openFolder,
            onEditFolder = editFolder,
            onFolderDrag = dragFolder,
            onFolderDragEnd = endFolderDrag,
            modifier = Modifier.retainedPage(visible = drawerOpen)
                .graphicsLayer { alpha = drawerVisibility }.statusBarContentFade(statusBarHeight),
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
                    // Offset zero uses the list's leading content padding.
                    // LazyColumn clamps this request when the remaining apps
                    // cannot fill the viewport; do not append phantom space.
                    drawerState.requestScrollToItem(model.indexOfSection(letter))
                    drawerOpen = true
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).offset(y = railTop),
            onScrubFinished = finishScrubbing,
        )
        if (!drawerOpen) {
            val fabDescription = stringResource(R.string.launcher_fab_description)
            val settingsLabel = stringResource(R.string.grace_settings)
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = LauncherLayout.End, bottom = 22.dp).size(54.dp)
                    .testTag("launcher_fab_surface"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
            ) {
                // Surface owns the shadow outside its outline. Only the inner
                // hit target/ripple is clipped; an outer clip erases elevation.
                Box(Modifier.fillMaxSize().testTag("launcher_fab").clip(CircleShape).combinedClickable(
                        enabled = !fullScreen,
                        interactionSource = remember { MutableInteractionSource() }, indication = ripple(),
                        role = Role.Button, onLongClickLabel = settingsLabel,
                        onClick = { overlay = LauncherOverlay.Search },
                        onLongClick = { overlay = LauncherOverlay.Settings },
                    ).semantics { contentDescription = fabDescription }, contentAlignment = Alignment.Center) {
                    // Use the app's real foreground path, without its adaptive background.
                    Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(48.dp))
                }
            }
        }
      }
      }
      LauncherOverlays(
          overlay = overlay, uiState = uiState, actions = actions, onChange = { overlay = it },
          onLaunchApp = onLaunchApp, onToggleFavorite = onToggleFavorite, onRequestCalendar = onDateClick,
          searchBackProgress = backProgress.value,
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
