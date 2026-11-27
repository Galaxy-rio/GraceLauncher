package com.galaxyrio.gracelauncher.data

import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import org.json.JSONObject

enum class NotificationDisplay(val id: String) {
    Normal("normal"), ExpandedOnly("expanded"), Hidden("hidden");

    fun visible(expanded: Boolean): Boolean = this == Normal || (expanded && this == ExpandedOnly)
}

data class ShortcutsFoldersSettings(
    val enabled: Boolean = true,
    val swipeLeft: PopupGestureActions = PopupGestureActions.defaults(PopupGesture.SwipeLeft),
    val swipeRight: PopupGestureActions = PopupGestureActions.defaults(PopupGesture.SwipeRight),
    val doubleTap: PopupGestureActions = PopupGestureActions.defaults(PopupGesture.DoubleTap),
    val silentNotifications: NotificationDisplay = NotificationDisplay.Hidden,
    val normalNotifications: NotificationDisplay = NotificationDisplay.Normal,
    val popupSwipeDistanceDp: Int = 8,
    val popupSwipeAngleDegrees: Int = 45,
    val singleLineNotificationPreviews: Boolean = false,
    val multipleAppNotificationPreviews: Boolean = false,
    val reduceRepeatedSenders: Boolean = true,
    val insideGestures: Map<PopupGesture, PopupGestureActions> = DefaultInsideGestures,
    val notificationActions: Boolean = true,
    val notificationDirectReply: Boolean = true,
    val notificationSuggestedReplies: Boolean = true,
    val notificationProgress: Boolean = true,
    val widerNotificationDetails: Boolean = false,
) {
    fun gesture(gesture: PopupGesture, scope: PopupGestureScope = PopupGestureScope.List): PopupGestureActions =
        if (scope == PopupGestureScope.InsidePopup) insideGestures[gesture] ?: DefaultInsideGestures.getValue(gesture)
        else when (gesture) {
            PopupGesture.SwipeLeft -> swipeLeft
            PopupGesture.SwipeRight -> swipeRight
            PopupGesture.DoubleTap -> doubleTap
        }

    fun withGesture(gesture: PopupGesture, value: PopupGestureActions, scope: PopupGestureScope = PopupGestureScope.List): ShortcutsFoldersSettings =
        if (scope == PopupGestureScope.InsidePopup) copy(insideGestures = insideGestures + (gesture to value.normalized()))
        else when (gesture) {
            PopupGesture.SwipeLeft -> copy(swipeLeft = value.normalized())
            PopupGesture.SwipeRight -> copy(swipeRight = value.normalized())
            PopupGesture.DoubleTap -> copy(doubleTap = value.normalized())
        }

    fun showsNotification(silent: Boolean, expanded: Boolean): Boolean = enabled &&
        (if (silent) silentNotifications else normalNotifications).visible(expanded)

    /** Select from already visible, newest-first notifications without changing the full pop-up. */
    fun previewNotifications(notifications: List<AppNotification>): List<AppNotification> =
        if (multipleAppNotificationPreviews) notifications.distinctBy { it.packageName } else notifications.take(1)

    fun encode(): String = JSONObject().put("enabled", enabled)
        .put("swipeLeft", swipeLeft.json())
        .put("swipeRight", swipeRight.json())
        .put("doubleTap", doubleTap.json())
        .put("silentNotifications", silentNotifications.id)
        .put("normalNotifications", normalNotifications.id)
        .put("singleLineNotificationPreviews", singleLineNotificationPreviews)
        .put("multipleAppNotificationPreviews", multipleAppNotificationPreviews)
        .put("reduceRepeatedSenders", reduceRepeatedSenders)
        .put("insideGestures", JSONObject().apply {
            PopupGesture.entries.forEach { put(it.id, gesture(it, PopupGestureScope.InsidePopup).json()) }
        })
        .put("notificationActions", notificationActions)
        .put("notificationDirectReply", notificationDirectReply)
        .put("notificationSuggestedReplies", notificationSuggestedReplies)
        .put("notificationProgress", notificationProgress)
        .put("widerNotificationDetails", widerNotificationDetails)
        .put("popupSwipeDistanceDp", popupSwipeDistanceDp.coerceIn(0, 48))
        .put("popupSwipeAngleDegrees", popupSwipeAngleDegrees.coerceIn(10, 90)).toString()

    companion object {
        private val DefaultInsideGestures = PopupGestureActions.legacyInsideGestures()

        fun decode(value: String?): ShortcutsFoldersSettings = runCatching {
            val json = JSONObject(value ?: "{}")
            val left = PopupGestureActions.decode(json.optJSONArray("swipeLeft"),
                if (json.has("swipeLeftToOpenFirst")) PopupGestureActions(selected =
                    if (json.optBoolean("swipeLeftToOpenFirst")) setOf(PopupGestureAction.OpenFirstItem) else emptySet())
                else PopupGestureActions.defaults(PopupGesture.SwipeLeft))
            val right = PopupGestureActions.decode(json.optJSONArray("swipeRight"), PopupGestureActions.defaults(PopupGesture.SwipeRight))
            val inside = json.optJSONObject("insideGestures")
            ShortcutsFoldersSettings(
                enabled = json.optBoolean("enabled", true),
                swipeLeft = left,
                swipeRight = right,
                insideGestures = if (inside == null) PopupGestureActions.legacyInsideGestures(left, right,
                    json.optBoolean("notificationSwipeRightDismiss", true))
                else PopupGesture.entries.associateWith { gesture ->
                    PopupGestureActions.decode(inside.optJSONArray(gesture.id), DefaultInsideGestures.getValue(gesture))
                },
                doubleTap = PopupGestureActions.decode(json.optJSONArray("doubleTap"), PopupGestureActions.defaults(PopupGesture.DoubleTap)),
                singleLineNotificationPreviews = json.optBoolean("singleLineNotificationPreviews", false),
                multipleAppNotificationPreviews = json.optBoolean("multipleAppNotificationPreviews", false),
                reduceRepeatedSenders = json.optBoolean("reduceRepeatedSenders", true),
                notificationActions = json.optBoolean("notificationActions", true),
                notificationDirectReply = json.optBoolean("notificationDirectReply", true),
                notificationSuggestedReplies = json.optBoolean("notificationSuggestedReplies", true),
                notificationProgress = json.optBoolean("notificationProgress", true),
                widerNotificationDetails = json.optBoolean("widerNotificationDetails", false),
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
