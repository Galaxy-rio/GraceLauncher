package com.galaxyrio.gracelauncher.data

import android.content.Context
import androidx.core.content.edit

class FavoritesStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun favoritesFor(apps: List<LauncherApp>): Set<String> {
        if (preferences.contains(KEY_FAVORITES)) {
            return preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()
        }

        val initial = apps.sortedWith(
            compareBy<LauncherApp> { preferredRank(it) }.thenBy { it.label.lowercase() },
        ).take(DEFAULT_FAVORITE_COUNT).mapTo(linkedSetOf(), LauncherApp::key)
        save(initial)
        return initial
    }

    fun toggle(appKey: String, current: Set<String>): Set<String> {
        val updated = current.toMutableSet().apply {
            if (!add(appKey)) remove(appKey)
        }
        save(updated)
        return updated
    }

    private fun save(keys: Set<String>) {
        preferences.edit { putStringSet(KEY_FAVORITES, keys) }
    }

    private fun preferredRank(app: LauncherApp): Int {
        val searchable = "${app.packageName} ${app.label}".lowercase()
        val preferredTokens = listOf(
            "dialer", "phone", "telecom",
            "message", "sms",
            "camera",
            "chrome", "browser",
            "gmail", "mail",
            "calendar",
        )
        val firstMatch = preferredTokens.indexOfFirst(searchable::contains)
        return if (firstMatch >= 0) firstMatch else Int.MAX_VALUE
    }

    private companion object {
        const val FILE_NAME = "grace_launcher_preferences"
        const val KEY_FAVORITES = "favorite_components"
        const val DEFAULT_FAVORITE_COUNT = 6
    }
}
