package com.galaxyrio.gracelauncher.ui.overlays

import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.galaxyrio.gracelauncher.ui.components.rememberLauncherSystemBars

/** ModalBottomSheet owns a separate window; changing the activity window doesn't affect its bars. */
@Composable
@Suppress("DEPRECATION")
internal fun LauncherSheetSystemBars(hideStatusBar: Boolean, darkNavigationIcons: Boolean) {
    val view = LocalView.current
    val window = remember(view) {
        generateSequence(view) { it.parent as? View }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    }
    DisposableEffect(window) {
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            it.statusBarColor = Color.TRANSPARENT
            it.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 28) it.navigationBarDividerColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                it.isStatusBarContrastEnforced = false
                it.isNavigationBarContrastEnforced = false
            }
        }
        // Only the sheet's window was changed; it is discarded when the modal closes.
        onDispose { }
    }
    rememberLauncherSystemBars(autoHide = hideStatusBar, darkIcons = false, allowPullDown = false,
        darkNavigationIcons = darkNavigationIcons, window = window)
}

/** Keep cutouts and the keyboard safe, but let the scrolling viewport reach the navigation bar. */
@Composable
internal fun launcherSheetContentInsets(): WindowInsets = WindowInsets.safeDrawing
    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).union(WindowInsets.ime)

/** This space belongs inside scroll content, so the final action can scroll clear of system controls. */
@Composable
internal fun launcherSheetBottomInset(): Dp = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
    .asPaddingValues().calculateBottomPadding()
