package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.platform.WidgetReconfigurationRoute
import com.galaxyrio.gracelauncher.platform.widgetReconfigurationRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure routing checks; no device, widget binding, installation, or storage changes. */
class WidgetConfigurationRoutingTest {
    @Test fun waitsForBothResumeAndWindowFocus() {
        for (sameProfile in listOf(false, true)) {
            for ((resumed, focused) in listOf(false to false, false to true, true to false)) {
                assertEquals(WidgetReconfigurationRoute.Wait,
                    widgetReconfigurationRoute(true, resumed, focused, sameProfile))
            }
        }
    }

    @Test fun sameProfileUsesExplicitForegroundConfiguration() {
        assertEquals(WidgetReconfigurationRoute.Direct,
            widgetReconfigurationRoute(true, true, true, true))
    }

    @Test fun workProfileAlwaysUsesHost() {
        assertEquals(WidgetReconfigurationRoute.Host,
            widgetReconfigurationRoute(true, true, true, false))
    }

    @Test fun returningFromLaunchedConfigurationDoesNotLaunchAgain() {
        for (sameProfile in listOf(false, true)) {
            assertEquals(WidgetReconfigurationRoute.Wait,
                widgetReconfigurationRoute(false, true, true, sameProfile))
        }
    }
}
