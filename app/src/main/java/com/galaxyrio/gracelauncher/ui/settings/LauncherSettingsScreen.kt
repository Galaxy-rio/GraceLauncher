@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.motion.settingsEnter
import com.galaxyrio.gracelauncher.ui.motion.settingsExit
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal enum class SettingsPage {
    Root, Productivity, Themes, Advanced, About, HiddenApps, Folders, FolderEditor,
    Changelog, Licenses, AppLicense,
}

/** Navigation owns each page's saved state and seekable predictive-back transition. */
@Composable
fun LauncherSettingsScreen(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: String? = null,
    initialFolderId: String? = null,
    handleRootBack: Boolean = true,
) {
    val navController = rememberNavController()
    val startDestination = remember(initialPage, initialFolderId) {
        when {
            initialFolderId != null -> "FolderEditor/${Uri.encode(initialFolderId)}"
            initialPage == "folders" -> SettingsPage.Folders.name
            else -> SettingsPage.Root.name
        }
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    val canPop = currentEntry != null && navController.previousBackStackEntry != null
    val scope = rememberCoroutineScope()
    val rootExit = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val latestOnBack by rememberUpdatedState(onBack)
    val distance = with(LocalDensity.current) { 30.dp.roundToPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val closeRoot: () -> Unit = {
        if (!closing) {
            if (!handleRootBack) latestOnBack() else {
                closing = true
                scope.launch {
                    rootExit.animateTo(1f, tween(300))
                    latestOnBack()
                }
            }
        }
    }

    // Embedded settings reveal the retained desktop. The standalone SettingsActivity
    // leaves its root callback disabled, preserving Android's cross-task animation.
    PredictiveBackHandler(enabled = handleRootBack && !canPop && !closing) { events ->
        try {
            events.collect { rootExit.snapTo(it.progress) }
            closing = true
            rootExit.animateTo(1f, tween(180))
            latestOnBack()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { rootExit.animateTo(0f, tween(180)) }
            closing = false
            throw cancelled
        }
    }

    val storage = SettingsStorageState(
        uiState.isLoadingSettings, uiState.settingsLoadFailed, uiState.settingsSaveFailed,
    )
    CompositionLocalProvider(LocalSettingsStorageState provides storage) {
      NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize().testTag("settings_navigation").graphicsLayer {
            alpha = 1f - rootExit.value
            translationX = distance * direction * rootExit.value
        }.background(MaterialTheme.colorScheme.surfaceContainer),
        enterTransition = { settingsEnter(distance) },
        exitTransition = { settingsExit(distance) },
        popEnterTransition = { settingsEnter(distance, back = true) },
        popExitTransition = { settingsExit(distance, back = true) },
      ) {
        SettingsPage.entries.forEach { page ->
            val isEditor = page == SettingsPage.FolderEditor
            composable(
                route = if (isEditor) "FolderEditor/{folderId}" else page.name,
                arguments = if (isEditor) listOf(navArgument("folderId") { type = NavType.StringType }) else emptyList(),
            ) { entry ->
                // Ignore double taps and clicks on an outgoing/predictively revealed page.
                fun isCurrent() = navController.currentBackStackEntry === entry &&
                    entry.lifecycle.currentState == Lifecycle.State.RESUMED && !closing
                val navigate: (SettingsPage) -> Unit = { destination ->
                    if (isCurrent()) navController.navigate(destination.name) { launchSingleTop = true }
                }
                val back: () -> Unit = {
                    if (isCurrent()) {
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                        else closeRoot()
                    }
                }
                // Never initialize an editable draft before Room's first snapshot.
                if (!storage.canEdit) {
                    SettingsScaffold(stringResource(R.string.settings_title), "settings_storage_blocked", back) { }
                } else when (page) {
                    SettingsPage.Root -> SettingsHome(uiState.isDefaultHome, actions.requestDefaultHome, back, navigate)
                    SettingsPage.Productivity -> ProductivitySettings(uiState, actions, back, navigate)
                    SettingsPage.Themes -> ThemeSettings(uiState, actions, back)
                    SettingsPage.Advanced -> SettingsScaffold(stringResource(R.string.settings_advanced), "settings_advanced", back) { }
                    SettingsPage.About -> AboutSettings(back, navigate)
                    SettingsPage.HiddenApps -> HiddenAppsSettings(uiState, actions, back)
                    SettingsPage.Folders -> FolderSettings(uiState, back) { folderId ->
                        if (isCurrent()) navController.navigate("FolderEditor/${Uri.encode(folderId ?: UUID.randomUUID().toString())}")
                    }
                    SettingsPage.FolderEditor -> FolderEditorSettings(entry.arguments?.getString("folderId"), uiState, actions, back)
                    SettingsPage.Changelog -> ChangelogSettings(back)
                    SettingsPage.Licenses -> LicenseSettings(back, navigate)
                    SettingsPage.AppLicense -> AppLicenseSettings(back)
                }
            }
        }
      }
    }
}

@Composable
private fun SettingsHome(
    isDefaultHome: Boolean?,
    onSetDefaultHome: () -> Unit,
    onBack: () -> Unit,
    navigate: (SettingsPage) -> Unit,
) {
    val showDefaultHomeBanner = isDefaultHome == false
    val categories = listOf(
        SettingsPage.Productivity to LauncherSymbol.Star,
        SettingsPage.Themes to LauncherSymbol.Palette,
        SettingsPage.Advanced to LauncherSymbol.Settings,
        SettingsPage.About to LauncherSymbol.Info,
    )
    SettingsScaffold(stringResource(R.string.settings_title), "settings_root", onBack) { padding ->
        SettingsList(padding) {
            item(key = "settings_intro") { Spacer(Modifier.height(if (showDefaultHomeBanner) 32.dp else 24.dp)) }
            if (showDefaultHomeBanner) {
                item(key = "default_home_banner") { DefaultHomeBanner(onSetDefaultHome) }
                item(key = "default_home_spacing") { Spacer(Modifier.height(32.dp)) }
            }
            categories.forEachIndexed { index, (page, symbol) ->
                item(key = page.name) {
                    val title = when (page) {
                        SettingsPage.Productivity -> R.string.settings_productivity
                        SettingsPage.Themes -> R.string.settings_themes
                        SettingsPage.Advanced -> R.string.settings_advanced
                        else -> R.string.settings_about
                    }
                    val summary = when (page) {
                        SettingsPage.Productivity -> R.string.settings_productivity_summary
                        SettingsPage.Themes -> R.string.settings_themes_summary
                        SettingsPage.Advanced -> R.string.settings_advanced_summary
                        else -> R.string.settings_about_summary
                    }
                    SettingsActionItem(
                        stringResource(title), stringResource(summary), index, categories.size,
                        tag = "settings_category_${page.name.lowercase()}",
                        leading = {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                LauncherIcon(symbol, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                    ) { navigate(page) }
                }
            }
        }
    }
}

@Composable
private fun DefaultHomeBanner(onClick: () -> Unit) {
    // A separate highlighted action, not a checked switch or a fifth category.
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("settings_default_home"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIcon(LauncherSymbol.Home, Modifier.size(24.dp))
            Text(stringResource(R.string.set_default_launcher), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            LauncherIcon(LauncherSymbol.Launch, Modifier.size(24.dp))
        }
    }
}
