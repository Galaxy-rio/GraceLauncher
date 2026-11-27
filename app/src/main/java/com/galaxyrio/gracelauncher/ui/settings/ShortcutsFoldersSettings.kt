package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.NotificationDisplay
import com.galaxyrio.gracelauncher.data.PopupGesture
import com.galaxyrio.gracelauncher.data.PopupGestureAction
import com.galaxyrio.gracelauncher.data.PopupGestureActions
import com.galaxyrio.gracelauncher.data.PopupGestureScope
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol

@Composable
internal fun ShortcutsFoldersSettingsScreen(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    navigate: (SettingsPage) -> Unit) {
    val settings = uiState.settings.shortcutsFolders
    var requestAccess by rememberSaveable { mutableStateOf(false) }
    fun update(change: (ShortcutsFoldersSettings) -> ShortcutsFoldersSettings) =
        actions.updateSettings { it.copy(shortcutsFolders = change(it.shortcutsFolders)) }

    SettingsScaffold(stringResource(R.string.popups_title), "shortcuts_folders_settings", onBack) { padding ->
        SettingsList(padding) {
            item {
                SettingsFeatureBanner(stringResource(R.string.shortcuts_folders_enable), settings.enabled, "shortcuts_folders_enabled") { value ->
                    update { it.copy(enabled = value) }
                }
            }
            if (!uiState.media.hasAccess) item {
                SettingsActionItem(stringResource(R.string.notification_allow), stringResource(R.string.media_access_required),
                    0, 1, "notifications_access", leading = { LauncherIcon(LauncherSymbol.Notifications) }) { requestAccess = true }
                Spacer(Modifier.height(12.dp))
            }
            item { SettingsHeading(stringResource(R.string.popup_notifications_heading)) }
            item {
                NotificationDisplayItem(stringResource(R.string.popup_silent_notifications),
                    settings.silentNotifications, settings.enabled, 0, 6, "popup_silent") { mode ->
                    update { it.copy(silentNotifications = mode) }
                }
            }
            item {
                NotificationDisplayItem(stringResource(R.string.popup_normal_notifications),
                    settings.normalNotifications, settings.enabled, 1, 6, "popup_normal") { mode ->
                    update { it.copy(normalNotifications = mode) }
                }
            }
            item {
                SettingsToggleItem(stringResource(R.string.popup_notification_actions), null,
                    settings.notificationActions, 2, 6, "popup_notification_actions", enabled = settings.enabled) { value ->
                    update { it.copy(notificationActions = value) }
                }
            }
            item {
                SettingsToggleItem(stringResource(R.string.popup_notification_direct_reply), null,
                    settings.notificationDirectReply, 3, 6, "popup_notification_direct_reply", enabled = settings.enabled) { value ->
                    update { it.copy(notificationDirectReply = value) }
                }
            }
            item {
                SettingsToggleItem(stringResource(R.string.popup_notification_suggested_replies), null,
                    settings.notificationSuggestedReplies, 4, 6, "popup_notification_suggested_replies", enabled = settings.enabled) { value ->
                    update { it.copy(notificationSuggestedReplies = value) }
                }
            }
            item {
                SettingsToggleItem(stringResource(R.string.popup_notification_progress), null,
                    settings.notificationProgress, 5, 6, "popup_notification_progress", enabled = settings.enabled) { value ->
                    update { it.copy(notificationProgress = value) }
                }
            }
            item { SettingsHeading(stringResource(R.string.popup_gestures_heading)) }
            item {
                SettingsActionItem(stringResource(R.string.popup_list_gesture), null, 0, 2, "open_list_gesture") {
                    navigate(SettingsPage.ListPopupGestures)
                }
            }
            item {
                SettingsActionItem(stringResource(R.string.popup_inside_gesture), null, 1, 2, "open_inside_popup_gesture") {
                    navigate(SettingsPage.InsidePopupGestures)
                }
            }
        }
    }
    if (requestAccess) AlertDialog(
        onDismissRequest = { requestAccess = false },
        title = { Text(stringResource(R.string.notification_allow)) },
        text = { Text(stringResource(R.string.media_access_explanation)) },
        confirmButton = {
            TextButton(onClick = { requestAccess = false; actions.requestMediaAccess() }, modifier = Modifier.testTag("media_access_continue")) {
                Text(stringResource(R.string.media_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = { requestAccess = false }) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
internal fun PopupGestureSettingsScreen(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    scope: PopupGestureScope) {
    val settings = uiState.settings.shortcutsFolders
    val title = if (scope == PopupGestureScope.List) R.string.popup_list_gesture else R.string.popup_inside_gesture
    fun update(gesture: PopupGesture, change: (PopupGestureActions) -> PopupGestureActions) = actions.updateSettings {
        val current = it.shortcutsFolders
        it.copy(shortcutsFolders = current.withGesture(gesture, change(current.gesture(gesture, scope)), scope))
    }
    SettingsScaffold(stringResource(title), "popup_gestures_${scope.name}", onBack) { padding ->
        SettingsList(padding) {
            PopupGesture.entries.forEach { gesture ->
                item("popup_gesture:${gesture.id}") {
                    val options = settings.gesture(gesture, scope).normalized()
                    val order = if (scope == PopupGestureScope.InsidePopup) options.order
                        else options.order - PopupGestureAction.DismissNotification
                    // The list height is fixed, so follow the confirm row's real height when text wraps or scales.
                    val density = LocalDensity.current
                    var confirmHeight by remember { mutableStateOf(80.dp) }
                    Column {
                        Text(stringResource(when (gesture) {
                            PopupGesture.SwipeLeft -> R.string.grace_button_swipe_left
                            PopupGesture.SwipeRight -> R.string.grace_button_swipe_right
                            PopupGesture.DoubleTap -> R.string.grace_button_double_tap
                        }), Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                        ClockFieldOrder(order, options.selected, LocalSettingsStorageState.current.canEdit,
                            uiState.settings.allowHapticFeedback, "popup_gesture_${gesture.id}",
                            label = { action -> stringResource(when (action) {
                                PopupGestureAction.OpenPopup -> R.string.popup_action_open
                                PopupGestureAction.OpenFirstItem -> R.string.popup_action_first_item
                                PopupGestureAction.OpenFirstNotification -> R.string.popup_action_first_notification
                                PopupGestureAction.DismissFirstNotification -> R.string.popup_action_dismiss_first
                                PopupGestureAction.DismissAllNotifications -> R.string.popup_action_dismiss_all
                                PopupGestureAction.DismissNotification -> R.string.popup_action_dismiss_notification
                            }) },
                            onSelect = { action, checked -> update(gesture) { value ->
                                value.copy(selected = if (checked) value.selected + action else value.selected - action)
                            } },
                            onReorder = { order -> update(gesture) { it.copy(order = order) } },
                            modifier = Modifier.height(64.dp * order.size + 2.dp * (order.size - 1) +
                                if (PopupGestureAction.DismissAllNotifications in options.selected) confirmHeight else 0.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                            fieldSupportingContent = { action ->
                                if (action == PopupGestureAction.DismissAllNotifications && action in options.selected) {
                                    Box(Modifier.heightIn(min = 80.dp).onSizeChanged { confirmHeight = with(density) { it.height.toDp() } }
                                        .padding(start = 40.dp, end = 8.dp),
                                        contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        SettingsToggleItem(stringResource(R.string.popup_confirm_multiple_apps), null,
                                            options.confirmDismissAll, 0, 1, "popup_confirm_multiple_apps_${gesture.id}") { confirm ->
                                            update(gesture) { it.copy(confirmDismissAll = confirm) }
                                        }
                                    }
                                }
                            })
                        if (gesture == PopupGesture.DoubleTap && options.selected.isNotEmpty()) Text(
                            stringResource(R.string.popup_double_tap_delay),
                            Modifier.padding(horizontal = 4.dp, vertical = 8.dp).testTag("popup_double_tap_delay"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun NotificationDisplayItem(title: String, selected: NotificationDisplay, enabled: Boolean,
    index: Int, count: Int, tag: String, onSelect: (NotificationDisplay) -> Unit) {
    val canEdit = enabled && LocalSettingsStorageState.current.canEdit
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        enabled = canEdit,
        modifier = Modifier.testTag(tag),
        content = {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title)
                // M3 connected buttons have asymmetric end shapes and animate to
                // the checked shape, rather than sharing a segmented outline.
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    NotificationDisplay.entries.forEachIndexed { buttonIndex, mode ->
                        ToggleButton(
                            checked = selected == mode,
                            onCheckedChange = { onSelect(mode) },
                            enabled = canEdit,
                            shapes = when (buttonIndex) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                NotificationDisplay.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
                                .testTag("$tag:${mode.id}").semantics { role = Role.RadioButton },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(when (mode) {
                                NotificationDisplay.Normal -> R.string.popup_notification_normal
                                NotificationDisplay.ExpandedOnly -> R.string.popup_notification_expanded
                                NotificationDisplay.Hidden -> R.string.popup_notification_hidden
                            }), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        },
    )
}
