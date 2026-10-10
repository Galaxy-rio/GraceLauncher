package com.galaxyrio.gracelauncher.data

import org.json.JSONObject

/** Desktop rail settings, stored alongside the rest of the list appearance. */
data class AlphabetAppearance(
    val fontId: String? = null,
    val fontColor: IconColor? = null,
    val fontSize: Int = 14,
    val indicatorShape: IconShape = IconShape.Circle,
    val pebbleRoundness: Int = IconDesign.DefaultPebbleRoundness,
    val squareCornerRadius: Int = 0,
    val cookieSides: Int = 4,
    val topPercent: Int = 35,
    val bottomPercent: Int = 85,
    val freeMovement: Boolean = false,
) {
    fun normalized(): AlphabetAppearance {
        val top = topPercent.coerceIn(0, 99)
        return copy(fontSize = fontSize.coerceIn(10, 28), topPercent = top,
            bottomPercent = bottomPercent.coerceIn(top + 1, 100),
            pebbleRoundness = pebbleRoundness.coerceIn(0, 100),
            squareCornerRadius = squareCornerRadius.coerceIn(0, 100),
            cookieSides = cookieSides.takeIf { it in IconDesign.CookieSides } ?: 4)
    }

    internal fun json(): JSONObject = normalized().let { value -> JSONObject().apply {
        put("fontId", value.fontId ?: JSONObject.NULL); put("fontColor", value.fontColor?.json())
        put("fontSize", value.fontSize); put("indicatorShape", value.indicatorShape.name)
        put("pebbleRoundness", value.pebbleRoundness); put("squareCornerRadius", value.squareCornerRadius)
        put("cookieSides", value.cookieSides); put("topPercent", value.topPercent)
        put("bottomPercent", value.bottomPercent); put("freeMovement", value.freeMovement)
    } }

    companion object {
        internal fun decode(value: JSONObject?): AlphabetAppearance {
            val defaults = AlphabetAppearance()
            return value?.let {
                AlphabetAppearance(
                    fontId = it.optString("fontId").takeIf { id -> id.isNotBlank() && id != "null" },
                    fontColor = IconColor.decode(it.optJSONObject("fontColor")),
                    fontSize = it.optInt("fontSize", defaults.fontSize),
                    indicatorShape = IconShape.entries.firstOrNull { shape -> shape.name == it.optString("indicatorShape") }
                        ?: defaults.indicatorShape,
                    pebbleRoundness = it.optInt("pebbleRoundness", defaults.pebbleRoundness),
                    squareCornerRadius = it.optInt("squareCornerRadius", defaults.squareCornerRadius),
                    cookieSides = it.optInt("cookieSides", defaults.cookieSides),
                    topPercent = it.optInt("topPercent", defaults.topPercent),
                    bottomPercent = it.optInt("bottomPercent", defaults.bottomPercent),
                    freeMovement = it.optBoolean("freeMovement", defaults.freeMovement),
                ).normalized()
            } ?: defaults
        }
    }
}
