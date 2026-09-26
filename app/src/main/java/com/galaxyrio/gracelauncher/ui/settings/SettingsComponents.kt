@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R

internal data class SettingsStorageState(val loading: Boolean = false, val loadFailed: Boolean = false, val saveFailed: Boolean = false) {
    val canEdit: Boolean get() = !loading && !loadFailed
}

internal val LocalSettingsStorageState = staticCompositionLocalOf { SettingsStorageState() }

@Composable
internal fun SettingsScaffold(
    title: String,
    tag: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val storage = LocalSettingsStorageState.current
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(tag).nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, Modifier.padding(start = 4.dp)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(painterResource(R.drawable.ms_arrow_back), stringResource(R.string.settings_back))
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            val message = when {
                storage.loading -> R.string.settings_storage_loading
                storage.loadFailed -> R.string.settings_storage_load_error
                storage.saveFailed -> R.string.settings_storage_save_error
                else -> null
            }
            if (message != null) Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (storage.loading) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (storage.loading) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(stringResource(message), Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).testTag("settings_storage_status"), style = MaterialTheme.typography.bodyMedium)
            }
        },
        content = content,
    )
}

@Composable
internal fun SettingsList(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp).testTag("settings_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content,
    )
}

@Composable
internal fun SettingsHeading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun SettingsActionItem(
    title: String,
    summary: String?,
    index: Int,
    count: Int,
    tag: String,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled && LocalSettingsStorageState.current.canEdit,
        modifier = Modifier.testTag(tag),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        leadingContent = leading,
        content = { Text(title) },
        supportingContent = if (summary == null) null else ({ Text(summary) }),
    )
}

@Composable
internal fun SettingsToggleItem(
    title: String,
    summary: String,
    checked: Boolean,
    index: Int,
    count: Int,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = LocalSettingsStorageState.current.canEdit,
        modifier = Modifier.testTag(tag),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        content = { Text(title) },
        supportingContent = { Text(summary) },
        // The whole segmented item is the sole toggle and accessibility target.
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
