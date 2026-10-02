package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.ClockFontStore
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.ui.theme.LauncherFontFamily

/** The editor and desktop share layout, responsive sizing and font resolution. */
@Composable
internal fun ClockFace(
    time: String,
    style: ClockStyle,
    color: Color,
    modifier: Modifier = Modifier,
    shadow: Shadow = Shadow.None,
) {
    val face = style.face.normalized()
    val context = LocalContext.current
    val fonts = remember(context) { ClockFontStore(context) }
    val customFamily by produceState<FontFamily?>(null, fonts, face.fontId, face.weight) {
        value = null
        value = face.fontId?.let { fonts.typeface(it, face.weight) }?.let { FontFamily(it) }
    }
    val stacked = style.layout == ClockLayout.TwoLines
    Text(
        text = clockFaceText(time, style), color = color, modifier = modifier,
        maxLines = if (stacked) 2 else 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = face.size.sp, stepSize = 1.sp),
        style = TextStyle(
            fontFamily = customFamily ?: LauncherFontFamily,
            fontSize = face.size.sp, fontWeight = FontWeight(face.weight),
            lineHeight = (if (stacked) 1.04 else 1.14).em,
            letterSpacing = face.letterSpacing.sp, fontFeatureSettings = "tnum", shadow = shadow,
        ),
    )
}

internal fun clockFaceText(time: String, style: ClockStyle): String = when {
    style.layout == ClockLayout.TwoLines -> time.replace(':', '\n')
    style.face.showColon -> time
    else -> time.replace(':', ' ')
}
