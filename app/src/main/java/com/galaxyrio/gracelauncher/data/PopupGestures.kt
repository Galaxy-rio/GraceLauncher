package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

enum class PopupGesture(val id: String) { SwipeLeft("swipeLeft"), SwipeRight("swipeRight"), DoubleTap("doubleTap") }

enum class PopupGestureAction(val id: String) {
    DismissFirstNotification("dismissFirstNotification"),
    OpenFirstItem("openFirstItem"),
    OpenPopup("openPopup"),
    OpenFirstNotification("openFirstNotification"),
    DismissAllNotifications("dismissAllNotifications"),
}

data class PopupGestureActions(
    val order: List<PopupGestureAction> = PopupGestureAction.entries.toList(),
    val selected: Set<PopupGestureAction> = emptySet(),
) {
    val enabledActions: List<PopupGestureAction> get() = normalized().order.filter { it in selected }

    fun normalized() = copy(order = (order + PopupGestureAction.entries).distinct())

    fun json(): JSONArray = JSONArray().apply {
        normalized().order.forEach { action ->
            put(JSONObject().put("action", action.id).put("enabled", action in selected))
        }
    }

    /** An unavailable or stale target must fall through, but a successful action stops the gesture. */
    suspend fun performFirstAvailable(perform: suspend (PopupGestureAction) -> Boolean): Boolean {
        for (action in enabledActions) if (perform(action)) return true
        return false
    }

    companion object {
        fun defaults(gesture: PopupGesture) = PopupGestureActions(selected = when (gesture) {
            PopupGesture.SwipeLeft -> setOf(PopupGestureAction.OpenFirstItem)
            PopupGesture.SwipeRight -> setOf(PopupGestureAction.OpenPopup)
            PopupGesture.DoubleTap -> emptySet()
        })

        fun decode(array: JSONArray?, fallback: PopupGestureActions): PopupGestureActions {
            if (array == null) return fallback
            val order = mutableListOf<PopupGestureAction>()
            val selected = mutableSetOf<PopupGestureAction>()
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val action = PopupGestureAction.entries.firstOrNull { it.id == entry.optString("action") } ?: continue
                if (action in order) continue
                order += action
                if (entry.optBoolean("enabled", false)) selected += action
            }
            return PopupGestureActions(order, selected).normalized()
        }
    }
}
