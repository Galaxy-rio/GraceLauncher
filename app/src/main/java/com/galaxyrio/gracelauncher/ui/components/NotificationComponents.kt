package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import kotlinx.coroutines.delay

@Composable
internal fun notificationAge(postedAt: Long): String {
    val now by produceState(System.currentTimeMillis(), postedAt) {
        while (true) { value = System.currentTimeMillis(); delay(60_000) }
    }
    val minutes = ((now - postedAt).coerceAtLeast(0) / 60_000)
    return when {
        minutes < 1 -> stringResource(R.string.notification_now)
        minutes < 60 -> stringResource(R.string.notification_minutes, minutes)
        minutes < 1440 -> stringResource(R.string.notification_hours, minutes / 60)
        else -> stringResource(R.string.notification_days, minutes / 1440)
    }
}
