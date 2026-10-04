package com.galaxyrio.gracelauncher.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.LauncherSearchBar
import com.galaxyrio.gracelauncher.ui.theme.LauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance

@Composable
internal fun AppSearchScreen(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onLaunch: (LauncherApp) -> Unit,
    onDetails: (LauncherApp) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backProgress: Float = 0f,
) {
    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    val apps = uiState.apps
    val results = remember(apps, query) {
        val term = query.trim()
        apps.filter { it.label.contains(term, ignoreCase = true) || it.originalLabel.contains(term, ignoreCase = true) }
    }
    val backDistance = with(LocalDensity.current) { 30.dp.toPx() } *
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    Surface(modifier.fillMaxSize().testTag("app_search").graphicsLayer {
        alpha = 1f - backProgress
        translationX = backDistance * backProgress
    }, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
            LauncherSearchBar(
                queryState, stringResource(R.string.search_apps), "app_search_query",
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                autoFocus = true, onBack = onDismiss,
                onSearch = { results.firstOrNull()?.let(onLaunch) },
            )
            CompositionLocalProvider(LocalLauncherAppearance provides LauncherAppearance(
                darkText = MaterialTheme.colorScheme.onSurface.luminance() < 0.5f,
                themedIcons = uiState.themedIcons,
            )) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (results.isEmpty()) item {
                        Text(stringResource(R.string.search_no_results), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(results, key = LauncherApp::key) { app ->
                        LauncherAppRow(
                            app = app,
                            onClick = { onLaunch(app) },
                            onLongClick = { onDetails(app) },
                            gestures = AppRowGestures(onLaunchAt = actions.launchAppAt?.let { launch ->
                                { target, bounds -> onDismiss(); launch(target, bounds) }
                            }),
                        )
                    }
                }
            }
        }
    }
}
