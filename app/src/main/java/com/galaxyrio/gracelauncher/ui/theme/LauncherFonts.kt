package com.galaxyrio.gracelauncher.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle

/**
 * Josefin Sans first, with bundled Noto fallbacks on Android 10+ and system CJK fonts.
 * Separate weight descriptors preserve real variable-font weights: wrapping a single
 * Android Typeface in Compose FontFamily would ignore subsequent TextStyle weights.
 */
val LauncherFontFamily: FontFamily = FontFamily(
    (100..700 step 100).map { weight -> LauncherFont(FontWeight(weight)) },
)

private data class LauncherFont(
    override val weight: FontWeight,
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = LauncherTypefaceLoader,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
) {
    override val style: FontStyle = FontStyle.Normal
}

private object LauncherTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        launcherTypeface(context, font.weight.weight)

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

private const val PRIMARY_FONT_ASSET = "fonts/josefin_sans.ttf"

private val typefaces = mutableMapOf<Int, Typeface>()

/**
 * Returns a cached real weight, shared by every launcher text surface.
 *
 * API 28 has no public custom per-glyph fallback-chain builder. It keeps the same
 * Josefin Sans variable weight and the device's sans-serif glyph fallback, which is not
 * guaranteed to be our bundled Noto files on OEM devices. No hidden APIs are used.
 *
 * Chinese, Japanese and Korean use Android's system sans-serif fallback.
 * Keep the text locale supplied by Compose so Android selects regional glyphs.
 */
internal fun launcherTypeface(context: Context, weight: Int): Typeface {
    require(weight in 100..700) { "Josefin Sans supports weights between 100 and 700" }
    return synchronized(typefaces) {
        typefaces.getOrPut(weight) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NotoFallbackApi29.build(context, weight)
            } else {
                checkNotNull(
                    Typeface.Builder(context.assets, PRIMARY_FONT_ASSET)
                        .setFontVariationSettings("'wght' $weight")
                        .setWeight(weight)
                        .setItalic(false)
                        .setFallback("sans-serif")
                        .build(),
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private object NotoFallbackApi29 {
    private val fallbackAssets = listOf(
        "fonts/noto_sans.ttf",
        "fonts/noto_sans_arabic.ttf",
        "fonts/noto_sans_hebrew.ttf",
        "fonts/noto_sans_devanagari.ttf",
        "fonts/noto_sans_thai.ttf",
    )

    // Retain each bundled font's native backing buffer once. All weight
    // instances reuse that buffer instead of re-reading files.
    // Access is serialized by the outer typeface cache lock.
    private val baseFonts = mutableMapOf<String, PlatformFont>()

    private fun family(context: Context, asset: String, weight: Int): PlatformFontFamily {
        val base = baseFonts.getOrPut(asset) {
            PlatformFont.Builder(context.assets, asset).build()
        }
        // The Font copy-builder needs API 31. The public buffer constructor also
        // shares font data and works on every API level supporting custom fallback.
        val font = PlatformFont.Builder(base.buffer)
            .setTtcIndex(base.ttcIndex)
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight)
            .setSlant(PlatformFontStyle.FONT_SLANT_UPRIGHT)
            .build()
        return PlatformFontFamily.Builder(font).build()
    }

    fun build(context: Context, weight: Int): Typeface {
        val builder = Typeface.CustomFallbackBuilder(family(context, PRIMARY_FONT_ASSET, weight))
        fallbackAssets.forEach { asset ->
            builder.addCustomFallback(family(context, asset, weight))
        }
        return builder
            .setStyle(PlatformFontStyle(weight, PlatformFontStyle.FONT_SLANT_UPRIGHT))
            // CJK, emoji and scripts beyond the packaged Noto families use the OS.
            .setSystemFallback("sans-serif")
            .build()
    }
}
