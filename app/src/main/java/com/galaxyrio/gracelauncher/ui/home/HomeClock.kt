package com.galaxyrio.gracelauncher.ui.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/** Explicit HH keeps the leading zero even in locales whose default hour pattern is H. */
internal fun formatHomeClock(
    now: Instant,
    use24Hour: Boolean,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", locale)
    .withDecimalStyle(DecimalStyle.of(locale)).withZone(zone).format(now)
