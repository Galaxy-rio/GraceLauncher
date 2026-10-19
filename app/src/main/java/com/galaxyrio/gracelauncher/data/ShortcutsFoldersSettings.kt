package com.galaxyrio.gracelauncher.data

import org.json.JSONObject

enum class NotificationDisplay(val id: String) {
    Normal("normal"), ExpandedOnly("expanded"), Hidden("hidden");

    fun visible(expanded: Boolean): Boolean = this == Normal || (expanded && this == ExpandedOnly)
}

data class ShortcutsFoldersSettings(
    val enabled: Boolean = true,
    val swipeLeftToOpenFirst: Boolean = false,
    val silentNotifications: NotificationDisplay = NotificationDisplay.Hidden,
    val normalNotifications: NotificationDisplay = NotificationDisplay.Normal,
    val popupSwipeDistanceDp: Int = 8,
    val popupSwipeAngleDegrees: Int = 45,
) {
    fun showsNotification(silent: Boolean, expanded: Boolean): Boolean = enabled &&
        (if (silent) silentNotifications else normalNotifications).visible(expanded)

    fun encode(): String = JSONObject().put("enabled", enabled)
        .put("swipeLeftToOpenFirst", swipeLeftToOpenFirst)
        .put("silentNotifications", silentNotifications.id)
        .put("normalNotifications", normalNotifications.id)
        .put("popupSwipeDistanceDp", popupSwipeDistanceDp.coerceIn(0, 48))
        .put("popupSwipeAngleDegrees", popupSwipeAngleDegrees.coerceIn(10, 90)).toString()

    companion object {
        fun decode(value: String?): ShortcutsFoldersSettings = runCatching {
            val json = JSONObject(value ?: "{}")
            ShortcutsFoldersSettings(
                enabled = json.optBoolean("enabled", true),
                swipeLeftToOpenFirst = json.optBoolean("swipeLeftToOpenFirst", false),
                popupSwipeDistanceDp = json.optInt("popupSwipeDistanceDp", 8).coerceIn(0, 48),
                popupSwipeAngleDegrees = json.optInt("popupSwipeAngleDegrees", 45).coerceIn(10, 90),
                silentNotifications = NotificationDisplay.entries.firstOrNull { it.id == json.optString("silentNotifications") }
                    ?: NotificationDisplay.Hidden,
                normalNotifications = NotificationDisplay.entries.firstOrNull { it.id == json.optString("normalNotifications") }
                    ?: NotificationDisplay.Normal,
            )
        }.getOrDefault(ShortcutsFoldersSettings())
    }
}
