package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R

enum class LauncherSymbol {
    Star, Info, Hourglass, Category, Delete, Chevron, Settings, Launch,
    Plus, Edit, Apps, Check, Palette, Home, Search, Folder, Clock, Calendar, Weather, Move, Resize
}

/** Official Material Symbols Outlined: optical size 24, weight 400, grade 0, fill 0. */
@Composable
fun LauncherIcon(
    symbol: LauncherSymbol,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(
            when (symbol) {
                LauncherSymbol.Star -> R.drawable.ms_star
                LauncherSymbol.Info -> R.drawable.ms_info
                LauncherSymbol.Hourglass -> R.drawable.ms_hourglass_empty
                LauncherSymbol.Category -> R.drawable.ms_category
                LauncherSymbol.Delete -> R.drawable.ms_delete
                LauncherSymbol.Chevron -> R.drawable.ms_expand_more
                LauncherSymbol.Settings -> R.drawable.ms_settings
                LauncherSymbol.Launch -> R.drawable.ms_open_in_new
                LauncherSymbol.Plus -> R.drawable.ms_add
                LauncherSymbol.Edit -> R.drawable.ms_edit
                LauncherSymbol.Apps -> R.drawable.ms_apps
                LauncherSymbol.Check -> R.drawable.ms_check
                LauncherSymbol.Palette -> R.drawable.ms_palette
                LauncherSymbol.Home -> R.drawable.ms_home
                LauncherSymbol.Search -> R.drawable.ms_search
                LauncherSymbol.Folder -> R.drawable.ms_folder
                LauncherSymbol.Clock -> R.drawable.ms_schedule
                LauncherSymbol.Calendar -> R.drawable.ms_calendar_month
                LauncherSymbol.Weather -> R.drawable.ms_sunny
                LauncherSymbol.Move -> R.drawable.ms_unfold_more
                LauncherSymbol.Resize -> R.drawable.ms_crop
            },
        ),
        // These icons decorate parent-labelled actions, which own accessibility semantics.
        contentDescription = null,
        modifier = modifier.size(24.dp),
        tint = tint,
    )
}
