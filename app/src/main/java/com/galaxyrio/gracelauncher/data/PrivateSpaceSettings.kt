package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

const val PrivateSpaceFolderId = "private-space"
const val PrivateSpaceFolderKey = "folder:$PrivateSpaceFolderId"
const val PrivateSpaceDefaultName = "private"

enum class PrivateSpaceDisplay { List, Folder, NormalApp }

/** Presentation only. Credentials and the profile's lock are always owned by Android. */
data class PrivateSpaceSettings(
    val enabled: Boolean = true,
    val passwordProtected: Boolean = true,
    val showIndicator: Boolean = true,
    val lockImmediately: Boolean = true,
    val display: PrivateSpaceDisplay = PrivateSpaceDisplay.Folder,
    val name: String = PrivateSpaceDefaultName,
    val appOrder: List<String> = emptyList(),
) {
    val protectsApps: Boolean get() = display != PrivateSpaceDisplay.NormalApp && passwordProtected
    val exposesApps: Boolean get() = enabled && !protectsApps
    val locksOnExit: Boolean get() = enabled && display != PrivateSpaceDisplay.NormalApp && lockImmediately
    fun folder(apps: List<LauncherApp> = emptyList()) = LauncherFolder(
        PrivateSpaceFolderId, name, orderedApps(apps).map { it.key }, FolderPlacement.AppList,
    )

    fun orderedApps(apps: List<LauncherApp>): List<LauncherApp> {
        val byKey = apps.associateBy(LauncherApp::key)
        return appOrder.distinct().mapNotNull(byKey::get) + apps.sortedWith(LauncherAppOrder).filterNot { it.key in appOrder }
    }

    fun encode(): String = JSONObject().put("enabled", enabled).put("passwordProtected", passwordProtected)
        .put("showIndicator", showIndicator).put("lockImmediately", lockImmediately)
        .put("display", display.name).put("name", name).put("appOrder", JSONArray(appOrder.distinct())).toString()

    companion object {
        fun decode(json: String?): PrivateSpaceSettings = runCatching {
            val value = JSONObject(json ?: return PrivateSpaceSettings())
            val order = value.optJSONArray("appOrder") ?: JSONArray()
            PrivateSpaceSettings(
                enabled = value.optBoolean("enabled", true),
                passwordProtected = value.optBoolean("passwordProtected", true),
                showIndicator = value.optBoolean("showIndicator", true),
                lockImmediately = value.optBoolean("lockImmediately", true),
                display = PrivateSpaceDisplay.entries.firstOrNull { it.name == value.optString("display") } ?: PrivateSpaceDisplay.Folder,
                name = value.optString("name", PrivateSpaceDefaultName).trim().ifBlank { PrivateSpaceDefaultName },
                appOrder = (0 until order.length()).mapNotNull { (order.opt(it) as? String)?.takeIf(String::isNotBlank) }.distinct(),
            )
        }.getOrDefault(PrivateSpaceSettings())
    }
}
