package com.galaxyrio.gracelauncher.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copy a bounded image into private storage; no persistent gallery/storage permission needed. */
class ItemIconStore(private val context: Context, private val packs: IconPackRepository) {
    private val directory get() = File(context.filesDir, "item_icons")

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

    suspend fun apply(app: LauncherApp, choice: ItemIcon?): LauncherApp = withContext(Dispatchers.IO) {
        if (choice == null) return@withContext app
        val icon = when (choice.kind) {
            "pack" -> packs.selectedIcon(choice)
            "image" -> runCatching {
                val image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(imageFile(choice.source))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                PackIcon(image.asImageBitmap())
            }.getOrNull()
            "system" -> if (app.shortcut != null) app.shortcut.icon?.let { PackIcon(it) } else runCatching {
                val drawable = context.packageManager.getActivityIcon(app.componentName)
                val mono = if (Build.VERSION.SDK_INT >= 33) (drawable as? AdaptiveIconDrawable)?.monochrome else null
                PackIcon(renderIcon(drawable).asImageBitmap(), mono?.let { renderIcon(it).asImageBitmap() })
            }.getOrNull()
            else -> null
        }
        if (icon == null) app else app.copy(icon = icon.bitmap, monochromeIcon = icon.monochrome,
            monochromeScale = icon.monochromeScale, iconPackPackage = choice.source.takeIf { choice.kind == "pack" })
    }

    suspend fun deleteImage(choice: ItemIcon?) = withContext(Dispatchers.IO) {
        if (choice?.kind == "image") runCatching { imageFile(choice.source).delete() }
        Unit
    }

    private fun imageFile(name: String): File {
        require(name.matches(Regex("[a-fA-F0-9-]{36}\\.png")))
        return File(directory, name)
    }
}
