package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

enum class PopupGesture(val id: String) { SwipeLeft("swipeLeft"), SwipeRight("swipeRight"), DoubleTap("doubleTap") }

enum class PopupGestureScope { List, InsidePopup }

enum class PopupGestureAction(val id: String) {
    DismissFirstNotification("dismissFirstNotification"),
    OpenFirstItem("openFirstItem"),
    OpenPopup("openPopup"),
    OpenFirstNotification("openFirstNotification"),
    DismissAllNotifications("dismissAllNotifications"),
    /** A single expanded card, independent of the first notification on an app row. */
    DismissNotification("dismissNotification"),
}

data class PopupGestureActions(
    val order: List<PopupGestureAction> = PopupGestureAction.entries.toList(),
    val selected: Set<PopupGestureAction> = emptySet(),
    val confirmDismissAll: Boolean = false,
) {
    val enabledActions: List<PopupGestureAction> get() = normalized().order.filter { it in selected }

    fun normalized() = copy(order = (order + PopupGestureAction.entries).distinct())

    fun json(): JSONArray = JSONArray().apply {
        normalized().order.forEach { action ->
            put(JSONObject().put("action", action.id).put("enabled", action in selected).apply {
                if (action == PopupGestureAction.DismissAllNotifications) put("confirmMultipleApps", confirmDismissAll)
            })
        }
    }

    /** An unavailable or stale target must fall through, but a successful action stops the gesture. */
    suspend fun performFirstAvailable(perform: suspend (PopupGestureAction) -> Boolean): Boolean {
        for (action in enabledActions) if (perform(action)) return true
        return false
    }

    companion object {
        // Card dismissal used to be independent of what a swipe on an app row did.
        // Keep both behaviors by putting the card-only action first on upgrade.
        internal fun legacyInsideGestures(
            listLeft: PopupGestureActions = defaults(PopupGesture.SwipeLeft),
            listRight: PopupGestureActions = defaults(PopupGesture.SwipeRight),
            dismissRight: Boolean = true,
        ): Map<PopupGesture, PopupGestureActions> {
            val card = PopupGestureAction.DismissNotification
            val clearActions = setOf(PopupGestureAction.DismissFirstNotification, PopupGestureAction.DismissAllNotifications)
            fun inside(previous: PopupGestureActions, allowed: Set<PopupGestureAction>, dismissCard: Boolean) =
                previous.copy(
                    order = (listOf(card) + previous.order).distinct(),
                    selected = previous.selected.intersect(allowed) + if (dismissCard) setOf(card) else emptySet(),
                ).normalized()
            return mapOf(
                PopupGesture.SwipeLeft to inside(listLeft, clearActions, true),
                PopupGesture.SwipeRight to inside(listRight, setOf(PopupGestureAction.OpenPopup), dismissRight),
                PopupGesture.DoubleTap to PopupGestureActions(),
            )
        }

        fun defaults(gesture: PopupGesture) = PopupGestureActions(selected = when (gesture) {
            PopupGesture.SwipeLeft -> setOf(PopupGestureAction.OpenFirstItem)
            PopupGesture.SwipeRight -> setOf(PopupGestureAction.OpenPopup)
            PopupGesture.DoubleTap -> emptySet()
        })

        fun decode(array: JSONArray?, fallback: PopupGestureActions): PopupGestureActions {
            if (array == null) return fallback
            val order = mutableListOf<PopupGestureAction>()
            val selected = mutableSetOf<PopupGestureAction>()
            var confirmDismissAll = false
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val action = PopupGestureAction.entries.firstOrNull { it.id == entry.optString("action") } ?: continue
                if (action in order) continue
                order += action
                if (entry.optBoolean("enabled", false)) selected += action
                if (action == PopupGestureAction.DismissAllNotifications) confirmDismissAll = entry.optBoolean("confirmMultipleApps", false)
            }
            return PopupGestureActions(order, selected, confirmDismissAll).normalized()
        }
    }
}
