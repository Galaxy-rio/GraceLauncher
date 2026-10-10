package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.data.icons.pebbleShapePower
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class IconShapeParametersTest {
    @Test fun themingDefaultsToOffButExplicitChoicesRemainEnabled() {
        assertFalse(IconDesign().themeIcons)
        assertFalse(IconDesign.defaults().themeIcons)
        assertTrue(IconDesign.defaults(themedIcons = true).themeIcons)
        assertTrue(IconDesign(themeIcons = true).withThemeDefaults(IconDesign.defaults()).themeIcons)
    }

    @Test fun shapeParametersAreClampedIndependently() {
        val normalized = IconDesign(pebbleRoundness = -20, squareCornerRadius = 150).normalized()
        assertEquals(0, normalized.pebbleRoundness)
        assertEquals(100, normalized.squareCornerRadius)
        val opposite = IconDesign(pebbleRoundness = 150, squareCornerRadius = -20).normalized()
        assertEquals(100, opposite.pebbleRoundness)
        assertEquals(0, opposite.squareCornerRadius)
    }

    @Test fun pebbleMovesContinuouslyFromBoxyToCircular() {
        val diagonal = sqrt(0.5)
        val extents = (0..100).map { diagonal.pow(pebbleShapePower(it)) }
        assertTrue(extents.zipWithNext().all { (previous, next) -> next < previous })
        assertEquals(diagonal, extents.last(), 0.000001)
        assertEquals(pebbleShapePower(0), pebbleShapePower(-1), 0.0)
        assertEquals(pebbleShapePower(100), pebbleShapePower(101), 0.0)
    }

    @Test fun defaultPebbleIsRounderThanTheOldFixedFourthPowerSquircle() {
        assertTrue(pebbleShapePower(IconDesign.DefaultPebbleRoundness) > 0.5)
        assertEquals(0, IconDesign().squareCornerRadius)
    }

    @Test fun switchingShapesKeepsTheirIndividualSliderValues() {
        val pebble = IconDesign(shape = IconShape.Pebble, pebbleRoundness = 33, squareCornerRadius = 58)
        assertEquals(pebble, pebble.copy(shape = IconShape.Square).normalized().copy(shape = IconShape.Pebble))
    }
}
