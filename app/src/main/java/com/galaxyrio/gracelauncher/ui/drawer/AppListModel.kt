package com.galaxyrio.gracelauncher.ui.drawer

import com.galaxyrio.gracelauncher.data.LauncherAlphabet
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherAppOrder
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.FolderPlacement

const val FolderSection = "◇"

sealed interface DrawerItem {
    val key: String
    val section: String

    data class Header(override val section: String) : DrawerItem {
        override val key = "header:$section"
    }

    data class App(val app: LauncherApp) : DrawerItem {
        override val key = app.key
        override val section = app.section
    }
    data class Folder(val folder: LauncherFolder) : DrawerItem {
        override val key = "folder:${folder.id}"
        override val section = FolderSection
    }
    data class PrivateApp(val app: LauncherApp) : DrawerItem {
        override val key = app.key
        override val section = FolderSection
    }
    data object PrivateStatus : DrawerItem {
        override val key = "private-space-status"
        override val section = FolderSection
    }
}

class AppListModel(apps: List<LauncherApp>, folders: List<LauncherFolder> = emptyList(),
    privateFolder: LauncherFolder? = null, privateExpanded: Boolean = false, privateApps: List<LauncherApp> = emptyList()) {
    private val grouped = apps.sortedWith(LauncherAppOrder).groupBy(LauncherApp::section)
    private val appLetters = LauncherAlphabet.filter { it != "#" && it in grouped } +
        grouped.keys.filter { it !in LauncherAlphabet }.sorted() +
        if ("#" in grouped) listOf("#") else emptyList()
    private val drawerFolders = folders.filter { it.placement == FolderPlacement.AppList }
    val letters: List<String> = appLetters + if (drawerFolders.isEmpty() && privateFolder == null) emptyList() else listOf(FolderSection)
    val items: List<DrawerItem> = buildList {
        appLetters.forEach { letter ->
            add(DrawerItem.Header(letter))
            grouped.getValue(letter).forEach { add(DrawerItem.App(it)) }
        }
        if (drawerFolders.isNotEmpty() || privateFolder != null) {
            add(DrawerItem.Header(FolderSection))
            drawerFolders.forEach { add(DrawerItem.Folder(it)) }
            // This synthetic folder is always last and never enters the stored folder table.
            if (privateFolder != null) {
                add(DrawerItem.Folder(privateFolder))
                if (privateExpanded) {
                    privateApps.forEach { add(DrawerItem.PrivateApp(it)) }
                    if (privateApps.isEmpty()) add(DrawerItem.PrivateStatus)
                }
            }
        }
    }
    private val sectionIndices = items.mapIndexedNotNull { index, item ->
        (item as? DrawerItem.Header)?.let { it.section to index }
    }.toMap()

    /** Every index refers to the same complete list, even while other groups are hidden. */
    fun indexOfSection(letter: String): Int =
        sectionIndices[letter] ?: 0
}
