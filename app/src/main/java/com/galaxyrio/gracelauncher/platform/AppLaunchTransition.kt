package com.galaxyrio.gracelauncher.platform

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.view.View

/** Public launch animation data; all coordinates supplied by the UI are in pixels. */
data class AppLaunchTransition(
    val sourceBounds: Rect,
    val options: Bundle,
) {
    companion object {
        /**
         * Compose's boundsInWindow and ActivityOptions use different coordinate spaces.
         * Convert explicitly so the reveal starts at the icon even in split screen or
         * when the host view is inset. Android remains in charge of the actual transition.
         */
        fun fromIcon(view: View, boundsInWindow: Rect): AppLaunchTransition? {
            if (!view.isAttachedToWindow || boundsInWindow.isEmpty) return null

            val viewInWindow = IntArray(2)
            val viewOnScreen = IntArray(2)
            view.getLocationInWindow(viewInWindow)
            view.getLocationOnScreen(viewOnScreen)
            val sourceBounds = Rect(boundsInWindow).apply {
                offset(viewOnScreen[0] - viewInWindow[0], viewOnScreen[1] - viewInWindow[1])
            }
            val options = ActivityOptions.makeClipRevealAnimation(
                view,
                boundsInWindow.left - viewInWindow[0],
                boundsInWindow.top - viewInWindow[1],
                boundsInWindow.width(),
                boundsInWindow.height(),
            ).toBundle()
            return AppLaunchTransition(sourceBounds, options)
        }
    }
}
