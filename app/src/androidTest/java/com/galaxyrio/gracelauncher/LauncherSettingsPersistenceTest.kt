package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.ThemeMode
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherSettingsPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "launcher-settings-test-${UUID.randomUUID()}.db"
    private var database: LauncherDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openRepository(): LauncherSettingsRepository {
        database?.close()
        val reopened = Room.databaseBuilder(context, LauncherDatabase::class.java, databaseName).build()
        database = reopened
        return LauncherSettingsRepository(reopened)
    }

    @Test
    fun settingsHiddenAppsAndOrderedFoldersSurviveDatabaseRecreation() = runBlocking {
        val repository = openRepository()
        val settings = LauncherSettings(
            calendarAgenda = false,
            showBatteryPercentage = false,
            allowHapticFeedback = false,
            useDynamicColors = false,
            themeColor = 0xFF28665A.toInt(),
            darkMode = ThemeMode.Dark,
        )
        val folder = LauncherFolder(
            id = "work",
            name = "Work",
            appKeys = listOf("mail/MailActivity", "notes/NotesActivity", "mail/MailActivity"),
            placement = FolderPlacement.Favorites,
        )
        repository.updateSettings(settings)
        repository.setHiddenApps(setOf("mail/MailActivity", "hidden/Activity"))
        repository.saveFolder(folder)

        val snapshot = withTimeout(10_000) { openRepository().snapshots.first() }
        assertEquals(settings, snapshot.settings)
        assertEquals(setOf("mail/MailActivity", "hidden/Activity"), snapshot.hiddenAppKeys)
        assertEquals(listOf(folder.copy(appKeys = folder.appKeys.distinct())), snapshot.folders)
    }

    @Test
    fun queuedSettingTransformsPreserveEachOtherBeforeAnyUiEmission() = runBlocking {
        val repository = openRepository()
        val original = LauncherSettings(themeColor = 0xFF28665A.toInt())
        repository.updateSettings(original)
        // Both callbacks exist before either commit reaches a UI collector.
        val disableAgenda: (LauncherSettings) -> LauncherSettings = { it.copy(calendarAgenda = false) }
        val disableBattery: (LauncherSettings) -> LauncherSettings = { it.copy(showBatteryPercentage = false) }
        coroutineScope {
            launch { repository.mutateSettings(disableAgenda) }
            launch { repository.mutateSettings(disableBattery) }
        }
        val saved = withTimeout(10_000) { openRepository().snapshots.first().settings }
        assertEquals(original.copy(calendarAgenda = false, showBatteryPercentage = false), saved)
    }

    @Test
    fun editingMembershipReplacesRowsAndDeletingFolderDoesNotResurrectOldMembers() = runBlocking {
        val repository = openRepository()
        val original = LauncherFolder("tools", "Tools", listOf("one/A", "two/B"), FolderPlacement.AppList)
        repository.saveFolder(original)
        val edited = original.copy(name = " Daily ", appKeys = listOf("three/C", "two/B"))
        repository.saveFolder(edited)
        assertEquals(
            listOf(edited.copy(name = "Daily")),
            withTimeout(10_000) { repository.snapshots.first().folders },
        )
        repository.deleteFolder(original.id)
        assertTrue(withTimeout(10_000) { repository.snapshots.first().folders }.isEmpty())
        repository.saveFolder(original.copy(appKeys = emptyList()))
        val reopened = withTimeout(10_000) { openRepository().snapshots.first() }
        assertTrue(reopened.folders.single().appKeys.isEmpty())
    }

    @Test
    fun hiddenAppsKeepFavoriteAndFolderMembershipAndCanBeRestored() = runBlocking {
        val app = LauncherApp(ComponentName("hidden", "hidden.Activity"), "Hidden", icon = null)
        val folder = LauncherFolder("folder", "Folder", listOf(app.key), FolderPlacement.Favorites)
        val repository = openRepository()
        repository.saveFolder(folder)
        repository.setHiddenApps(setOf(app.key))
        val snapshot = withTimeout(10_000) { repository.snapshots.first() }
        val state = LauncherUiState(
            apps = listOf(app), favoriteKeys = setOf(app.key),
            hiddenAppKeys = snapshot.hiddenAppKeys, folders = snapshot.folders,
        )
        assertTrue(state.visibleApps.isEmpty())
        assertTrue(state.favoriteApps.isEmpty())
        assertTrue(app.key in state.favoriteKeys)
        assertEquals(listOf(app.key), state.folders.single().appKeys)

        repository.setHiddenApps(emptySet())
        val restored = state.copy(hiddenAppKeys = withTimeout(10_000) { repository.snapshots.first().hiddenAppKeys })
        assertEquals(listOf(app), restored.visibleApps)
        assertEquals(listOf(app), restored.favoriteApps)
        assertEquals(listOf(app.key), restored.folders.single().appKeys)
    }

    @Test
    fun anEmptyDatabaseUsesDefaultsAndDoesNotResetLegacyPreferences() = runBlocking {
        val favorites = context.getSharedPreferences("grace_launcher_preferences", 0)
        val appearance = context.getSharedPreferences("launcher_appearance", 0)
        val favoritesBefore = favorites.all
        val appearanceBefore = appearance.all
        val snapshot = withTimeout(10_000) { openRepository().snapshots.first() }
        assertEquals(LauncherSettings(), snapshot.settings)
        assertTrue(snapshot.hiddenAppKeys.isEmpty())
        assertTrue(snapshot.folders.isEmpty())
        assertEquals(favoritesBefore, favorites.all)
        assertEquals(appearanceBefore, appearance.all)
    }

    @Test
    fun appsDoNotFlashBeforeSettingsLoadOrAfterAReadFailure() {
        val app = LauncherApp(ComponentName("one", "one.Activity"), "One", icon = null)
        val loading = LauncherUiState(apps = listOf(app), favoriteKeys = setOf(app.key), isLoadingSettings = true)
        assertTrue(loading.visibleApps.isEmpty())
        assertTrue(loading.favoriteApps.isEmpty())
        assertTrue(loading.copy(isLoadingSettings = false, settingsLoadFailed = true).visibleApps.isEmpty())
        assertFalse(loading.copy(isLoadingSettings = false).visibleApps.isEmpty())
    }
}
