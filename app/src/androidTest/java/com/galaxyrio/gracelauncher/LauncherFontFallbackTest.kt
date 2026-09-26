package com.galaxyrio.gracelauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.text.PositionedGlyphs
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.ui.theme.launcherTypeface
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verify actual native glyph selection, not just the declared Compose family. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class LauncherFontFallbackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assetSizes = mutableMapOf<String, Int>()

    @Test
    fun mixedTextUsesJosefinForLatinAndBundledNotoForOtherGlyphs() {
        val glyphs = shape("A中あ한ΩЖ")
        assertEquals(6, glyphs.glyphCount())
        listOf("josefin_sans", "noto_sans_cjk", "noto_sans_cjk", "noto_sans_cjk", "noto_sans", "noto_sans")
            .forEachIndexed { index, asset -> assertFont(glyphs, index, asset) }
    }

    @Test
    fun additionalScriptsUseTheirBundledNotoFamilies() {
        listOf(
            Triple("العربية", "ar", "noto_sans_arabic"),
            Triple("עברית", "he", "noto_sans_hebrew"),
            // The general Noto Sans file also contains these Devanagari glyphs.
            Triple("हिन्दी", "hi", "noto_sans"),
            Triple("ภาษาไทย", "th", "noto_sans_thai"),
        ).forEach { (text, language, asset) ->
            val glyphs = shape(text, locale = Locale.forLanguageTag(language), rtl = language in listOf("ar", "he"))
            assertTrue(glyphs.glyphCount() > 0)
            repeat(glyphs.glyphCount()) { index -> assertFont(glyphs, index, asset) }
        }
    }

    @Test
    fun allWeightsUseRealVariableFontInstancesForPrimaryAndFallback() {
        (100..700 step 100).forEach { weight ->
            val typeface = launcherTypeface(context, weight)
            assertEquals(weight, typeface.weight)
            assertSame(typeface, launcherTypeface(context, weight))
            val glyphs = shape("A中", weight)
            assertFont(glyphs, 0, "josefin_sans")
            assertFont(glyphs, 1, "noto_sans_cjk")
            repeat(glyphs.glyphCount()) { index ->
                val font = glyphs.getFont(index)
                assertEquals(weight, font.style.weight)
                val axis = font.axes.orEmpty().singleOrNull { it.tag == "wght" }
                // Android 15+ can carry the variable weight as a per-glyph
                // override rather than an axis on the shared backing Font.
                val override = if (Build.VERSION.SDK_INT >= 35) {
                    assertTrue("Weight must not be synthesized", !glyphs.getFakeBold(index))
                    glyphs.getWeightOverride(index).takeUnless { it == PositionedGlyphs.NO_OVERRIDE }
                } else null
                // Both bundled Josefin Sans and Noto CJK default to Thin.
                val defaultWeight = 100f
                assertEquals("Weight $weight, glyph $index", weight.toFloat(), override ?: axis?.styleValue ?: defaultWeight, 0.01f)
            }
        }
        listOf("A", "中").forEach { text ->
            val thin = inkCoverage(text, 100)
            val normal = inkCoverage(text, 400)
            val bold = inkCoverage(text, 700)
            assertTrue("$text must render real weight differences", thin < normal && normal < bold)
        }
    }

    @Test
    fun japaneseAndChineseSelectLocalizedFormsFromTheSameCjkFile() {
        val chinese = shape("骨", locale = Locale.SIMPLIFIED_CHINESE)
        val japanese = shape("骨", locale = Locale.JAPANESE)
        assertFont(chinese, 0, "noto_sans_cjk")
        assertFont(japanese, 0, "noto_sans_cjk")
        assertNotEquals(chinese.getGlyphId(0), japanese.getGlyphId(0))
    }

    @Test
    fun emojiRemainsAvailableThroughTheFinalSystemFallback() {
        val glyphs = shape("😀")
        assertTrue(glyphs.glyphCount() > 0)
        repeat(glyphs.glyphCount()) { index -> assertTrue(glyphs.getGlyphId(index) != 0) }
    }

    private fun shape(
        text: String,
        weight: Int = 400,
        locale: Locale = Locale.ENGLISH,
        rtl: Boolean = false,
    ): PositionedGlyphs {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = launcherTypeface(context, weight)
            textSize = 32f
            textLocale = locale
        }
        return TextRunShaper.shapeTextRun(text, 0, text.length, 0, text.length, 0f, 0f, rtl, paint)
    }

    private fun assertFont(glyphs: PositionedGlyphs, index: Int, asset: String) {
        assertTrue("Missing glyph at $index for $asset", glyphs.getGlyphId(index) != 0)
        val size = assetSizes.getOrPut(asset) {
            context.assets.open("fonts/$asset.ttf").use { input ->
                val buffer = ByteArray(8192)
                var size = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                }
                size
            }
        }
        // Each bundled font has a distinct size; system fallback fonts cannot
        // silently satisfy this check (including the system's own Noto CJK).
        assertEquals("Unexpected native font for glyph $index; expected $asset", size, glyphs.getFont(index).buffer.capacity())
    }

    private fun inkCoverage(text: String, weight: Int): Long {
        val bitmap = Bitmap.createBitmap(256, 160, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = launcherTypeface(context, weight)
            textSize = 100f
        }
        Canvas(bitmap).drawText(text, 20f, 120f, paint)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return pixels.sumOf { (it ushr 24).toLong() }
    }
}
