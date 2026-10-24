package com.galaxyrio.gracelauncher.platform

internal enum class WidgetReconfigurationRoute { Wait, Direct, Host }

/** Reconfiguration is a foreground user action, not an automatic launch during onCreate. */
internal fun widgetReconfigurationRoute(
    queued: Boolean,
    resumed: Boolean,
    focused: Boolean,
    sameProfile: Boolean,
): WidgetReconfigurationRoute = when {
    !queued || !resumed || !focused -> WidgetReconfigurationRoute.Wait
    sameProfile -> WidgetReconfigurationRoute.Direct
    // Only AppWidgetHost can launch the provider's configuration across profiles.
    else -> WidgetReconfigurationRoute.Host
}
