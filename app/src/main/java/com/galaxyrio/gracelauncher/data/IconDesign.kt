package com.galaxyrio.gracelauncher.data

import org.json.JSONObject

enum class IconShape { None, Circle, Pebble, Square, Gem, Cookie }

/** Dynamic colors remain dynamic after saving, including wallpaper changes. */
data class IconColor(val argb: Int, val dynamic: Boolean = false) {
    internal fun json() = JSONObject().put("argb", argb).put("dynamic", dynamic)
    companion object {
        internal fun decode(value: JSONObject?) = value?.let { IconColor(it.optInt("argb"), it.optBoolean("dynamic")) }
    }
}

/** Non-destructive parameters. Position is a percentage of the tray's width/height. */
data class IconDesign(
    val shape: IconShape = IconShape.None,
    val cookieSides: Int = 4,
    val background: IconColor? = null,
    val foreground: IconColor? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val size: Int = 100,
) {
    fun normalized() = copy(
        cookieSides = cookieSides.takeIf { it in CookieSides } ?: 4,
        x = x.takeIf { it.isFinite() }?.coerceIn(-50f, 50f) ?: 0f,
        y = y.takeIf { it.isFinite() }?.coerceIn(-50f, 50f) ?: 0f,
        size = size.coerceIn(25, 200),
    )
    internal fun json(): JSONObject = normalized().let { value ->
        JSONObject().put("shape", value.shape.name).put("cookieSides", value.cookieSides)
            .put("x", value.x).put("y", value.y).put("size", value.size).apply {
                value.background?.let { put("background", it.json()) }
                value.foreground?.let { put("foreground", it.json()) }
            }
    }
    companion object {
        val CookieSides = listOf(4, 6, 7, 9, 12)
        internal fun decode(value: JSONObject?) = value?.let {
            IconDesign(
                shape = IconShape.entries.firstOrNull { shape -> shape.name == it.optString("shape") } ?: IconShape.None,
                cookieSides = it.optInt("cookieSides", 4),
                background = IconColor.decode(it.optJSONObject("background")),
                foreground = IconColor.decode(it.optJSONObject("foreground")),
                x = it.optDouble("x", 0.0).toFloat(), y = it.optDouble("y", 0.0).toFloat(),
                size = it.optInt("size", 100),
            ).normalized()
        }
    }
}

/** Desktop overrides and pack-adapted icons are never candidates for bulk styling. */
fun isBulkIconDesignEligible(app: LauncherApp, special: ItemIcon?): Boolean =
    special == null && app.themeIconPackPackage == null && app.shortcut == null
