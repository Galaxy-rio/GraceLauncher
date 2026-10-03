@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.galaxyrio.gracelauncher.data.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.material3.MaterialShapes
import androidx.core.graphics.createBitmap
import androidx.graphics.shapes.toPath
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The same original layers and renderer are used by the live draft and saved launcher icons. */
internal class IconLayers(
    val original: Bitmap,
    val background: Bitmap? = null,
    val foreground: Bitmap = original,
    val monochrome: Bitmap? = null,
    val originalMask: Path? = null,
) {
    val layered: Boolean get() = background != null
    private var tintedKey: Pair<Int?, Int?>? = null
    private var tinted: Bitmap? = null

    @Synchronized
    fun symbol(backgroundColor: Int?, foregroundColor: Int?): Bitmap {
        if (backgroundColor == null && foregroundColor == null) return foreground
        val key = backgroundColor to foregroundColor
        if (tintedKey == key) tinted?.let { return it }
        val result = if (layered) {
            if (foregroundColor == null) foreground else createBitmap(original.width, original.height).also {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN)
                }
                Canvas(it).drawBitmap(monochrome ?: foreground, 0f, 0f, paint)
            }
        } else recolorGrayscale(foreground, backgroundColor ?: Color.BLACK, foregroundColor ?: Color.WHITE)
        tintedKey = key
        tinted = result
        return result
    }
}

internal fun iconLayers(drawable: Drawable, size: Int): IconLayers {
    val original = renderIcon(drawable, size)
    if (drawable !is AdaptiveIconDrawable) return IconLayers(original)
    val previous = Rect(drawable.bounds)
    try {
        drawable.setBounds(0, 0, size, size)
        // AdaptiveIconDrawable sets expanded child bounds, preserving Android's safe zone.
        fun layer(child: Drawable): Bitmap = createBitmap(size, size).also { child.draw(Canvas(it)) }
        val mono = if (Build.VERSION.SDK_INT >= 33) drawable.monochrome?.let {
            it.bounds = drawable.foreground.bounds
            layer(it)
        } else null
        return IconLayers(original, layer(drawable.background), layer(drawable.foreground), mono, Path(drawable.iconMask))
    } finally { drawable.bounds = previous }
}

internal fun renderDesignedIcon(layers: IconLayers, design: IconDesign, dynamicBackground: Int, dynamicForeground: Int): Bitmap {
    val style = design.normalized()
    if (style == IconDesign()) return layers.original
    val side = layers.original.width
    val output = createBitmap(side, side)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val mask = if (style.shape == IconShape.None) layers.originalMask else iconShapePath(style.shape, style.cookieSides, side.toFloat())
    mask?.let(canvas::clipPath)
    val background = style.background?.let { if (it.dynamic) dynamicBackground else it.argb }
    val foreground = style.foreground?.let { if (it.dynamic) dynamicForeground else it.argb }
    if (layers.layered) {
        if (background != null) canvas.drawColor(background)
        else layers.background?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
    }
    val scale = style.size / 100f
    val centerX = side * (0.5f + style.x / 100f)
    val centerY = side * (0.5f + style.y / 100f)
    val half = side * scale / 2f
    canvas.drawBitmap(layers.symbol(background, foreground), null,
        RectF(centerX - half, centerY - half, centerX + half, centerY + half), paint)
    return output
}

/** Normalize only visible pixels; transparent padding must not compress the luminance range. */
internal fun recolorGrayscale(bitmap: Bitmap, start: Int, end: Int): Bitmap {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    fun luminance(color: Int): Float = .2126f * Color.red(color) + .7152f * Color.green(color) + .0722f * Color.blue(color)
    var low = 255f
    var high = 0f
    for (color in pixels) if (Color.alpha(color) > 0) {
        val gray = luminance(color)
        low = minOf(low, gray); high = maxOf(high, gray)
    }
    val span = high - low
    fun channel(from: Int, to: Int, amount: Float) = (from + (to - from) * amount).roundToInt().coerceIn(0, 255)
    for (index in pixels.indices) {
        val color = pixels[index]
        if (Color.alpha(color) == 0) continue
        val amount = if (span > .001f) ((luminance(color) - low) / span).coerceIn(0f, 1f) else 1f
        pixels[index] = Color.argb(Color.alpha(color),
            channel(Color.red(start), Color.red(end), amount),
            channel(Color.green(start), Color.green(end), amount),
            channel(Color.blue(start), Color.blue(end), amount))
    }
    return Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
}

/** Native Material expressive Gem/Cookie paths, with a continuous Samsung-style squircle. */
internal fun iconShapePath(shape: IconShape, sides: Int = 4, size: Float = 1f): Path {
    val path = when (shape) {
        IconShape.Gem -> MaterialShapes.Gem.toPath()
        IconShape.Cookie -> when (sides) {
            6 -> MaterialShapes.Cookie6Sided; 7 -> MaterialShapes.Cookie7Sided
            9 -> MaterialShapes.Cookie9Sided; 12 -> MaterialShapes.Cookie12Sided
            else -> MaterialShapes.Cookie4Sided
        }.toPath()
        IconShape.Pebble -> Path().apply {
            repeat(128) { index ->
                val angle = index * Math.PI * 2 / 128
                fun squircle(value: Double) = (abs(value).pow(.5) * if (value < 0) -1 else 1).toFloat()
                val x = .5f + .5f * squircle(cos(angle))
                val y = .5f + .5f * squircle(sin(angle))
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        IconShape.Square -> Path().apply { addRect(0f, 0f, 1f, 1f, Path.Direction.CW) }
        IconShape.None, IconShape.Circle -> Path().apply { addCircle(.5f, .5f, .5f, Path.Direction.CW) }
    }
    path.transform(Matrix().apply { setScale(size, size) })
    return path
}
