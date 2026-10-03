@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.data.icons.iconShapePath
import kotlin.math.roundToInt

internal fun LazyListScope.iconDesignerControls(
    all: Boolean, enabled: Boolean, appLabel: String?, sourceLabel: String, design: IconDesign,
    layered: Boolean, dynamicColors: Pair<Int, Int>, onSwitch: () -> Unit, onSource: () -> Unit,
    onColor: (Boolean) -> Unit, onChange: (IconDesign) -> Unit,
) {
    val cookie = design.shape == IconShape.Cookie
    val count = (if (all) 5 else 6) + if (cookie) 1 else 0
    var index = 0
    if (!all) {
        val position = index++
        item("switch") { SettingsActionItem(stringResource(R.string.icon_designer_switch_app), appLabel,
            position, count, "icon_designer_switch", onClick = onSwitch) }
    }
    val sourceIndex = index++
    item("source") { SettingsActionItem(stringResource(R.string.icon_designer_source), sourceLabel,
        sourceIndex, count, "icon_designer_source", enabled = enabled, onClick = onSource) }
    val shapeIndex = index++
    item("shape") {
        DesignerSegment(shapeIndex, count, "icon_designer_shape") {
            Text(stringResource(R.string.icon_designer_shape))
            LazyRow(Modifier.fillMaxWidth().selectableGroup().testTag("icon_designer_shapes").padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IconShape.entries, key = { it.name }) { shape ->
                    val label = shape.label()
                    val selected = shape == design.shape
                    val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.width(if (shape == IconShape.Cookie) 104.dp else 76.dp).clip(MaterialTheme.shapes.large)
                        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = { onChange(design.copy(shape = shape)) })
                        .testTag("icon_designer_shape:${shape.name}").semantics { contentDescription = label }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                            Canvas(Modifier.size(48.dp).padding(6.dp)) {
                                val path = iconShapePath(shape, design.cookieSides, size.minDimension).asComposePath()
                                if (shape == IconShape.None) drawPath(path, color,
                                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                                else drawPath(path, color)
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
    if (cookie) {
        val cookieIndex = index++
        item("cookie") {
            DesignerSegment(cookieIndex, count, "icon_designer_cookie") {
                Text(stringResource(R.string.icon_designer_cookie_sides, design.cookieSides))
                Slider(value = IconDesign.CookieSides.indexOf(design.cookieSides).coerceAtLeast(0).toFloat(),
                    onValueChange = { onChange(design.copy(cookieSides = IconDesign.CookieSides[it.roundToInt().coerceIn(0, 4)])) },
                    valueRange = 0f..4f, steps = 3, enabled = enabled, modifier = Modifier.testTag("icon_designer_cookie_slider"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconDesign.CookieSides.forEach { Text(it.toString(), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
    val colorIndex = index++
    item("colors") {
        DesignerSegment(colorIndex, count, "icon_designer_colors") {
            Text(stringResource(R.string.icon_designer_color))
            if (all) Text(stringResource(R.string.icon_designer_color_mixed), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            DesignerColorRow(stringResource(if (all) R.string.icon_designer_tray_gradient else if (layered)
                R.string.icon_designer_tray_color else R.string.icon_designer_gradient_start), design.background,
                dynamicColors.first, enabled, "icon_designer_background", { onColor(true) }) { onChange(design.copy(background = null)) }
            DesignerColorRow(stringResource(if (all) R.string.icon_designer_symbol_gradient else if (layered)
                R.string.icon_designer_symbol_color else R.string.icon_designer_gradient_end), design.foreground,
                dynamicColors.second, enabled, "icon_designer_foreground", { onColor(false) }) { onChange(design.copy(foreground = null)) }
        }
    }
    val positionIndex = index++
    item("position") {
        DesignerSegment(positionIndex, count, "icon_designer_position") {
            Text(stringResource(R.string.icon_designer_size_position))
            DesignerSlider(stringResource(R.string.icon_designer_x), design.x, 0f, -50f..50f,
                enabled, "icon_designer_x") { onChange(design.copy(x = it)) }
            DesignerSlider(stringResource(R.string.icon_designer_y), design.y, 0f, -50f..50f,
                enabled, "icon_designer_y") { onChange(design.copy(y = it)) }
            DesignerSlider(stringResource(R.string.icon_designer_size), design.size.toFloat(), 100f, 25f..200f,
                enabled, "icon_designer_size") { onChange(design.copy(size = it.roundToInt())) }
        }
    }
    item("effects") {
        SegmentedListItem(shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            modifier = Modifier.testTag("icon_designer_effects"),
            content = { Text(stringResource(R.string.icon_designer_effects)) },
            supportingContent = { Text(stringResource(R.string.settings_coming_soon)) })
    }
}

@Composable
private fun DesignerSegment(index: Int, count: Int, tag: String, content: @Composable ColumnScope.() -> Unit) {
    SegmentedListItem(shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.testTag(tag), content = { Column(Modifier.padding(vertical = 8.dp), content = content) })
}

@Composable
private fun DesignerSlider(label: String, value: Float, default: Float, range: ClosedFloatingPointRange<Float>,
    enabled: Boolean, tag: String, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$label · ${value.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(value, { onChange(it.roundToInt().toFloat()) }, valueRange = range, enabled = enabled,
                modifier = Modifier.testTag(tag).semantics { contentDescription = label })
        }
        ResetIconButton(label, enabled && value != default, "${tag}_reset") { onChange(default) }
    }
}

@Composable
private fun DesignerColorRow(label: String, choice: IconColor?, dynamic: Int, enabled: Boolean, tag: String,
    onPick: () -> Unit, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onPick, enabled = enabled, modifier = Modifier.weight(1f).testTag(tag),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp)) {
            Surface(Modifier.size(28.dp), shape = CircleShape, color = choice?.let { Color(if (it.dynamic) dynamic else it.argb) }
                ?: MaterialTheme.colorScheme.surfaceContainerHighest, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label)
                Text(stringResource(if (choice == null) R.string.icon_designer_original_color else if (choice.dynamic)
                    R.string.settings_dynamic_colors else R.string.icon_designer_custom_color), style = MaterialTheme.typography.labelSmall)
            }
        }
        ResetIconButton(label, enabled && choice != null, "${tag}_reset", onReset)
    }
}

@Composable
private fun ResetIconButton(label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag)) {
        Icon(painterResource(R.drawable.ms_restart_alt), stringResource(R.string.clock_reset_value, label))
    }
}

@Composable
internal fun IconShape.label(): String = stringResource(when (this) {
    IconShape.None -> R.string.icon_designer_shape_none; IconShape.Circle -> R.string.icon_designer_shape_circle
    IconShape.Pebble -> R.string.icon_designer_shape_pebble; IconShape.Square -> R.string.icon_designer_shape_square
    IconShape.Gem -> R.string.icon_designer_shape_gem; IconShape.Cookie -> R.string.icon_designer_shape_cookie
})
