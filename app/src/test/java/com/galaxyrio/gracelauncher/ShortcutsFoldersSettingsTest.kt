package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.NotificationDisplay
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings
import org.junit.Assert.*
import org.junit.Test

class ShortcutsFoldersSettingsTest {
    @Test fun defaultsHideSilentAndShowOrdinaryNotifications() {
        val settings = ShortcutsFoldersSettings()
        assertTrue(settings.enabled)
        assertFalse(settings.swipeLeftToOpenFirst)
        listOf(false, true).forEach { expanded ->
            assertFalse(settings.showsNotification(silent = true, expanded))
            assertTrue(settings.showsNotification(silent = false, expanded))
        }
    }

    @Test fun bothKindsHaveIndependentNormalExpandedAndHiddenModes() {
        NotificationDisplay.entries.forEach { silent ->
            NotificationDisplay.entries.forEach { normal ->
                val settings = ShortcutsFoldersSettings(silentNotifications = silent, normalNotifications = normal)
                listOf(false, true).forEach { expanded ->
                    assertEquals(silent == NotificationDisplay.Normal || (expanded && silent == NotificationDisplay.ExpandedOnly),
                        settings.showsNotification(silent = true, expanded))
                    assertEquals(normal == NotificationDisplay.Normal || (expanded && normal == NotificationDisplay.ExpandedOnly),
                        settings.showsNotification(silent = false, expanded))
                }
            }
        }
    }

    @Test fun disablingPopupsHidesPreviewsWithoutLosingTheirPreferences() {
        val enabled = ShortcutsFoldersSettings(swipeLeftToOpenFirst = true, silentNotifications = NotificationDisplay.Normal,
            normalNotifications = NotificationDisplay.ExpandedOnly)
        val disabled = enabled.copy(enabled = false)
        listOf(false, true).forEach { silent ->
            listOf(false, true).forEach { expanded -> assertFalse(disabled.showsNotification(silent, expanded)) }
        }
        assertEquals(enabled, disabled.copy(enabled = true))
    }
}
