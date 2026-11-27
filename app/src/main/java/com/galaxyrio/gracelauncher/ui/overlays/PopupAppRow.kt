package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.IconDisplayStyle
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.PopupGesture
import com.galaxyrio.gracelauncher.data.PopupGestureAction
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.components.*
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.effectiveDisplay

@Composable
internal fun PopupAppRow(
    item: LauncherApp, folder: Boolean, showNames: Boolean, notification: AppNotification?,
    enabled: Boolean, resolve: (PopupGesture) -> PopupGestureAction?,
    perform: (PopupGesture, Rect) -> Unit, onOpenPopup: ((Rect) -> Unit)?,
    onLaunch: (Rect) -> Unit, onDetails: () -> Unit,
) {
    val display = item.effectiveDisplay(LocalLauncherAppearance.current)
    val hiddenIcon = display.style == IconDisplayStyle.Hidden
    val list = LocalListAppearance.current
    val tag = if (folder) "folder_app:${item.key}" else "shortcut:${item.shortcut?.id ?: item.key}"
    var anchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val trackAnchor = Modifier.onGloballyPositioned { anchor = it }
    fun bounds() = anchor?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero
    val swipeLeft = stringResource(R.string.grace_button_swipe_left)
    val swipeRight = stringResource(R.string.grace_button_swipe_right)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
            .semantics {
                if (!showNames && !hiddenIcon) contentDescription = item.label
                if (enabled) customActions = buildList {
                    if (resolve(PopupGesture.SwipeLeft) != null) add(CustomAccessibilityAction(swipeLeft) {
                        perform(PopupGesture.SwipeLeft, bounds()); true
                    })
                    if (resolve(PopupGesture.SwipeRight) != null) add(CustomAccessibilityAction(swipeRight) {
                        perform(PopupGesture.SwipeRight, bounds()); true
                    })
                }
            }
            .popupRowGestureInput(item.key, enabled, LocalPopupGestureSettings.current, resolve,
                perform = { perform(it, bounds()) })
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(enabled = enabled, onLongClick = onDetails, onClick = { onLaunch(bounds()) },
                onDoubleClick = if (resolve(PopupGesture.DoubleTap) != null) ({
                    perform(PopupGesture.DoubleTap, bounds())
                }) else null)
            .padding(horizontal = LauncherLayout.PopupContentInset, vertical = (list.appSpacing / 2f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!hiddenIcon) {
            AppIcon(item, trackAnchor, size = 36.dp, display = display)
            if (showNames || notification != null) Spacer(Modifier.width(list.iconNameGap.dp))
        }
        AppNotificationLabel(item.label, listOfNotNull(notification), showNames || hiddenIcon,
            modifier = Modifier.weight(1f), labelModifier = if (hiddenIcon) trackAnchor else Modifier,
            onWallpaper = false, maxLines = 2)
        if (notification != null && onOpenPopup != null) IconButton(
            onClick = { onOpenPopup(bounds()) }, enabled = enabled,
            modifier = Modifier.size(48.dp).testTag("popup_notification_arrow:${item.key}")
                .semantics { contentDescription = item.label },
        ) {
            LauncherIcon(LauncherSymbol.Chevron,
                Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Ltr) -90f else 90f))
        }
    }
}
