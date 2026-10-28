package com.galaxyrio.gracelauncher.ui.drawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.GraceButtonAction
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderId
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.FolderRow
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.components.LocalListAppearance
import com.galaxyrio.gracelauncher.ui.components.startPadding
import com.galaxyrio.gracelauncher.ui.components.endPadding
import com.galaxyrio.gracelauncher.ui.components.listLabelStyle
import com.galaxyrio.gracelauncher.ui.components.stableStatusBarInset
import com.galaxyrio.gracelauncher.ui.components.LauncherSearchBar
import com.galaxyrio.gracelauncher.ui.components.GraceButtonOrigin
import com.galaxyrio.gracelauncher.ui.components.GraceButtonTransition
import com.galaxyrio.gracelauncher.ui.components.GraceButtonTransitionOverlay
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

@Composable
fun AppDrawerScreen(
    model: AppListModel,
    listState: LazyListState,
    selectedLetter: String?,
    topSpace: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppDetails: (LauncherApp) -> Unit,
    onAppShortcuts: (LauncherApp, Rect) -> Unit,
    modifier: Modifier = Modifier,
    rowGestures: AppRowGestures = AppRowGestures(),
    highlightedAppKey: String? = null,
    onOpenFolder: (LauncherFolder, Rect) -> Unit = { _, _ -> },
    onEditFolder: (LauncherFolder) -> Unit = {},
    onFolderDrag: (LauncherFolder, Rect, Boolean) -> Unit = { _, _, _ -> },
    onFolderDragEnd: (Boolean) -> Unit = {},
    notificationsFor: (LauncherApp) -> List<AppNotification> = { emptyList() },
    folderApps: Map<String, LauncherApp> = emptyMap(),
    appStackContent: (@Composable (LauncherFolder) -> Unit)? = null,
    privateExpanded: Boolean = false,
    privateAppsPublic: Boolean = false,
    privateLoading: Boolean = false,
    privateFailed: Boolean = false,
    onPrivateSpaceSettings: () -> Unit = {},
    onRetryPrivateSpace: () -> Unit = {},
    workExpanded: Boolean = false,
    onWorkProfileSettings: () -> Unit = {},
    searchState: TextFieldState,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchAnimationDurationMillis: Int = 500,
    searchAnimationsEnabled: Boolean = true,
) {
    val appearance = LocalLauncherAppearance.current
    val listAppearance = LocalListAppearance.current
    val labelStyle = listLabelStyle()
    val density = LocalDensity.current
    val searchTop = stableStatusBarInset() + 12.dp
    var searchBarHeight by remember { mutableStateOf(56.dp) }
    var searchButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var searchTransition by remember { mutableStateOf<GraceButtonTransition?>(null) }
    var requestKeyboard by remember { mutableStateOf(false) }
    val listTopPadding = if (searchExpanded) searchBarHeight / 2 + 16.dp else topSpace
    LaunchedEffect(searchExpanded, searchTransition) {
        if (!searchExpanded) searchTransition = null
        else searchTransition?.let { it.animate(); searchTransition = null }
    }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded && searchTransition != null) delay(100)
        requestKeyboard = searchExpanded
    }
    val safeTop = with(density) { (stableStatusBarInset() + 12.dp)
        .coerceAtLeast(LauncherLayout.TopFadeHeight).roundToPx() }
    val currentModel by rememberUpdatedState(model)
    val currentPrivateLoading by rememberUpdatedState(privateLoading)
    val query = searchState.text.toString()
    val visibleItems = remember(model, query) { model.filterApps(query) }
    val currentQueryActive by rememberUpdatedState(query.isNotBlank())
    LaunchedEffect(query) {
        if (listState.layoutInfo.totalItemsCount > 0) listState.scrollToItem(0)
    }
    var lastSelectedLetter by remember { mutableStateOf(selectedLetter) }
    SideEffect { if (selectedLetter != null) lastSelectedLetter = selectedLetter }
    // Share one fade clock across all rows, including those composed after
    // release. The selected section stays opaque and every row keeps its height.
    val releasedItemsAlpha = animateFloatAsState(
        targetValue = if (selectedLetter == null) 1f else 0f,
        animationSpec = if (selectedLetter == null) tween(180) else snap(),
        label = "alphabetReleaseFade",
    )
    LaunchedEffect(privateExpanded, workExpanded) {
        if (!privateExpanded && !workExpanded) return@LaunchedEffect
        if (currentQueryActive) return@LaunchedEffect
        val expandedId = if (workExpanded) com.galaxyrio.gracelauncher.data.WorkProfileFolderId else PrivateSpaceFolderId
        withFrameNanos { }
        // Position this opening once, after its initial contents have been laid
        // out. Later package/icon refreshes must leave the user's scroll alone.
        snapshotFlow {
            currentQueryActive || (
                (workExpanded || !currentPrivateLoading || currentModel.items.any { it is DrawerItem.PrivateApp }) &&
                    listState.layoutInfo.totalItemsCount == currentModel.items.size &&
                    listState.layoutInfo.viewportEndOffset > listState.layoutInfo.viewportStartOffset
            )
        }.first { it }
        if (currentQueryActive) return@LaunchedEffect
        val expandedModel = currentModel
        val index = expandedModel.items.indexOfFirst { it is DrawerItem.Folder && it.folder.id == expandedId }
        if (index < 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        val rowHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == expandedModel.items[index].key }?.size
            ?: with(density) { LauncherLayout.RowMinHeight.roundToPx() }
        val blockSize = 1 + expandedModel.items.drop(index + 1).takeWhile { it.section == FolderSection && it !is DrawerItem.Folder }.size
        val blockHeight = rowHeight * blockSize
        val desiredTop = (viewport - blockHeight - with(density) { 80.dp.roundToPx() }).coerceAtLeast(safeTop)
        // Short lists settle above the search field and system inset; tall lists
        // start with the profile row in view and continue below it in this LazyColumn.
        listState.animateScrollToItem(index, with(density) { listTopPadding.roundToPx() } - desiredTop)
    }
    // Share consumed insets so the list and search follow the keyboard together.
    Box(modifier.fillMaxSize().navigationBarsPadding().imePadding()
        .then(if (searchTransition != null) Modifier.clearAndSetSemantics {} else Modifier)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .padding(top = if (searchExpanded) searchTop + searchBarHeight / 2 else 0.dp)
                .graphicsLayer { alpha = searchTransition?.searchResultsAlpha ?: 1f }
                .testTag("app_drawer"),
            // Scrollable leading space, not padding on the viewport: earlier groups
            // may occupy this area after jumping to a later letter. Bottom padding
            // leaves room for the search button inside the safe viewport.
            contentPadding = PaddingValues(start = listAppearance.startPadding, end = listAppearance.endPadding,
                top = listTopPadding, bottom = if (searchExpanded) 24.dp else 80.dp),
            userScrollEnabled = selectedLetter == null,
        ) {
            if (query.isNotBlank() && visibleItems.isEmpty()) {
                item(key = "drawer-search-empty") {
                    Text(
                        stringResource(R.string.drawer_search_no_results),
                        Modifier.padding(horizontal = LauncherLayout.ContentInset, vertical = 16.dp),
                        color = appearance.text,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = appearance.textShadow),
                    )
                }
            }
            items(
                items = visibleItems,
                key = DrawerItem::key,
                contentType = { when (it) { is DrawerItem.Header -> "header"; is DrawerItem.App, is DrawerItem.PrivateApp -> "app"; is DrawerItem.Folder -> "folder"; DrawerItem.PrivateStatus, DrawerItem.WorkStatus -> "status" } },
            ) { item ->
                // Keep every item's key and measured height. Hiding a group must
                // not change scroll bounds or move the selected header on release.
                Box(Modifier.retainItemSpace(selectedLetter == null || item.section == selectedLetter)
                    .graphicsLayer {
                        alpha = if (selectedLetter == null && item.section != lastSelectedLetter) releasedItemsAlpha.value else 1f
                    }) {
                    when (item) {
                        is DrawerItem.Header -> Text(
                            text = when (item.section) {
                                FolderSection -> stringResource(R.string.drawer_folders)
                                GraceSection -> stringResource(R.string.app_name)
                                else -> item.section
                            },
                            modifier = Modifier
                                .height(44.dp)
                                .padding(start = LauncherLayout.ContentInset, end = LauncherLayout.ContentInset, top = 12.dp)
                                .testTag("section:${item.section}"),
                            color = labelStyle.color,
                            style = TextStyle(
                                fontFamily = labelStyle.fontFamily,
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Normal,
                                shadow = appearance.textShadow,
                            ),
                        )
                        is DrawerItem.App -> LauncherAppRow(
                            showLabel = listAppearance.showNames(favorites = false, insideFolder = item.section == FolderSection),
                            app = item.app,
                            onClick = { onLaunchApp(item.app) },
                            onLongClick = { onAppDetails(item.app) },
                            onSwipeRight = { onAppShortcuts(item.app, it) },
                            gestures = rowGestures,
                            highlighted = highlightedAppKey == item.app.key,
                            notifications = notificationsFor(item.app),
                        )
                        is DrawerItem.Folder -> if (item.folder.isAppStack && appStackContent != null) appStackContent(item.folder) else FolderRow(
                            showLabel = listAppearance.showNames(favorites = false),
                            folder = item.folder,
                            app = folderApps[item.folder.id] ?: item.folder.asApp(),
                            notifications = notificationsFor(folderApps[item.folder.id] ?: item.folder.asApp()),
                            highlighted = highlightedAppKey == item.folder.key,
                            onOpen = { onOpenFolder(item.folder, it) },
                            onLongClick = { onEditFolder(item.folder) },
                            onDrag = { bounds, expanded -> onFolderDrag(item.folder, bounds, expanded) },
                            onDragEnd = onFolderDragEnd,
                            onSwipeLeft = rowGestures.onOpenFirst?.let { open -> { bounds ->
                                open(folderApps[item.folder.id] ?: item.folder.asApp(), bounds)
                            } },
                        )
                        is DrawerItem.PrivateApp -> LauncherAppRow(
                            showLabel = listAppearance.showNames(favorites = false, insideFolder = true),
                            app = item.app, onClick = { onLaunchApp(item.app) }, onLongClick = { onAppDetails(item.app) },
                            onSwipeRight = { if (privateAppsPublic) onAppShortcuts(item.app, it) },
                            gestures = if (privateAppsPublic) rowGestures else AppRowGestures(onLaunchAt = rowGestures.onLaunchAt),
                            highlighted = highlightedAppKey == item.app.key,
                            modifier = Modifier.animateItem(fadeOutSpec = null),
                        )
                        DrawerItem.WorkStatus -> Column(Modifier.padding(start = LauncherLayout.ContentInset, bottom = 12.dp)) {
                            Text(stringResource(R.string.work_profile_no_apps), Modifier.padding(vertical = 12.dp), color = appearance.text,
                                style = MaterialTheme.typography.bodyMedium.copy(shadow = appearance.textShadow))
                            TextButton(onClick = onWorkProfileSettings) { Text(stringResource(R.string.work_profile_setup), color = appearance.text) }
                        }
                        DrawerItem.PrivateStatus -> Column(Modifier.padding(start = LauncherLayout.ContentInset, bottom = 12.dp)) {
                            Text(stringResource(when { privateLoading -> R.string.private_space_loading
                                privateFailed -> R.string.private_space_unavailable; else -> R.string.private_space_empty }),
                                Modifier.padding(vertical = 12.dp), color = appearance.text,
                                style = MaterialTheme.typography.bodyMedium.copy(shadow = appearance.textShadow))
                            if (!privateLoading) TextButton(onClick = if (privateFailed) onRetryPrivateSpace else onPrivateSpaceSettings) {
                                Text(stringResource(if (privateFailed) R.string.retry else R.string.private_space_setup), color = appearance.text)
                            }
                        }
                    }
                }
            }
        }
        if (searchExpanded) LauncherSearchBar(
            textFieldState = searchState,
            placeholder = stringResource(R.string.drawer_search_apps),
            tag = "drawer_search",
            autoFocus = requestKeyboard,
            onBack = { onSearchExpandedChange(false) },
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(start = 16.dp, end = 16.dp, top = searchTop)
                .onSizeChanged { searchBarHeight = with(density) { it.height.toDp() } }
                .onGloballyPositioned { searchTransition?.searchBounds = it.boundsInRoot() }
                .graphicsLayer { alpha = searchTransition?.searchBarAlpha ?: 1f },
        ) else FloatingActionButton(
            onClick = {
                if (searchAnimationsEnabled) searchTransition = GraceButtonTransition(
                    GraceButtonAction.Search,
                    GraceButtonOrigin(searchButtonBounds, glyphRes = R.drawable.ms_search),
                    durationMillis = searchAnimationDurationMillis,
                )
                onSearchExpandedChange(true)
            },
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd)
                // Keep the button clear of the alphabet's edge touch strip.
                .padding(end = LauncherLayout.End, bottom = 8.dp)
                .testTag("drawer_search_button")
                .onGloballyPositioned { searchButtonBounds = it.boundsInRoot() },
        ) {
            Icon(painterResource(R.drawable.ms_search), stringResource(R.string.search_apps))
        }
        searchTransition?.let { GraceButtonTransitionOverlay(it) }
    }
}

private fun Modifier.retainItemSpace(visible: Boolean): Modifier =
    // Unlike alpha=0, not placing a child also removes its invisible hit targets.
    // Clear descendants from TalkBack while retaining the complete list geometry.
    then(if (visible) Modifier else Modifier.clearAndSetSemantics {}).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            if (visible) placeable.placeRelative(0, 0)
        }
    }
