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
import com.galaxyrio.gracelauncher.SettingsActivity
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AppRepository(private val context: Context, private val iconPacks: IconPackRepository = IconPackRepository(context)) {
    private val packageManager = context.packageManager

    suspend fun loadApps(iconPackPackage: String? = null): List<LauncherApp> = withContext(Dispatchers.IO) {
        val pack = iconPacks.load(iconPackPackage)
        val coroutine = currentCoroutineContext()
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

        resolved.asSequence()
            .mapNotNull { resolveInfo ->
                coroutine.ensureActive()
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                // Expose only our settings entry, never the launcher/HOME activity.
                if (activityInfo.packageName == context.packageName &&
                    activityInfo.name != SettingsActivity::class.java.name) return@mapNotNull null
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                val label = resolveInfo.loadLabel(packageManager)
                    .toString()
                    .trim()
                    .ifBlank { activityInfo.name.substringAfterLast('.') }
                val drawable = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                val packed = pack?.iconFor(component, drawable)
                val icon = packed?.bitmap ?: runCatching { drawable?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
                val monochrome = if (packed != null) packed.monochrome else if (Build.VERSION.SDK_INT >= 33) {
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
                LauncherApp(componentName = component, label = label, icon = icon, monochromeIcon = monochrome,
                    iconPackPackage = pack?.packageName?.takeIf { packed != null }, monochromeScale = packed?.monochromeScale ?: 1.4f)
            }
            .distinctBy(LauncherApp::key)
            .sortedWith(LauncherAppOrder)
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
