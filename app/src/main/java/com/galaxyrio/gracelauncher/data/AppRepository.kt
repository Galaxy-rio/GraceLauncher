package com.galaxyrio.gracelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val packageManager = context.packageManager

    suspend fun loadApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        resolved.asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                val label = resolveInfo.loadLabel(packageManager)
                    .toString()
                    .trim()
                    .ifBlank { activityInfo.name.substringAfterLast('.') }
                val drawable = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                val icon = runCatching { drawable?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
                val monochrome = if (Build.VERSION.SDK_INT >= 33) {
                    runCatching {
                        (drawable as? AdaptiveIconDrawable)?.monochrome?.toBitmap(144, 144)?.let { bitmap ->
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            // Some packages publish an effectively empty placeholder.
                            // Use their normal icon instead of a tiny dot in a circle.
                            bitmap.takeIf { pixels.count { pixel -> pixel ushr 24 > 32 } >= pixels.size / 100 }
                                ?.asImageBitmap()
                        }
                    }.getOrNull()
                } else null
                LauncherApp(componentName = component, label = label, icon = icon, monochromeIcon = monochrome)
            }
            .distinctBy(LauncherApp::key)
            .sortedWith { left, right -> collator.compare(left.label, right.label) }
            .toList()
    }

    fun launch(
        app: LauncherApp,
        sourceBounds: Rect? = null,
        options: Bundle? = null,
    ): Boolean = runCatching {
        // This launcher API preserves the target's task semantics and lets Android
        // animate the launch from the real clicked icon, without private APIs.
        context.getSystemService(LauncherApps::class.java).startMainActivity(
            app.componentName,
            Process.myUserHandle(),
            sourceBounds,
            options,
        )
    }.isSuccess
}
