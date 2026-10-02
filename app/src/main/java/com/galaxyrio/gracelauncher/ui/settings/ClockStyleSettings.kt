@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.ClockFaceStyle
import com.galaxyrio.gracelauncher.data.ClockFontFile
import com.galaxyrio.gracelauncher.data.ClockFontStore
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.home.ClockFace
import com.galaxyrio.gracelauncher.ui.home.formatHomeClock
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ClockStyleSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A scalar saved-state draft survives recreation without changing the desktop.
    var encoded by rememberSaveable { mutableStateOf(uiState.settings.clockStyle.encode()) }
    val draft = remember(encoded) { ClockStyle.decode(encoded) }
    val face = draft.face
    fun changeFace(transform: (ClockFaceStyle) -> ClockFaceStyle) { encoded = draft.withFace(transform(face)).encode() }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var fontRevision by remember { mutableIntStateOf(0) }
    val store = remember(context) { ClockFontStore(context) }
    val fonts by produceState(emptyList<ClockFontFile>(), store, fontRevision) { value = store.list() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val font = store.import(uri)
                // Read the current draft after IO; do not overwrite another style's edits.
                val current = ClockStyle.decode(encoded)
                encoded = current.withFace(current.face.copy(fontId = font.id)).encode()
                fontRevision++
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(context, R.string.clock_font_import_error, Toast.LENGTH_LONG).show()
            } finally { importing = false }
        }
    }
    if (dialog == "font") {
        ClockFontDialog(fonts, face.fontId,
            onSelect = { id -> changeFace { it.copy(fontId = id) }; dialog = null },
            onImport = { dialog = null; picker.launch(arrayOf("*/*")) }, onDismiss = { dialog = null })
    }
    val fontLabel = when {
        importing -> stringResource(R.string.clock_font_importing)
        face.fontId == null -> stringResource(R.string.clock_font_default)
        else -> fonts.firstOrNull { it.id == face.fontId }?.name ?: stringResource(R.string.clock_font_unavailable)
    }
    val count = if (draft.layout == ClockLayout.SingleLine) 6 else 5
    val defaults = ClockStyle.defaults(draft.layout)
    SettingsScaffold(stringResource(R.string.settings_clock_style), "settings_clock_style_editor", onBack,
        fixedCollapsed = true,
        actions = {
            Button(
                modifier = Modifier.padding(end = 8.dp).testTag("clock_style_apply"), enabled = !saving && !importing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            if (actions.applyClockStyle(draft)) onBack()
                            else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
                        } finally { saving = false }
                    }
                },
            ) { Text(stringResource(R.string.clock_style_apply)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            ClockStylePreview(draft, uiState, Modifier.padding(top = 8.dp, bottom = 16.dp))
            LazyColumn(
                Modifier.weight(1f).testTag("clock_style_controls"),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                contentPadding = PaddingValues(bottom = 24.dp),
                userScrollEnabled = !saving,
            ) {
                item {
                    ClockLayoutSelector(draft.layout, count, enabled = !saving && !importing) {
                        encoded = draft.copy(layout = it).encode()
                    }
                }
                item {
                    SettingsActionItem(stringResource(R.string.settings_font), fontLabel, 1, count,
                        "clock_font_selector", enabled = !saving && !importing) { dialog = "font" }
                }
                item {
                    ClockSlider(stringResource(R.string.clock_weight), face.weight, defaults.weight,
                        100..if (face.fontId == null) 700 else 900, step = 100,
                        index = 2, count = count, tag = "clock_weight", enabled = !saving && !importing,
                        onChange = { value -> changeFace { it.copy(weight = value) } })
                }
                item {
                    ClockSlider(stringResource(R.string.clock_size), face.size, defaults.size, 32..144, step = 1,
                        index = 3, count = count, tag = "clock_size", enabled = !saving && !importing,
                        onChange = { value -> changeFace { it.copy(size = value) } })
                }
                item {
                    ClockSlider(stringResource(R.string.clock_letter_spacing), face.letterSpacing, defaults.letterSpacing, -8..16, step = 1,
                        index = 4, count = count, tag = "clock_letter_spacing", enabled = !saving && !importing,
                        onChange = { value -> changeFace { it.copy(letterSpacing = value) } })
                }
                if (draft.layout == ClockLayout.SingleLine) item {
                    SettingsToggleItem(stringResource(R.string.clock_show_colon), stringResource(R.string.clock_show_colon_summary),
                        face.showColon, 5, count, "clock_show_colon") { value ->
                        if (!saving && !importing) changeFace { it.copy(showColon = value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockLayoutSelector(selected: ClockLayout, count: Int, enabled: Boolean, onSelect: (ClockLayout) -> Unit) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(0, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.testTag("clock_layout_selector"),
        content = {
            Row(
                Modifier.fillMaxWidth().selectableGroup().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClockLayoutOption(selected, selected = true, enabled = enabled, onSelect = onSelect)
                VerticalDivider(Modifier.height(28.dp), color = MaterialTheme.colorScheme.outline)
                ClockLayout.entries.filter { it != selected }.forEach { layout ->
                    key(layout) { ClockLayoutOption(layout, selected = false, enabled = enabled, onSelect = onSelect) }
                }
            }
        },
    )
}

@Composable
private fun ClockLayoutOption(layout: ClockLayout, selected: Boolean, enabled: Boolean, onSelect: (ClockLayout) -> Unit) {
    val label = layout.label()
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(72.dp).testTag("clock_layout:${layout.name}")
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .selectable(selected, enabled = enabled && LocalSettingsStorageState.current.canEdit,
                role = Role.RadioButton, onClick = { onSelect(layout) })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (layout == ClockLayout.SingleLine) "09:30" else "09\n30",
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = if (layout == ClockLayout.SingleLine) 20.sp else 28.sp,
                lineHeight = 28.sp,
                letterSpacing = (-1).sp,
                fontFeatureSettings = "tnum",
            ),
            textAlign = TextAlign.Center,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClockStylePreview(style: ClockStyle, uiState: LauncherUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons)
    val now by produceState(Instant.now()) {
        while (true) { value = Instant.now(); delay(60_000 - System.currentTimeMillis() % 60_000) }
    }
    val shape = MaterialTheme.shapes.extraLarge
    Box(modifier.fillMaxWidth().testTag("clock_style_preview")
        .height(if (style.layout == ClockLayout.SingleLine) 168.dp else 232.dp)
        .drawWithContent {
            // Clear the window, not an isolated offscreen layer. FLAG_SHOW_WALLPAPER
            // supplies the real (including live) wallpaper without reading its bitmap.
            drawOutline(shape.createOutline(size, layoutDirection, this), Color.Transparent, blendMode = BlendMode.Clear)
            drawContent()
        }, contentAlignment = Alignment.CenterStart) {
        ClockFace(formatHomeClock(now, DateFormat.is24HourFormat(context), locale), style,
            appearance.text, Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)
                .testTag("clock_style_preview_text"), appearance.textShadow)
    }
}

@Composable
private fun ClockSlider(
    title: String, value: Int, default: Int, range: IntRange, step: Int,
    index: Int, count: Int, tag: String, enabled: Boolean, onChange: (Int) -> Unit,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.testTag("${tag}_item"),
        content = {
            Column {
                Text(title)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(value = value.toFloat(), onValueChange = {
                        onChange((range.first + ((it - range.first) / step).roundToInt() * step).coerceIn(range))
                    }, valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = if (step > 1) (range.last - range.first) / step - 1 else 0,
                        enabled = enabled, modifier = Modifier.weight(1f).testTag(tag).semantics { contentDescription = title })
                    IconButton(onClick = { onChange(default) }, enabled = enabled && value != default,
                        modifier = Modifier.size(48.dp).clip(CircleShape).testTag("${tag}_reset")) {
                        Icon(painterResource(R.drawable.ms_restart_alt), stringResource(R.string.clock_reset_value, title))
                    }
                }
            }
        },
    )
}

@Composable
private fun ClockFontDialog(fonts: List<ClockFontFile>, selected: String?, onSelect: (String?) -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.settings_font)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup()) {
                item { FontOption(stringResource(R.string.clock_font_default), null, selected, onSelect) }
                items(fonts, key = { it.id }) { FontOption(it.name, it.id, selected, onSelect) }
            }
        },
        confirmButton = { TextButton(onClick = onImport, modifier = Modifier.testTag("clock_font_import")) { Text(stringResource(R.string.clock_font_import)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun FontOption(label: String, id: String?, selected: String?, onSelect: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("clock_font:${id ?: "default"}")
        .selectable(selected == id, role = Role.RadioButton, onClick = { onSelect(id) }).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        RadioButton(selected == id, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ClockLayout.label(): String = stringResource(if (this == ClockLayout.SingleLine) R.string.clock_single_line else R.string.clock_two_lines)
