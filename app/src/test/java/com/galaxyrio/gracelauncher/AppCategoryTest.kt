package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class AppCategoryTest {
    @Test fun conditionsUseExclusiveEndAndSupportMidnight() {
        assertTrue(AppCategory("a").isVisible(false, 0))
        val music = AppCategory("music", condition = CategoryCondition.Headphones)
        assertFalse(music.isVisible(false, 600))
        assertTrue(music.isVisible(true, 600))
        val daytime = AppCategory("day", condition = CategoryCondition.Time, startMinute = 480, endMinute = 1080)
        assertFalse(daytime.isVisible(false, 479))
        assertTrue(daytime.isVisible(false, 480))
        assertTrue(daytime.isVisible(false, 1079))
        assertFalse(daytime.isVisible(false, 1080))
        val overnight = daytime.copy(startMinute = 1320, endMinute = 420)
        assertTrue(overnight.isVisible(false, 1320))
        assertTrue(overnight.isVisible(false, 0))
        assertTrue(overnight.isVisible(false, 419))
        assertFalse(overnight.isVisible(false, 420))
        assertFalse(overnight.isVisible(false, 1319))
        assertTrue(daytime.copy(startMinute = 0, endMinute = 0).isVisible(false, 700))
    }

    @Test fun migrationKeepsMembershipOrderWidgetsGroupsAndUnavailableFavorites() {
        val layout = HomeLayout(homeOrder = listOf("grace:clock", "b", "widget:7", "folder:x", "a", "shortcut:x"),
            topOffsetDp = 24f, baselineOffsetDp = 52f, editingLocked = true)
        val migrated = migrateHomeCategories(LauncherSettings(homeLayout = layout),
            listOf("a", "b", "shortcut:x", "missing", "folder:x", PrivateSpaceFolderKey))
        val categories = requireNotNull(migrated.appCategories)
        assertEquals(listOf("b", "a", "shortcut:x", "missing"), categories.first().itemKeys)
        assertEquals(listOf("grace:clock", "category:favorites", "widget:7", "folder:x", "category:music"), migrated.homeLayout.homeOrder)
        assertEquals(layout.topOffsetDp, migrated.homeLayout.topOffsetDp)
        assertEquals(layout.baselineOffsetDp, migrated.homeLayout.baselineOffsetDp)
        assertTrue(migrated.homeLayout.editingLocked)
        assertEquals(CategoryCondition.Always, categories.first().condition)
        assertEquals(CategoryCondition.Headphones, categories.last().condition)
        assertEquals(migrated, migrateHomeCategories(migrated, listOf("different")))
    }

    @Test fun removingEveryFavoriteDoesNotRestoreLegacyMembers() {
        val migrated = migrateHomeCategories(LauncherSettings(), listOf("a"))
        val empty = migrated.copy(appCategories = migrated.appCategories!!.map { it.copy(itemKeys = emptyList()) })
        assertTrue(migrateHomeCategories(empty, listOf("a", "b")).appCategories!!.first().itemKeys.isEmpty())
    }

    @Test fun migrationWithoutExplicitHomeOrderKeepsLeadingProfileAndFolderPositions() {
        val migrated = migrateHomeCategories(LauncherSettings(), listOf(WorkProfileFolderKey, "a", "folder:x", "b"))
        assertEquals(listOf(WorkProfileFolderKey, "category:favorites", "folder:x", "category:music"),
            migrated.homeLayout.homeOrder!!.filterNot { it.startsWith("grace:") })
        assertEquals(listOf("a", "b"), migrated.appCategories!!.first().itemKeys)
    }

    @Test fun activeCategoriesExpandInPlaceAndOnlyTheFirstOccurrenceIsShown() {
        val favorites = AppCategory(FavoritesCategoryId, itemKeys = listOf("a", "folder:x", "missing"))
        val music = AppCategory(MusicCategoryId, itemKeys = listOf("a", "b", "folder:x", "shortcut:x"), condition = CategoryCondition.Headphones)
        val widget = PopupItem("widget:7", HomeLayout(widgetId = 7))
        val entries = listOf(PopupItem(favorites.key), PopupItem(music.key), widget, PopupItem("folder:x"))
        val on = expandHomeCategories(entries, listOf(favorites, music), true, 600) { it != "missing" }
        assertEquals(listOf("a", "folder:x", "b", "shortcut:x", "widget:7"), on.items.map { it.key })
        assertEquals(favorites.key, on.owners["a"])
        assertEquals(music.key, on.owners["b"])
        assertEquals(widget, on.items.last())
        val off = expandHomeCategories(entries, listOf(favorites, music), false, 600) { it != "missing" }
        assertEquals(listOf("a", "folder:x", "widget:7"), off.items.map { it.key })
        val reversed = expandHomeCategories(listOf(PopupItem(music.key), PopupItem(favorites.key)), listOf(favorites, music), true, 600) { it != "missing" }
        assertEquals(listOf("a", "b", "folder:x", "shortcut:x"), reversed.items.map { it.key })
        assertEquals(music.key, reversed.owners["a"])
    }

    @Test fun anInactiveEarlierCategoryDoesNotSuppressLaterMembers() {
        val sleeping = AppCategory("sleeping", itemKeys = listOf("a"), condition = CategoryCondition.Time, startMinute = 1200, endMinute = 420)
        val always = AppCategory("always", itemKeys = listOf("a"))
        val result = expandHomeCategories(listOf(PopupItem(sleeping.key), PopupItem(always.key)), listOf(sleeping, always), false, 600) { true }
        assertEquals(listOf("a"), result.items.map { it.key })
        assertEquals(always.key, result.owners["a"])
    }
}
