package com.galaxyrio.gracelauncher.data

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap
import java.text.Normalizer
import java.util.Locale

data class LauncherApp(
    val componentName: ComponentName,
    val label: String,
    val icon: ImageBitmap?,
) {
    val key: String = componentName.flattenToString()
    val packageName: String = componentName.packageName
    val section: String = sectionForLabel(label)
}

val LauncherAlphabet: List<String> = ('A'..'Z').map(Char::toString) + "#"

private val combiningMarks = Regex("\\p{Mn}+")

fun sectionForLabel(label: String): String {
    val latinized = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .uppercase(Locale.getDefault())
    val first = latinized.firstOrNull()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}
