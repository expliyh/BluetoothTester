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
    const val DEFAULT_ENTER_DURATION_MS: Int = 260
    const val DEFAULT_EXIT_DURATION_MS: Int = 200
    const val DEFAULT_SLIDE_FRACTION: Int = 3

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { it / DEFAULT_SLIDE_FRACTION },
            animationSpec = tween(
                durationMillis = DEFAULT_ENTER_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DEFAULT_ENTER_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            initialAlpha = 0.2f
        )
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -it / DEFAULT_SLIDE_FRACTION },
            animationSpec = tween(
                durationMillis = DEFAULT_EXIT_DURATION_MS,
                easing = FastOutLinearInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = DEFAULT_EXIT_DURATION_MS,
                easing = FastOutLinearInEasing
            )
        )
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -it / DEFAULT_SLIDE_FRACTION },
            animationSpec = tween(
                durationMillis = DEFAULT_ENTER_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DEFAULT_ENTER_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            initialAlpha = 0.2f
        )
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it / DEFAULT_SLIDE_FRACTION },
            animationSpec = tween(
                durationMillis = DEFAULT_EXIT_DURATION_MS,
                easing = FastOutLinearInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = DEFAULT_EXIT_DURATION_MS,
                easing = FastOutLinearInEasing
            )
        )
    }
}
