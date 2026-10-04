package com.galaxyrio.gracelauncher.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import androidx.core.content.ContextCompat
import com.galaxyrio.gracelauncher.R
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.ThemeMode
import com.galaxyrio.gracelauncher.data.isBulkIconDesignEligible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copy a bounded image into private storage; no persistent gallery/storage permission needed. */
class ItemIconStore(private val context: Context, private val packs: IconPackRepository) {
    private val directory get() = File(context.filesDir, "item_icons")
    private val images = LruCache<String, Bitmap>(12)

    private fun imageBitmap(name: String, size: Int): Bitmap? = runCatching {
        val key = "$name:$size"
        images[key] ?: ImageDecoder.decodeBitmap(ImageDecoder.createSource(imageFile(name))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(size, size)
        }.also { images.put(key, it) }
    }.getOrNull()

    suspend fun importImage(uri: Uri): ItemIcon = withContext(Dispatchers.IO) {
        val image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1f, 512f / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
        val side = minOf(image.width, image.height)
        val cropped = Bitmap.createBitmap(image, (image.width - side) / 2, (image.height - side) / 2, side, side)
        directory.mkdirs()
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            ItemIcon("image", file.name)
        } catch (error: Exception) { file.delete(); throw error }
        finally { if (cropped !== image) cropped.recycle(); image.recycle() }
    }

    internal fun dynamicColors(settings: LauncherSettings): Pair<Int, Int> {
        val dark = when (settings.darkMode) {
            ThemeMode.Dark -> true; ThemeMode.Light -> false
            ThemeMode.System -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            return scheme.primaryContainer.toArgb() to scheme.onPrimaryContainer.toArgb()
        }
        return 0xFFEADDFF.toInt() to settings.themeColor
    }

    internal suspend fun layers(app: LauncherApp, choice: ItemIcon, settings: LauncherSettings, size: Int = 384): IconLayers? =
        withContext(Dispatchers.IO) {
            if (app.folderId != null && choice.kind in setOf("system", "theme")) {
                val colors = dynamicColors(settings)
                val glyph = ContextCompat.getDrawable(context, R.drawable.ms_folder)?.mutate() ?: return@withContext null
                glyph.setTint(colors.second)
                return@withContext iconLayers(AdaptiveIconDrawable(ColorDrawable(colors.first), InsetDrawable(glyph, 0.22f)), size)
            }
            val drawable = when (choice.kind) {
                "theme" -> settings.enabledIconPackPackages.firstNotNullOfOrNull { packs.load(it)?.designDrawableFor(app.componentName) }
                    ?: runCatching { context.packageManager.getActivityIcon(app.componentName) }.getOrNull()
                "system" -> runCatching { context.packageManager.getActivityIcon(app.componentName) }.getOrNull()
                "pack" -> packs.load(choice.source)?.let {
                    if (choice.name.isEmpty()) it.designDrawableFor(app.componentName) else it.designDrawable(choice.name)
                }
                else -> null
            }
            if (drawable != null && app.shortcut == null) return@withContext iconLayers(drawable, size)
            val bitmap = if (choice.kind == "image") imageBitmap(choice.source, size) else null
            (bitmap ?: (app.shortcut?.icon ?: app.icon)?.asAndroidBitmap())?.let { IconLayers(it) }
        }

    suspend fun apply(app: LauncherApp, choice: ItemIcon?, settings: LauncherSettings = LauncherSettings()): LauncherApp = withContext(Dispatchers.IO) {
        if (choice == null) return@withContext app
        choice.design?.let { design ->
            val original = layers(app, choice, settings, 144) ?: return@withContext app
            val colors = dynamicColors(settings)
            return@withContext app.copy(icon = renderDesignedIcon(original, design, colors.first, colors.second).asImageBitmap(),
                monochromeIcon = null, iconPackPackage = choice.source.takeIf { choice.kind == "pack" })
        }
        val icon = when (choice.kind) {
            "pack" -> packs.selectedIcon(choice)
            "image" -> imageBitmap(choice.source, 144)?.let { PackIcon(it.asImageBitmap()) }
            "system" -> if (app.folderId != null) null else if (app.shortcut != null) app.shortcut.icon?.let { PackIcon(it) } else runCatching {
                val drawable = context.packageManager.getActivityIcon(app.componentName)
                val mono = if (Build.VERSION.SDK_INT >= 33) (drawable as? AdaptiveIconDrawable)?.monochrome else null
                PackIcon(renderIcon(drawable).asImageBitmap(), mono?.let { renderIcon(it).asImageBitmap() })
            }.getOrNull()
            else -> null
        }
        if (icon == null) app else app.copy(icon = icon.bitmap, monochromeIcon = icon.monochrome,
            monochromeScale = icon.monochromeScale, iconPackPackage = choice.source.takeIf { choice.kind == "pack" })
    }

    /** Special (including desktop edits) > mapped packs > bulk design > system. */
    internal suspend fun isMappedBulkSource(app: LauncherApp, choice: ItemIcon): Boolean =
        choice.kind == "pack" && choice.name.isEmpty() && packs.load(choice.source)?.designDrawableFor(app.componentName) != null

    suspend fun applyDesign(app: LauncherApp, special: ItemIcon?, bulk: ItemIcon?, settings: LauncherSettings): LauncherApp {
        if (special != null) return apply(app, special, settings)
        if (bulk == null || !isBulkIconDesignEligible(app, special)) return app
        // A bulk source pack supplies its mapped icons intact; style only its missing icons.
        if (bulk.kind == "pack" && bulk.name.isEmpty()) {
            val mapped = packs.load(bulk.source)?.iconFor(app.componentName, null)
            if (mapped != null) return app.copy(icon = mapped.bitmap, monochromeIcon = mapped.monochrome,
                monochromeScale = mapped.monochromeScale, iconPackPackage = bulk.source)
            return apply(app, ItemIcon.System.copy(design = bulk.design), settings)
        }
        return apply(app, bulk, settings)
    }

    suspend fun deleteImage(choice: ItemIcon?) = withContext(Dispatchers.IO) {
        if (choice?.kind == "image") runCatching {
            images.snapshot().keys.filter { it.startsWith("${choice.source}:") }.forEach(images::remove)
            imageFile(choice.source).delete()
        }
        Unit
    }

    private fun imageFile(name: String): File {
        require(name.matches(Regex("[a-fA-F0-9-]{36}\\.png")))
        return File(directory, name)
    }
}
