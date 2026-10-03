package com.galaxyrio.gracelauncher.ui.overlays

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.sectionForLabel
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.ItemIconStore
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.AlphabetRail
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSearchBar
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.settings.SettingsScaffold
import com.galaxyrio.gracelauncher.ui.settings.SettingsActionItem
import com.galaxyrio.gracelauncher.ui.settings.SettingsHeading
import com.galaxyrio.gracelauncher.ui.settings.SettingsList
import kotlinx.coroutines.launch

/** Per-item choices, like the launcher's global pack picker, never execute icon-pack code. */
@Composable
internal fun IconEditorScreen(app: LauncherApp, uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { IconPackRepository(context) }
    val iconStore = remember(context) { ItemIconStore(context, repository) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pack by rememberSaveable(app.key) { mutableStateOf<String?>(null) }
    val query = rememberTextFieldState()
    val names by produceState<List<String>?>(null, pack) {
        value = null
        if (pack != null) value = repository.iconNames(pack!!)
    }
    val original by produceState(app, app.key) { value = iconStore.apply(app, ItemIcon.System) }
    val back = { if (!busy) { if (pack != null) { pack = null; query.edit { replace(0, length, "") } } else onBack() }; Unit }
    BackHandler(onBack = back)
    fun save(operation: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (operation()) onBack()
                else Toast.makeText(context, R.string.icon_edit_failed, Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) save { actions.importItemIcon(app, uri) }
    }
    val selectedPack = uiState.iconPacks.firstOrNull { it.packageName == pack }
    SettingsScaffold(selectedPack?.label ?: stringResource(R.string.edit_icon), "icon_editor", back, fixedCollapsed = true) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (pack == null) SettingsList(PaddingValues(0.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(app, size = 72.dp)
                        Text(app.label, style = MaterialTheme.typography.titleLarge)
                    }
                }
                item {
                    SettingsActionItem(stringResource(R.string.icon_edit_default), null, 0, 3, "icon_edit_default",
                        enabled = !busy, leading = { AppIcon(original, size = 40.dp) }) { save { actions.setItemIcon(app, ItemIcon.System) } }
                }
                item {
                    SettingsActionItem(stringResource(R.string.icon_edit_follow_theme), null, 1, 3, "icon_edit_theme",
                        enabled = !busy, leading = {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { LauncherIcon(LauncherSymbol.Palette) }
                        }) { save { actions.setItemIcon(app, null) } }
                }
                item {
                    SettingsActionItem(stringResource(R.string.icon_edit_image), null, 2, 3, "icon_edit_image",
                        enabled = !busy, leading = {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { LauncherIcon(LauncherSymbol.Plus) }
                        }) { imagePicker.launch("image/*") }
                }
                item { SettingsHeading(stringResource(R.string.settings_icon_pack)) }
                if (uiState.iconPacks.isEmpty()) item {
                    Text(stringResource(R.string.icon_pack_empty), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                itemsIndexed(uiState.iconPacks, key = { _, item -> item.packageName }) { index, item ->
                    SettingsActionItem(item.label, null, index, uiState.iconPacks.size, "icon_edit_pack:${item.packageName}",
                        enabled = !busy, leading = {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                if (item.icon != null) Image(item.icon, null, Modifier.fillMaxSize()) else LauncherIcon(LauncherSymbol.Palette)
                            }
                        }) { pack = item.packageName }
                }
            } else {
                LauncherSearchBar(query, stringResource(R.string.icon_edit_search), "icon_search", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                val available = names
                if (available == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else key(pack) {
                    IconPackGrid(pack!!, available, query.text.toString(), repository, !busy,
                        onSelect = { choice -> save { actions.setItemIcon(app, choice) } }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun IconPackGrid(
    packageName: String,
    names: List<String>,
    query: String,
    repository: IconPackRepository,
    enabled: Boolean,
    onSelect: (ItemIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(names) {
        names.groupBy(::sectionForLabel)
            .toSortedMap(compareBy<String> { it == "#" }.thenBy { it })
            .mapValues { (_, icons) -> icons.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    }
    val term = query.trim().replace('_', ' ')
    val sections = remember(grouped, term) {
        grouped.mapValues { (_, icons) -> if (term.isEmpty()) icons else icons.filter { it.replace('_', ' ').contains(term, true) } }
            .filterValues { it.isNotEmpty() }
    }
    val letters = remember(sections) { sections.keys.toList() }
    // Include full-span headings in the index, so jumps remain correct at every grid width.
    val sectionIndices = remember(sections) {
        buildMap {
            var index = 0
            sections.forEach { (letter, icons) -> put(letter, index); index += icons.size + 1 }
        }
    }
    val grid = rememberLazyGridState()
    val selectedLetter by remember(grid, sectionIndices) {
        derivedStateOf { sectionIndices.entries.lastOrNull { it.value <= grid.firstVisibleItemIndex }?.key }
    }
    LaunchedEffect(sections) { grid.scrollToItem(0) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (sections.isEmpty()) {
            Text(stringResource(R.string.icon_edit_no_results), Modifier.padding(24.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp), state = grid,
                modifier = Modifier.fillMaxSize().padding(end = 48.dp).testTag("icon_pack_grid"),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sections.forEach { (letter, icons) ->
                    item(key = "section:$letter", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                        Text(letter, Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    items(icons, key = { "icon:$it" }, contentType = { "icon" }) { name ->
                        val choice = ItemIcon("pack", packageName, name)
                        val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, choice) {
                            value = repository.selectedIcon(choice)?.bitmap
                        }
                        Column(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = enabled && icon != null, role = Role.Button) { onSelect(choice) }
                                .semantics { contentDescription = name.replace('_', ' ') }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
                            Text(name.replace('_', ' '), Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            AlphabetRail(
                letters = letters, selectedLetter = selectedLetter,
                height = minOf(maxHeight, 20.dp * letters.size),
                onLetterSelected = { letter -> sectionIndices[letter]?.let { grid.requestScrollToItem(it) } },
                modifier = Modifier.align(Alignment.CenterEnd), includeHome = false, onWallpaper = false,
            )
        }
    }
}
