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
import org.json.JSONObject

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
        val reopened = Room.databaseBuilder(context, LauncherDatabase::class.java, databaseName)
            .addMigrations(LauncherDatabase.Migration1To2, LauncherDatabase.Migration2To3).build()
        database = reopened
        return LauncherSettingsRepository(reopened)
    }

    @Test
    fun versionOneMigratesWithoutLosingSettingsHiddenAppsOrFolderOrder() = runBlocking {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.galaxyrio.gracelauncher.data.LauncherDatabase/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(databaseName, 0, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                fun execute(sql: String) = legacy.execSQL(sql.replace('$' + "{TABLE_NAME}", table))
                execute(entity.getString("createSql"))
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) execute(indices.getJSONObject(i).getString("createSql"))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) legacy.execSQL(setup.getString(index))
            legacy.execSQL("INSERT INTO launcher_settings VALUES (0, 0, 1, 0, 0, -14129574, 'Dark')")
            legacy.execSQL("INSERT INTO hidden_apps VALUES ('mail/MailActivity')")
            legacy.execSQL("INSERT INTO folders VALUES ('work', 'Work', 'Favorites')")
            legacy.execSQL("INSERT INTO folder_apps VALUES ('work', 'notes/NotesActivity', 0)")
            legacy.execSQL("INSERT INTO folder_apps VALUES ('work', 'mail/MailActivity', 1)")
            legacy.version = 1
        }
        val repository = openRepository()
        val migrated = withTimeout(10_000) { repository.snapshots.first() }
        assertEquals(LauncherSettings(calendarAgenda = false, allowHapticFeedback = false,
            useDynamicColors = false, themeColor = -14129574, darkMode = ThemeMode.Dark), migrated.settings)
        assertEquals(setOf("mail/MailActivity"), migrated.hiddenAppKeys)
        assertEquals(listOf(LauncherFolder("work", "Work", listOf("notes/NotesActivity", "mail/MailActivity"),
            FolderPlacement.Favorites)), migrated.folders)
        repository.mutateSettings { it.copy(iconPackPackage = "me.morirain.dev.iconpack.pure") }
        val reopened = withTimeout(10_000) { openRepository().snapshots.first() }
        assertEquals(migrated.copy(settings = migrated.settings.copy(iconPackPackage = "me.morirain.dev.iconpack.pure")), reopened)
    }

    @Test
    fun versionTwoPreservesIconPackAndAddsPersistentMediaPreference() = runBlocking {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.galaxyrio.gracelauncher.data.LauncherDatabase/2.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(databaseName, 0, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                fun execute(sql: String) = legacy.execSQL(sql.replace('$' + "{TABLE_NAME}", table))
                execute(entity.getString("createSql"))
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) execute(indices.getJSONObject(i).getString("createSql"))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) legacy.execSQL(setup.getString(index))
            legacy.execSQL("INSERT INTO launcher_settings VALUES (0, 0, 1, 0, 0, -14129574, 'Dark', 'me.morirain.dev.iconpack.pure')")
            legacy.version = 2
        }
        val repository = openRepository()
        val settings = withTimeout(10_000) { repository.snapshots.first().settings }
        assertTrue(settings.mediaPlayer)
        assertEquals("me.morirain.dev.iconpack.pure", settings.iconPackPackage)
        repository.mutateSettings { it.copy(mediaPlayer = false) }
        assertEquals(settings.copy(mediaPlayer = false), withTimeout(10_000) { openRepository().snapshots.first().settings })
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
            iconPackPackage = "me.morirain.dev.iconpack.pure",
            mediaPlayer = false,
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
