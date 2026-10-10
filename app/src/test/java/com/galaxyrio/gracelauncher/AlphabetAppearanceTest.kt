package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.AlphabetAppearance
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.ui.components.alphabetDriftAt
import com.galaxyrio.gracelauncher.ui.components.alphabetIndexAt
import org.junit.Assert.*
import org.junit.Test

class AlphabetAppearanceTest {
    @Test fun defaultsKeepTheOriginalGlyphSizeAndCircularIndicator() {
        val value = AlphabetAppearance()
        assertEquals(14, value.fontSize)
        assertEquals(IconShape.Circle, value.indicatorShape)
        assertEquals(85, value.pebbleRoundness)
        assertFalse(value.freeMovement)
        assertNull(value.fontId)
        assertNull(value.fontColor)
    }

    @Test fun limitsAlwaysDescribeANonEmptyRangeWithinTheScreen() {
        for (top in listOf(-100, 0, 35, 85, 99, 100, 1000)) {
            for (bottom in listOf(-100, 0, 35, 85, 99, 100, 1000)) {
                val value = AlphabetAppearance(topPercent = top, bottomPercent = bottom).normalized()
                assertTrue(value.topPercent in 0..99)
                assertTrue(value.bottomPercent in (value.topPercent + 1)..100)
            }
        }
    }

    @Test fun invalidSizesAndShapeParametersAreSanitized() {
        val value = AlphabetAppearance(fontSize = 90, pebbleRoundness = -10, squareCornerRadius = 300, cookieSides = 99).normalized()
        assertEquals(28, value.fontSize)
        assertEquals(0, value.pebbleRoundness)
        assertEquals(100, value.squareCornerRadius)
        assertEquals(4, value.cookieSides)
        assertEquals(10, value.copy(fontSize = -1).normalized().fontSize)
    }

    @Test fun topOvershootReversesFromTheNewOriginWithoutUndoingTheOvershoot() {
        val drift = alphabetDriftAt(-90f, 300f, 0f, true)
        assertEquals(-90f, drift, 0f)
        assertEquals(0, alphabetIndexAt(-90f - drift, 300f, 6))
        val reversed = alphabetDriftAt(-35f, 300f, drift, true)
        assertEquals(drift, reversed, 0f)
        assertEquals(1, alphabetIndexAt(-35f - reversed, 300f, 6))
    }

    @Test fun bottomOvershootHasTheSameImmediateReverseBehavior() {
        val drift = alphabetDriftAt(410f, 300f, 0f, true)
        assertEquals(110f, drift, 0f)
        assertEquals(5, alphabetIndexAt(410f - drift, 300f, 6))
        val reversed = alphabetDriftAt(350f, 300f, drift, true)
        assertEquals(drift, reversed, 0f)
        assertEquals(4, alphabetIndexAt(350f - reversed, 300f, 6))
    }

    @Test fun stationaryAndDisabledRailsClampInsteadOfDrifting() {
        assertEquals(0f, alphabetDriftAt(100f, 300f, 0f, true), 0f)
        assertEquals(0f, alphabetDriftAt(-90f, 300f, -40f, false), 0f)
        assertEquals(0, alphabetIndexAt(-90f, 300f, 6))
        assertEquals(5, alphabetIndexAt(900f, 300f, 6))
        assertEquals(0, alphabetIndexAt(90f, 0f, 0))
        assertEquals(0f, alphabetDriftAt(-90f, 0f, -40f, true), 0f)
    }

    @Test fun driftAndSelectionScaleWithPreviewOrDisplayDensity() {
        for (scale in listOf(.5f, 1f, 2.5f)) {
            val drift = alphabetDriftAt(-90f * scale, 300f * scale, 0f, true)
            assertEquals(-90f * scale, drift, .001f)
            assertEquals(1, alphabetIndexAt(-35f * scale - drift, 300f * scale, 6))
        }
    }
}
