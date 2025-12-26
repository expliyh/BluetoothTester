package top.expli.bluetoothtester.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun BluetoothTesterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun AnimatedBluetoothTesterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    durationMillis: Int = 300,
    content: @Composable () -> Unit
) {
    val targetScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val animSpec = tween<Color>(durationMillis = durationMillis, easing = FastOutSlowInEasing)

    // Animate a subset of frequently used colors to avoid heavy recomposition
    val primary by animateColorAsState(targetScheme.primary, animSpec, label = "primary")
    val onPrimary by animateColorAsState(targetScheme.onPrimary, animSpec, label = "onPrimary")
    val primaryContainer by animateColorAsState(
        targetScheme.primaryContainer,
        animSpec,
        label = "primaryContainer"
    )
    val onPrimaryContainer by animateColorAsState(
        targetScheme.onPrimaryContainer,
        animSpec,
        label = "onPrimaryContainer"
    )
    val secondary by animateColorAsState(targetScheme.secondary, animSpec, label = "secondary")
    val surface by animateColorAsState(targetScheme.surface, animSpec, label = "surface")
    val onSurface by animateColorAsState(targetScheme.onSurface, animSpec, label = "onSurface")
    val surfaceVariant by animateColorAsState(
        targetScheme.surfaceVariant,
        animSpec,
        label = "surfaceVariant"
    )
    val onSurfaceVariant by animateColorAsState(
        targetScheme.onSurfaceVariant,
        animSpec,
        label = "onSurfaceVariant"
    )
    val error by animateColorAsState(targetScheme.error, animSpec, label = "error")
    val outline by animateColorAsState(targetScheme.outline, animSpec, label = "outline")

    val animatedScheme = ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = targetScheme.inversePrimary,
        secondary = secondary,
        onSecondary = targetScheme.onSecondary,
        secondaryContainer = targetScheme.secondaryContainer,
        onSecondaryContainer = targetScheme.onSecondaryContainer,
        tertiary = targetScheme.tertiary,
        onTertiary = targetScheme.onTertiary,
        tertiaryContainer = targetScheme.tertiaryContainer,
        onTertiaryContainer = targetScheme.onTertiaryContainer,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = targetScheme.surfaceTint,
        inverseSurface = targetScheme.inverseSurface,
        inverseOnSurface = targetScheme.inverseOnSurface,
        error = error,
        onError = targetScheme.onError,
        errorContainer = targetScheme.errorContainer,
        onErrorContainer = targetScheme.onErrorContainer,
        outline = outline,
        scrim = targetScheme.scrim,
        outlineVariant = targetScheme.outlineVariant,
        surfaceBright = targetScheme.surfaceBright,
        surfaceDim = targetScheme.surfaceDim,
        surfaceContainer = targetScheme.surfaceContainer,
        surfaceContainerHigh = targetScheme.surfaceContainerHigh,
        surfaceContainerHighest = targetScheme.surfaceContainerHighest,
        surfaceContainerLow = targetScheme.surfaceContainerLow,
        surfaceContainerLowest = targetScheme.surfaceContainerLowest,
        primaryFixed = targetScheme.primaryFixed,
        primaryFixedDim = targetScheme.primaryFixedDim,
        onPrimaryFixed = targetScheme.onPrimaryFixed,
        onPrimaryFixedVariant = targetScheme.onPrimaryFixedVariant,
        secondaryFixed = targetScheme.secondaryFixed,
        secondaryFixedDim = targetScheme.secondaryFixedDim,
        onSecondaryFixed = targetScheme.onSecondaryFixed,
        onSecondaryFixedVariant = targetScheme.onSecondaryFixedVariant,
        tertiaryFixed = targetScheme.tertiaryFixed,
        tertiaryFixedDim = targetScheme.tertiaryFixedDim,
        onTertiaryFixed = targetScheme.onTertiaryFixed,
        onTertiaryFixedVariant = targetScheme.onTertiaryFixedVariant
    )

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = Typography,
        content = content
    )
}
