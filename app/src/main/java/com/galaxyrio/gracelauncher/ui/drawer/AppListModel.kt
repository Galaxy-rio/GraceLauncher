package com.galaxyrio.gracelauncher.ui.drawer

import com.galaxyrio.gracelauncher.data.LauncherAlphabet
import com.galaxyrio.gracelauncher.data.LauncherApp

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
}

class AppListModel(apps: List<LauncherApp>) {
    private val grouped = apps.groupBy(LauncherApp::section)
    val letters: List<String> = LauncherAlphabet.filter(grouped::containsKey)
    val items: List<DrawerItem> = buildList {
        letters.forEach { letter ->
            add(DrawerItem.Header(letter))
            grouped.getValue(letter).forEach { add(DrawerItem.App(it)) }
        }
    }
    val headerIndices: Map<String, Int> = items.mapIndexedNotNull { index, item ->
        if (item is DrawerItem.Header) item.section to index else null
    }.toMap()
}
