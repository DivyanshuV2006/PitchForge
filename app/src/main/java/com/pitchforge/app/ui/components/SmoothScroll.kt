package com.pitchforge.app.ui.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Longer-coasting fling than Compose's default, but hard-stops at list bounds and
 * never hands leftover velocity to nested scroll / overscroll (that handoff is what
 * felt like a one-second lag at the top or bottom).
 *
 * [frictionMultiplier] below 1.0 = silkier / longer coast.
 */
@Composable
fun rememberSmoothFlingBehavior(
    frictionMultiplier: Float = 0.55f
): FlingBehavior {
    val decay = remember(frictionMultiplier) {
        exponentialDecay<Float>(frictionMultiplier = frictionMultiplier)
    }
    return remember(decay) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (abs(initialVelocity) < 1f) return 0f

                var lastValue = 0f
                val anim = AnimationState(initialValue = 0f, initialVelocity = initialVelocity)
                anim.animateDecay(decay) {
                    val delta = value - lastValue
                    lastValue = value
                    val consumed = scrollBy(delta)
                    // Bound hit — stop immediately; return 0 below so parents don't coast.
                    if (abs(delta - consumed) > 0.5f) cancelAnimation()
                }
                return 0f
            }
        }
    }
}
