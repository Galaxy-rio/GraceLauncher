package com.galaxyrio.gracelauncher.ui.settings

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.isBulkIconDesignEligible
import com.galaxyrio.gracelauncher.data.icons.IconLayers
import com.galaxyrio.gracelauncher.data.icons.ItemIconStore
import com.galaxyrio.gracelauncher.data.icons.iconLayers
import com.galaxyrio.gracelauncher.data.icons.renderDesignedIcon
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DesignerPreviewIcon(app: LauncherApp, layers: IconLayers?, design: IconDesign, dynamicColors: Pair<Int, Int>,
    size: Dp, modifier: Modifier = Modifier, draggable: Boolean = false, onChange: (IconDesign) -> Unit = {}) {
    val bitmap by produceState<ImageBitmap?>(layers?.original?.asImageBitmap(), layers, design, dynamicColors) {
        value = layers?.let { withContext(Dispatchers.Default) { renderDesignedIcon(it, design, dynamicColors.first, dynamicColors.second).asImageBitmap() } }
    }
    val currentDesign by rememberUpdatedState(design)
    val changeDesign by rememberUpdatedState(onChange)
    val description = if (draggable) stringResource(R.string.icon_designer_drag_symbol, app.label) else app.label
    Box(modifier.size(size).semantics { contentDescription = description }
        .then(if (!draggable || layers == null) Modifier else Modifier.pointerInput(layers) {
            var moving = false
            detectDragGestures(orientationLock = null, onDragStart = { down, _, _ ->
                val position = down.position
                val style = currentDesign
                val symbol = if (style.foreground != null) layers.monochrome ?: layers.foreground else layers.foreground
                val px = (position.x / this.size.width - .5f - style.x / 100f) / (style.size / 100f) + .5f
                val py = (position.y / this.size.height - .5f - style.y / 100f) / (style.size / 100f) + .5f
                moving = px in 0f..<1f && py in 0f..<1f &&
                    AndroidColor.alpha(symbol.getPixel((px * symbol.width).toInt(), (py * symbol.height).toInt())) > 24
            }, onDragEnd = { moving = false }, onDragCancel = { moving = false }) { pointer, delta ->
                if (moving) {
                    pointer.consume()
                    changeDesign(currentDesign.copy(x = currentDesign.x + delta.x / this.size.width * 100,
                        y = currentDesign.y + delta.y / this.size.height * 100).normalized())
                }
            }
        }), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, null, Modifier.fillMaxSize()) else AppIcon(app, size = size)
    }
}

@Composable
internal fun DesignerBulkPreviewIcon(app: LauncherApp, uiState: LauncherUiState, choice: ItemIcon,
    store: ItemIconStore, dynamicColors: Pair<Int, Int>, size: Dp) {
    if (!isBulkIconDesignEligible(app, uiState.itemIcons[app.key])) {
        AppIcon(app, size = size)
        return
    }
    val data by produceState<Pair<IconLayers?, Boolean>>(null to false, app.key, choice.kind, choice.source, choice.name,
        uiState.settings.enabledIconPackPackages) {
        val mapped = store.isMappedBulkSource(app, choice)
        val source = if (choice.kind == "pack" && choice.name.isEmpty() && !mapped) ItemIcon.System else choice
        value = store.layers(app, source, uiState.settings) to mapped
    }
    DesignerPreviewIcon(app, data.first, if (data.second) IconDesign() else choice.design ?: IconDesign(), dynamicColors, size)
}

@Composable
internal fun DesignerSamplePreviewIcon(row: Int, column: Int, choice: ItemIcon, store: ItemIconStore,
    settings: LauncherSettings, dynamicColors: Pair<Int, Int>, size: Dp) {
    val context = LocalContext.current
    val original = remember(context, row, column, dynamicColors) {
        val symbols = listOf(listOf(R.drawable.ms_schedule, R.drawable.ms_calendar_month, R.drawable.ms_settings),
            listOf(R.drawable.ms_sunny, R.drawable.ms_folder, R.drawable.ms_star),
            listOf(R.drawable.ms_hourglass_empty, R.drawable.ms_category, R.drawable.ms_palette))
        val glyph = requireNotNull(context.getDrawable(symbols[row][column])).mutate()
        glyph.setTint(if (row == 0) dynamicColors.second else 0xFF24354C.toInt())
        val background = if (row == 0) dynamicColors.first else listOf(0xFFDBEBFA.toInt(), 0xFFFCE2C5.toInt(), 0xFFD9EDCD.toInt())[column]
        if (row < 2) iconLayers(AdaptiveIconDrawable(ColorDrawable(background), InsetDrawable(glyph, .30f)), 384)
        else {
            val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawRoundRect(0f, 0f, 384f, 384f, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
            glyph.setBounds(76, 76, 308, 308); glyph.draw(canvas)
            IconLayers(bitmap)
        }
    }
    val app = remember(original, row, column) {
        LauncherApp(ComponentName("designer.sample$row$column", "Preview"), "", original.original.asImageBitmap())
    }
    val layers by produceState(original, choice.kind, choice.source, original) {
        value = if (choice.kind == "image") store.layers(app, choice, settings) ?: original else original
    }
    DesignerPreviewIcon(app, layers, choice.design ?: IconDesign(), dynamicColors, size)
}
