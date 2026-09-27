package com.galaxyrio.gracelauncher.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

// Same hierarchy motion as Sudoku's MaterialTransitionPatterns: 30 dp travel,
// 300 ms spatial motion, an outgoing 90 ms fade and an incoming 210 ms fade.
// NavHost also seeks these transitions while a predictive-back gesture is held.
internal fun <S> AnimatedContentTransitionScope<S>.settingsEnter(distance: Int, back: Boolean = false) =
    slideIntoContainer(
        towards = if (back) AnimatedContentTransitionScope.SlideDirection.End else AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        initialOffset = { it.coerceIn(-distance, distance) },
    ) + fadeIn(tween(210, delayMillis = 90, easing = LinearEasing))

internal fun <S> AnimatedContentTransitionScope<S>.settingsExit(distance: Int, back: Boolean = false) =
    slideOutOfContainer(
        towards = if (back) AnimatedContentTransitionScope.SlideDirection.End else AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        targetOffset = { it.coerceIn(-distance, distance) },
    ) + fadeOut(tween(90, easing = LinearEasing))
