package com.galaxyrio.gracelauncher.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.galaxyrio.gracelauncher.data.WallpaperTextMode

data class LauncherAppearance(val darkText: Boolean = false, val themedIcons: Boolean = true) {
    val text: Color get() = if (darkText) Color(0xFF202025) else Color(0xFFFAF9FE)
    val textShadow: Shadow get() = if (darkText) Shadow.None else Shadow(Color.Black.copy(alpha = 0.32f), blurRadius = 4f)
}

val LocalLauncherAppearance = staticCompositionLocalOf { LauncherAppearance() }

@Composable
fun rememberLauncherAppearance(mode: WallpaperTextMode, themedIcons: Boolean): LauncherAppearance {
    val context = LocalContext.current
    val manager = remember(context) { WallpaperManager.getInstance(context) }
    fun prefersDark(colors: WallpaperColors?): Boolean = Build.VERSION.SDK_INT >= 31 &&
        colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    var dark by remember(manager) {
        mutableStateOf(runCatching { prefersDark(manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)) }.getOrDefault(false))
    }
    DisposableEffect(manager) {
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) dark = prefersDark(colors)
        }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return LauncherAppearance(
        darkText = when (mode) {
            WallpaperTextMode.Auto -> dark
            WallpaperTextMode.Light -> false
            WallpaperTextMode.Dark -> true
        },
        themedIcons = themedIcons,
    )
}

@Composable
fun rememberBatteryPercent(): Int? {
    val context = LocalContext.current
    var percent by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                percent = if (level >= 0 && scale > 0) (100 * level / scale) else null
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}
