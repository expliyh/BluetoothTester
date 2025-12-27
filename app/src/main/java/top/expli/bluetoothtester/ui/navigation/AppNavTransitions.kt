package top.expli.bluetoothtester.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

object AppNavTransitions {
    const val DefaultEnterDurationMs: Int = 260
    const val DefaultExitDurationMs: Int = 200
    const val DefaultSlideFraction: Int = 3

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { it / DefaultSlideFraction },
            animationSpec = tween(
                durationMillis = DefaultEnterDurationMs,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DefaultEnterDurationMs,
                easing = FastOutSlowInEasing
            ),
            initialAlpha = 0.2f
        )
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -it / DefaultSlideFraction },
            animationSpec = tween(
                durationMillis = DefaultExitDurationMs,
                easing = FastOutLinearInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = DefaultExitDurationMs,
                easing = FastOutLinearInEasing
            )
        )
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -it / DefaultSlideFraction },
            animationSpec = tween(
                durationMillis = DefaultEnterDurationMs,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DefaultEnterDurationMs,
                easing = FastOutSlowInEasing
            ),
            initialAlpha = 0.2f
        )
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it / DefaultSlideFraction },
            animationSpec = tween(
                durationMillis = DefaultExitDurationMs,
                easing = FastOutLinearInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = DefaultExitDurationMs,
                easing = FastOutLinearInEasing
            )
        )
    }
}
