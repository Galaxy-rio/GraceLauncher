@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AboutSettings(onBack: () -> Unit, navigate: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    SettingsScaffold(stringResource(R.string.settings_about), "settings_about", onBack) { padding ->
        SettingsList(padding) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AboutAppIcon()
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.settings_about_description), style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                        Text(stringResource(R.string.settings_version, version), Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item { SettingsHeading(stringResource(R.string.settings_about_app)) }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_changelog), stringResource(R.string.settings_changelog_summary),
                    0, 2, "settings_open_changelog", leading = { LauncherIcon(LauncherSymbol.Hourglass) },
                ) { navigate(SettingsPage.Changelog) }
            }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_licenses), stringResource(R.string.settings_licenses_summary),
                    1, 2, "settings_open_licenses", leading = { LauncherIcon(LauncherSymbol.Info) },
                ) { navigate(SettingsPage.Licenses) }
            }
        }
    }
}

@Composable
private fun AboutAppIcon() {
    val size = 92.dp
    val containerSize = size * (LoadingIndicatorDefaults.ContainerWidth.value / LoadingIndicatorDefaults.IndicatorSize.value)
    val shapes = remember { listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.SoftBurst) }
    Box(Modifier.size(containerSize).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        LoadingIndicator(color = MaterialTheme.colorScheme.primaryContainer, polygons = shapes, modifier = Modifier.fillMaxSize())
        Icon(
            painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size),
        )
    }
}

@Composable
internal fun ChangelogSettings(onBack: () -> Unit) {
    val changes = listOf(
        R.string.settings_changelog_home to R.string.settings_changelog_home_summary,
        R.string.settings_changelog_interactions to R.string.settings_changelog_interactions_summary,
        R.string.settings_changelog_settings to R.string.settings_changelog_settings_summary,
    )
    SettingsScaffold(stringResource(R.string.settings_changelog), "settings_changelog", onBack) { padding ->
        SettingsList(padding) {
            item { SettingsHeading(stringResource(R.string.settings_current_build)) }
            changes.forEach { (title, description) ->
                item {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LicenseSettings(onBack: () -> Unit, navigate: (SettingsPage) -> Unit) {
    SettingsScaffold(stringResource(R.string.settings_licenses), "settings_licenses", onBack) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_font_licenses), stringResource(R.string.settings_font_licenses_summary),
                    0, 3, "settings_font_licenses",
                ) { navigate(SettingsPage.FontLicenses) }
            }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_icon_licenses), stringResource(R.string.settings_icon_licenses_summary),
                    1, 3, "settings_icon_licenses",
                ) { navigate(SettingsPage.IconLicenses) }
            }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_framework_licenses), stringResource(R.string.settings_framework_licenses_summary),
                    2, 3, "settings_framework_licenses",
                ) { navigate(SettingsPage.FrameworkLicenses) }
            }
        }
    }
}

@Composable
internal fun LicenseTextSettings(fonts: Boolean, onBack: () -> Unit, titleResource: Int = if (fonts) R.string.settings_font_licenses else R.string.settings_icon_licenses, frameworks: Boolean = false) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val unavailable = stringResource(R.string.settings_license_unavailable)
    val text by produceState<String?>(null, fonts, frameworks, resources) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (fonts) context.assets.open("fonts/NOTICE.txt").bufferedReader().use { it.readText() }
                else resources.openRawResource(if (frameworks) R.raw.settings_framework_licenses else R.raw.settings_material_symbols_license).bufferedReader().use { it.readText() }
            }.getOrDefault(unavailable)
        }
    }
    SettingsScaffold(stringResource(titleResource), "settings_license_text", onBack) { padding ->
        SettingsList(padding) {
            item {
                Text(
                    text ?: stringResource(R.string.settings_license_loading),
                    Modifier.padding(horizontal = 4.dp, vertical = 20.dp), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
