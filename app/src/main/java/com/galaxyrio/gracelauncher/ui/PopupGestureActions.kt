package com.galaxyrio.gracelauncher.ui

import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.PopupGesture
import com.galaxyrio.gracelauncher.data.PopupGestureAction
import com.galaxyrio.gracelauncher.data.PopupGestureScope
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderId
import com.galaxyrio.gracelauncher.data.RecentlyInstalledFolderId
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.ShortcutStatus
import com.galaxyrio.gracelauncher.data.WorkProfileFolderId
import com.galaxyrio.gracelauncher.data.notifications.AppNotification

internal data class PopupGestureTarget(val app: LauncherApp, val notification: AppNotification? = null)

/** Notification operations use the touched revision; opening a pop-up uses the row owner. */
internal fun LauncherUiState.popupGestureTarget(owner: LauncherApp, notification: AppNotification?): PopupGestureTarget? {
    if (notification == null) return findItem(owner.key)?.let { PopupGestureTarget(it) }
    val current = notifications[notification.packageName]?.firstOrNull {
        it.key == notification.key && it.revision == notification.revision
    } ?: return null
    val app = apps.firstOrNull {
        it.packageName == current.packageName && it.user == null && it.shortcut == null && it.folderId == null
    } ?: return null
    return PopupGestureTarget(app, current)
}

internal fun LauncherUiState.popupGestureFolder(app: LauncherApp): LauncherFolder? = when (app.folderId) {
    PrivateSpaceFolderId -> privateFolder
    WorkProfileFolderId -> workFolder
    RecentlyInstalledFolderId -> recentlyInstalledFolder(app.label)
    else -> folders.firstOrNull { it.id == app.folderId }
}

internal fun LauncherUiState.firstGestureNotification(target: PopupGestureTarget): AppNotification? =
    target.notification ?: notificationsFor(target.app, expanded = true).firstOrNull()

internal fun LauncherUiState.allGestureNotifications(target: PopupGestureTarget): List<AppNotification> =
    if (target.app.folderId == null) ownNotificationsFor(target.app, expanded = true, includeHidden = true)
    else notificationsFor(target.app, expanded = true, includeHidden = true)

/** Dismiss-all means the whole row (every app in a folder), even when the swipe starts on one preview line. */
private fun LauncherUiState.dismissAllTarget(owner: LauncherApp, target: PopupGestureTarget): PopupGestureTarget =
    if (target.notification == null) target else popupGestureTarget(owner, null) ?: target

private fun LauncherUiState.needsPopupShortcuts(owner: LauncherApp): Boolean {
    val custom = popups[owner.key]
    return owner.folderId == null && (custom == null || custom.firstOrNull()?.let {
        it.widget == null && findItem(it.key) == null
    } == true)
}

/** Optimistic only while shortcuts are loading; execution checks again before falling through. */
internal fun LauncherUiState.resolvePopupGesture(
    owner: LauncherApp, gesture: PopupGesture, notification: AppNotification?,
    scope: PopupGestureScope = PopupGestureScope.List, canOpenPopup: Boolean = true,
    cachedShortcuts: (LauncherApp) -> ShortcutResult?,
): PopupGestureAction? {
    val options = settings.shortcutsFolders
    val plan = options.gesture(gesture, scope)
    if (plan.selected.isEmpty() || (scope == PopupGestureScope.InsidePopup && owner.shortcut != null)) return null
    val target = popupGestureTarget(owner, notification) ?: return null
    if (!options.enabled && target.app.folderId == null) return null
    return plan.enabledActions.firstOrNull { action ->
        if (!action.appliesTo(scope, notification, canOpenPopup)) return@firstOrNull false
        when (action) {
            PopupGestureAction.OpenPopup -> owner.folderId == null || popupGestureFolder(owner) != null
            PopupGestureAction.OpenFirstItem -> {
                val needsShortcuts = needsPopupShortcuts(target.app)
                val cached = if (needsShortcuts) cachedShortcuts(target.app) else null
                firstPopupApp(target.app, cached?.asApps(target.app).orEmpty()) != null ||
                    (needsShortcuts && (cached == null || cached.status == ShortcutStatus.Loading))
            }
            PopupGestureAction.OpenFirstNotification -> firstGestureNotification(target)?.canOpen == true
            PopupGestureAction.DismissFirstNotification, PopupGestureAction.DismissNotification ->
                firstGestureNotification(target)?.canDismiss == true
            PopupGestureAction.DismissAllNotifications -> allGestureNotifications(dismissAllTarget(owner, target)).any { it.canDismiss }
        }
    }
}

private fun PopupGestureAction.appliesTo(scope: PopupGestureScope, notification: AppNotification?, canOpenPopup: Boolean): Boolean {
    val card = scope == PopupGestureScope.InsidePopup && notification != null
    return when (this) {
        PopupGestureAction.DismissNotification -> card
        PopupGestureAction.OpenPopup -> canOpenPopup && !card
        PopupGestureAction.OpenFirstItem -> !card
        else -> true
    }
}

/** Only a clear spanning multiple apps needs the optional confirmation. */
internal fun notificationDismissNeedsConfirmation(notifications: List<AppNotification>): Boolean =
    notifications.asSequence().filter { it.canDismiss }.map { it.packageName }.distinct().take(2).count() > 1

/** Both list levels and their settings preview use the same ordered actions. */
internal suspend fun performPopupGesture(
    owner: LauncherApp, gesture: PopupGesture, notification: AppNotification?,
    state: () -> LauncherUiState, actions: () -> LauncherActions,
    canContinue: () -> Boolean,
    openPopup: (LauncherApp) -> Boolean, launchItem: (LauncherApp) -> Boolean,
    confirmDismiss: suspend (List<AppNotification>) -> Boolean = { true },
    scope: PopupGestureScope = PopupGestureScope.List, canOpenPopup: Boolean = true,
    onNotificationOpened: () -> Unit = {},
): Boolean {
    if (scope == PopupGestureScope.InsidePopup && owner.shortcut != null) return false
    val plan = state().settings.shortcutsFolders.gesture(gesture, scope)
    fun current(): Pair<LauncherUiState, PopupGestureTarget>? {
        if (!canContinue()) return null
        val latest = state()
        val options = latest.settings.shortcutsFolders
        if (options.gesture(gesture, scope) != plan) return null
        val target = latest.popupGestureTarget(owner, notification) ?: return null
        if (!options.enabled && target.app.folderId == null) return null
        return latest to target
    }
    return plan.performFirstAvailable { action ->
        if (!action.appliesTo(scope, notification, canOpenPopup)) return@performFirstAvailable false
        val (latest, target) = current() ?: return@performFirstAvailable false
        when (action) {
            PopupGestureAction.OpenPopup -> openPopup(owner)
            PopupGestureAction.OpenFirstItem -> {
                val defaults = if (latest.needsPopupShortcuts(target.app))
                    actions().shortcuts(target.app).asApps(target.app) else emptyList()
                // Shortcut lookup can suspend across HOME, navigation, settings or profile changes.
                val (refreshed, refreshedTarget) = current() ?: return@performFirstAvailable false
                refreshed.firstPopupApp(refreshedTarget.app, defaults)?.let(launchItem) == true
            }
            PopupGestureAction.OpenFirstNotification -> latest.firstGestureNotification(target)?.let {
                (it.canOpen && actions().openNotification(it.key, it.revision)).also { opened ->
                    if (opened) onNotificationOpened()
                }
            } == true
            PopupGestureAction.DismissFirstNotification, PopupGestureAction.DismissNotification -> latest.firstGestureNotification(target)?.let {
                it.canDismiss && actions().dismissNotification(it.key, it.revision)
            } == true
            PopupGestureAction.DismissAllNotifications -> {
                val pending = latest.allGestureNotifications(latest.dismissAllTarget(owner, target)).filter { it.canDismiss }
                // A declined confirmation ends the gesture instead of falling through to the next action.
                if (plan.confirmDismissAll && notificationDismissNeedsConfirmation(pending) && !confirmDismiss(pending)) return@performFirstAvailable true
                val (refreshed, refreshedTarget) = if (pending.isEmpty()) latest to target
                    else current() ?: return@performFirstAvailable false
                var dismissed = false
                refreshed.allGestureNotifications(refreshed.dismissAllTarget(owner, refreshedTarget)).filter { it.canDismiss }.groupBy { it.packageName }
                    .forEach { (pkg, list) ->
                        // App-wide clear also takes group summaries the list hides; per-key is the fallback.
                        if (actions().dismissAllNotifications(pkg)) dismissed = true
                        else list.forEach { if (actions().dismissNotification(it.key, it.revision)) dismissed = true }
                    }
                dismissed
            }
        }
    }
}
