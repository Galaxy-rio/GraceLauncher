package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.galaxyrio.gracelauncher.data.GraceButtonGesture
import kotlin.math.abs

/** Observe only motion left over at a list boundary; keep Android's native stretch. */
internal class HomeGestureConnection(
    private val effect: OverscrollEffect?,
    private val list: LazyListState,
    private val pullThreshold: Float,
    private val flingThreshold: Float,
    private val canTrigger: (GraceButtonGesture) -> Boolean,
    private val onGesture: (GraceButtonGesture) -> Unit,
) : NestedScrollConnection {
    private var edgePull = 0f
    private var armed = false
    private var fired = false

    fun beginGesture() { edgePull = 0f; armed = true; fired = false }
    fun cancelGesture() { armed = false; edgePull = 0f }

    private fun boundaryGesture(delta: Float): GraceButtonGesture? = when {
        delta > 0 && !list.canScrollBackward -> GraceButtonGesture.SwipeDown
        delta < 0 && !list.canScrollForward -> GraceButtonGesture.SwipeUp
        else -> null
    }

    private fun trigger(delta: Float): Boolean {
        val gesture = boundaryGesture(delta) ?: return false
        if (!armed || fired || !canTrigger(gesture)) return false
        fired = true
        onGesture(gesture)
        return true
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Reversing a pull cancels its accumulated distance, including stretch relaxation.
        if (source == NestedScrollSource.UserInput && available.y * edgePull < 0f) {
            edgePull = if (abs(available.y) >= abs(edgePull)) 0f else edgePull + available.y
        }
        if (effect?.isInProgress != true) return Offset.Zero
        var forwarded = Offset.Zero
        val consumed = effect.applyToScroll(Offset(0f, available.y), source) { delta ->
            forwarded = delta
            delta
        }
        return consumed - forwarded
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
            if (abs(consumed.y) > 0.5f) edgePull = 0f
            if (boundaryGesture(available.y) != null && abs(available.y) > 0.5f) {
                if (edgePull * available.y < 0f) edgePull = 0f
                edgePull += available.y
            }
        }
        return effect?.applyToScroll(Offset(0f, available.y), source) { Offset.Zero } ?: Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Commit on release, not halfway through a drag. This also handles slow,
        // deliberate pulls with almost zero release velocity on a short list.
        val direction = when {
            abs(edgePull) >= pullThreshold -> edgePull
            // A nested widget gets to consume its own fling first unless the
            // pointer has already pulled past the HOME list's boundary.
            abs(edgePull) > 0.5f && edgePull * available.y > 0f && abs(available.y) >= flingThreshold -> available.y
            else -> 0f
        }
        if (!trigger(direction)) return Velocity.Zero
        effect?.applyToFling(Velocity(0f, available.y)) { Velocity.Zero }
        return Velocity(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // A fast fling may reach the boundary after release. Only its remaining
        // velocity counts; an ordinary fling ending inside the list does not.
        if (abs(available.y) >= flingThreshold) trigger(available.y)
        armed = false
        effect?.applyToFling(Velocity(0f, available.y)) { Velocity.Zero }
        return if (effect == null) Velocity.Zero else Velocity(0f, available.y)
    }
}
