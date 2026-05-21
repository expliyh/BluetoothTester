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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max
import kotlin.math.min

// region 颜色工具

/** 向白色混合 */
private fun Color.tint(ratio: Float): Color = Color(
    red = red + (1f - red) * ratio,
    green = green + (1f - green) * ratio,
    blue = blue + (1f - blue) * ratio,
    alpha = alpha
)

/** 向黑色混合 */
private fun Color.shade(ratio: Float): Color = Color(
    red = red * (1f - ratio),
    green = green * (1f - ratio),
    blue = blue * (1f - ratio),
    alpha = alpha
)

/** 色调旋转（范围 0..360） */
private fun Color.hueShifted(degrees: Float): Color {
    val hsl = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
        hsl
    )
    hsl[0] = (hsl[0] + degrees) % 360f
    return Color.hsl(hsl[0], hsl[1], hsl[2], alpha)
}

/** 降低饱和度 */
private fun Color.desaturated(factor: Float = 0.5f): Color {
    val hsl = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
        hsl
    )
    hsl[1] = max(0f, min(1f, hsl[1] * factor))
    return Color.hsl(hsl[0], hsl[1], hsl[2], alpha)
}

/** 计算颜色亮度 (0=黑, 1=白) */
private fun Color.luminance(): Float {
    val r = if (red <= 0.04045f) red / 12.92f else ((red + 0.055f) / 1.055f).let { it * it * it }
    val g = if (green <= 0.04045f) green / 12.92f else ((green + 0.055f) / 1.055f).let { it * it * it }
    val b = if (blue <= 0.04045f) blue / 12.92f else ((blue + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/** 混合两种颜色（ratio=0 是本色, ratio=1 是 other） */
private fun Color.blend(other: Color, ratio: Float): Color = Color(
    red = red * (1f - ratio) + other.red * ratio,
    green = green * (1f - ratio) + other.green * ratio,
    blue = blue * (1f - ratio) + other.blue * ratio,
    alpha = alpha * (1f - ratio) + other.alpha * ratio
)

// endregion

/** 默认浅色方案（蓝色系） */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC2C7CE),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
)

/** 默认深色方案（蓝色系） */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF42474E),
    inverseSurface = Color(0xFFF1F0F4),
    inverseOnSurface = Color(0xFF2F3033),
)

/**
 * 从种子颜色生成完整 ColorScheme。
 * 模仿 Material 3 的 tonal palette 生成逻辑。
 */
private fun schemeFromSeed(seedColor: Color, darkTheme: Boolean): ColorScheme {
    // 根据 Seed 的亮度决定 light / dark 模式的 primary 基调
    val seedLum = seedColor.luminance()

    // 深色模式：primary 更亮，类似 tone 80
    val primary = if (darkTheme) {
        when {
            seedLum < 0.4f -> seedColor.tint(0.45f)      // 暗色种子 → 提亮较多
            seedLum < 0.7f -> seedColor.tint(0.20f)      // 中等亮度 → 微调
            else -> seedColor                              // 本身已经够亮（如黄色）
        }
    } else {
        // 浅色模式：primary 更暗，类似 tone 40
        when {
            seedLum > 0.75f -> seedColor.shade(0.12f)     // 很亮的颜色（黄）→ 轻微加深
            seedLum > 0.55f -> seedColor.shade(0.22f)     // 中等亮度 → 适度加深
            else -> seedColor                              // 本身已经够暗
        }
    }

    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White

    val primaryContainer = if (darkTheme) {
        primary.shade(0.30f)
    } else {
        primary.tint(if (seedLum > 0.6f) 0.35f else 0.55f)
    }
    val onPrimaryContainer = if (darkTheme) {
        primaryContainer.tint(0.55f)
    } else {
        primaryContainer.shade(0.80f)
    }

    val secondary = primary.hueShifted(15f).desaturated(0.55f)
    val secondaryContainer = if (darkTheme) secondary.shade(0.30f) else secondary.tint(0.55f)
    val onSecondary = if (secondary.luminance() > 0.5f) Color.Black else Color.White
    val onSecondaryContainer = if (darkTheme) secondaryContainer.tint(0.55f) else secondaryContainer.shade(0.80f)

    // tertiary：与 primary 形成互补对比，保留更多饱和度作为点缀色
    var tertiary = primary.hueShifted(50f).desaturated(0.80f)
    // 确保 tertiary 在对应模式下有足够对比度
    if (!darkTheme && tertiary.luminance() > 0.60f) {
        tertiary = tertiary.shade(0.30f)
    } else if (darkTheme && tertiary.luminance() < 0.35f) {
        tertiary = tertiary.tint(0.30f)
    }
    val tertiaryContainer = if (darkTheme) tertiary.shade(0.30f) else tertiary.tint(0.55f)
    val onTertiary = if (tertiary.luminance() > 0.5f) Color.Black else Color.White
    val onTertiaryContainer = if (darkTheme) tertiaryContainer.tint(0.55f) else tertiaryContainer.shade(0.80f)

    val errorLight = Color(0xFFB3261E)
    val errorDark = Color(0xFFF2B8B5)
    val onErrorLight = Color(0xFFFFFFFF)
    val onErrorDark = Color(0xFF601410)
    val errorContainerLight = Color(0xFFF9DEDC)
    val errorContainerDark = Color(0xFF8C1D18)
    val onErrorContainerLight = Color(0xFF410E0B)
    val onErrorContainerDark = Color(0xFFF9DEDC)

    // 从 primary 衍生 surface 色调
    val baseSurface = if (darkTheme) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)
    // surfaceVariant：在 surface 上叠加 primary 色调
    val surfaceVariant = if (darkTheme) {
        baseSurface.blend(primary, 0.10f)
    } else {
        // 浅色模式下需要更高的混合比才能看出来
        baseSurface.blend(primary, 0.08f)
    }
    val onSurface = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val onSurfaceVariant = if (darkTheme) {
        onSurface.blend(primary.desaturated(0.5f), 0.30f)
    } else {
        onSurface.blend(primary.desaturated(0.5f), 0.25f)
    }
    val outline = if (darkTheme) {
        onSurface.copy(alpha = 0.45f)
    } else {
        onSurface.copy(alpha = 0.45f)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = errorDark,
            onError = onErrorDark,
            errorContainer = errorContainerDark,
            onErrorContainer = onErrorContainerDark,
            surface = baseSurface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            inverseSurface = Color(0xFFE6E1E5),
            inverseOnSurface = Color(0xFF1C1B1F),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = errorLight,
            onError = onErrorLight,
            errorContainer = errorContainerLight,
            onErrorContainer = onErrorContainerLight,
            surface = baseSurface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            inverseSurface = Color(0xFF313033),
            inverseOnSurface = Color(0xFFF4EFF4),
        )
    }
}

@Composable
fun BluetoothTesterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        seedColor != null -> schemeFromSeed(seedColor, darkTheme)
        else -> if (darkTheme) DarkScheme else LightScheme
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
    seedColor: Color? = null,
    durationMillis: Int = 300,
    content: @Composable () -> Unit
) {
    val targetScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        seedColor != null -> schemeFromSeed(seedColor, darkTheme)
        else -> if (darkTheme) DarkScheme else LightScheme
    }
    val animSpec = tween<Color>(durationMillis = durationMillis, easing = FastOutSlowInEasing)

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
