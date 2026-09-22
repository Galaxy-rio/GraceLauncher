package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.LauncherApp

// Wallpaper remains visible on both pages; a small shadow keeps white text readable.
val WallpaperTextShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 5f)

@Composable
fun AppIcon(app: LauncherApp, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    if (app.icon != null) {
        Image(bitmap = app.icon, contentDescription = null, modifier = modifier.size(size))
    } else {
        val colors = listOf(
            Color(0xFF65D5BE), Color(0xFFFFB3A7), Color(0xFFAEC6FF),
            Color(0xFFFFD18A), Color(0xFFD4B7FF),
        )
        val color = remember(app.key) { colors[(app.key.hashCode() and Int.MAX_VALUE) % colors.size] }
        Box(
            modifier = modifier.size(size).background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.firstOrNull()?.uppercase().orEmpty(),
                color = Color(0xFF13201E),
                fontSize = (size.value * 0.43f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun LauncherAppRow(
    app: LauncherApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .testTag("app:${app.key}")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app)
        Spacer(Modifier.width(18.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(fontWeight = FontWeight.Medium, shadow = WallpaperTextShadow),
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
