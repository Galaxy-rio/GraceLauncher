package com.galaxyrio.gracelauncher.data

import org.json.JSONObject

enum class ClockLayout { SingleLine, TwoLines }

data class ClockFaceStyle(
    val fontId: String? = null,
    val weight: Int = 400,
    val size: Int = 72,
    val letterSpacing: Int = -3,
    val showColon: Boolean = false,
) {
    fun normalized() = copy(
        weight = weight.coerceIn(100, if (fontId == null) 700 else 900),
        size = size.coerceIn(32, 144),
        letterSpacing = letterSpacing.coerceIn(-8, 16),
    )
}

/** Each layout keeps its own draft and saved typography. */
data class ClockStyle(
    val layout: ClockLayout = ClockLayout.SingleLine,
    val singleLine: ClockFaceStyle = defaults(ClockLayout.SingleLine),
    val twoLines: ClockFaceStyle = defaults(ClockLayout.TwoLines),
) {
    val face: ClockFaceStyle get() = if (layout == ClockLayout.SingleLine) singleLine else twoLines

    fun withFace(face: ClockFaceStyle) = if (layout == ClockLayout.SingleLine) copy(singleLine = face.normalized())
        else copy(twoLines = face.normalized())

    fun encode(): String = JSONObject().apply {
        put("layout", layout.name)
        fun encodeFace(face: ClockFaceStyle) = JSONObject().apply {
            val safe = face.normalized()
            put("fontId", safe.fontId ?: JSONObject.NULL)
            put("weight", safe.weight)
            put("size", safe.size)
            put("letterSpacing", safe.letterSpacing)
            put("showColon", safe.showColon)
        }
        put("singleLine", encodeFace(singleLine))
        put("twoLines", encodeFace(twoLines))
    }.toString()

    companion object {
        fun defaults(layout: ClockLayout) = ClockFaceStyle(size = if (layout == ClockLayout.SingleLine) 72 else 88)

        fun decode(value: String?): ClockStyle = runCatching {
            val json = JSONObject(value ?: "{}")
            fun face(layout: ClockLayout, key: String): ClockFaceStyle {
                val defaults = defaults(layout)
                val data = json.optJSONObject(key) ?: return defaults
                return ClockFaceStyle(
                    fontId = data.optString("fontId").takeIf { it.isNotBlank() && it != "null" },
                    weight = data.optInt("weight", defaults.weight),
                    size = data.optInt("size", defaults.size),
                    letterSpacing = data.optInt("letterSpacing", defaults.letterSpacing),
                    showColon = data.optBoolean("showColon", defaults.showColon),
                ).normalized()
            }
            ClockStyle(
                layout = ClockLayout.entries.firstOrNull { it.name == json.optString("layout") } ?: ClockLayout.SingleLine,
                singleLine = face(ClockLayout.SingleLine, "singleLine"),
                twoLines = face(ClockLayout.TwoLines, "twoLines"),
            )
        }.getOrDefault(ClockStyle())
    }
}
