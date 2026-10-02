package com.galaxyrio.gracelauncher.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "launcher_settings")
data class LauncherSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val calendarAgenda: Boolean,
    val showBatteryPercentage: Boolean,
    val allowHapticFeedback: Boolean,
    val useDynamicColors: Boolean,
    val themeColor: Int,
    val darkMode: String,
    val iconPackPackage: String? = null,
    @ColumnInfo(defaultValue = "1") val mediaPlayer: Boolean = true,
    @ColumnInfo(defaultValue = "0") val weatherEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "7") val weatherForecastDays: Int = 7,
    val weatherLocationId: String? = null,
    val clockAppKey: String? = null,
    val clockStyleJson: String? = null,
)

@Entity(tableName = "hidden_apps")
data class HiddenAppEntity(@PrimaryKey val appKey: String)

@Entity(tableName = "folders")
data class LauncherFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val placement: String,
)

@Entity(
    tableName = "folder_apps",
    primaryKeys = ["folderId", "appKey"],
    foreignKeys = [ForeignKey(
        entity = LauncherFolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("folderId")],
)
data class FolderAppEntity(val folderId: String, val appKey: String, val position: Int)

data class FolderWithApps(
    @Embedded val folder: LauncherFolderEntity,
    @Relation(parentColumn = "id", entityColumn = "folderId") val apps: List<FolderAppEntity>,
)

@Dao
abstract class LauncherSettingsDao {
    @Query("SELECT * FROM launcher_settings WHERE id = 0")
    abstract fun observeSettings(): Flow<LauncherSettingsEntity?>

    @Query("SELECT * FROM launcher_settings WHERE id = 0")
    abstract suspend fun readSettings(): LauncherSettingsEntity?

    @Query("SELECT appKey FROM hidden_apps ORDER BY appKey")
    abstract fun observeHiddenApps(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE, id")
    abstract fun observeFolders(): Flow<List<FolderWithApps>>

    @Upsert
    abstract suspend fun saveSettings(settings: LauncherSettingsEntity)

    @Query("DELETE FROM hidden_apps")
    protected abstract suspend fun clearHiddenApps()

    @Insert
    protected abstract suspend fun insertHiddenApps(apps: List<HiddenAppEntity>)

    @Transaction
    open suspend fun replaceHiddenApps(apps: List<HiddenAppEntity>) {
        clearHiddenApps()
        insertHiddenApps(apps)
    }

    @Upsert
    protected abstract suspend fun upsertFolder(folder: LauncherFolderEntity)

    @Query("DELETE FROM folder_apps WHERE folderId = :folderId")
    protected abstract suspend fun clearFolderApps(folderId: String)

    @Insert
    protected abstract suspend fun insertFolderApps(apps: List<FolderAppEntity>)

    @Transaction
    open suspend fun saveFolder(folder: LauncherFolderEntity, apps: List<FolderAppEntity>) {
        upsertFolder(folder)
        clearFolderApps(folder.id)
        insertFolderApps(apps)
    }

    @Query("DELETE FROM folders WHERE id = :folderId")
    abstract suspend fun deleteFolder(folderId: String)
}

@Database(
    entities = [LauncherSettingsEntity::class, HiddenAppEntity::class,
        LauncherFolderEntity::class, FolderAppEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun settingsDao(): LauncherSettingsDao

    companion object {
        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE launcher_settings ADD COLUMN iconPackPackage TEXT DEFAULT NULL")
            }
        }

        val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE launcher_settings ADD COLUMN mediaPlayer INTEGER NOT NULL DEFAULT 1")
            }
        }

        val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE launcher_settings ADD COLUMN weatherEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE launcher_settings ADD COLUMN weatherForecastDays INTEGER NOT NULL DEFAULT 7")
                db.execSQL("ALTER TABLE launcher_settings ADD COLUMN weatherLocationId TEXT DEFAULT NULL")
            }
        }

        val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE launcher_settings ADD COLUMN clockAppKey TEXT DEFAULT NULL")
            }
        }

        @Volatile private var instance: LauncherDatabase? = null

        val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE launcher_settings ADD COLUMN clockStyleJson TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): LauncherDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LauncherDatabase::class.java,
                "grace_launcher.db",
            ).addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6).build().also { instance = it }
        }
    }
}
