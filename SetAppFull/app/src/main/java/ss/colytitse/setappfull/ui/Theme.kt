package ss.colytitse.setappfull.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ss.colytitse.setappfull.AppSettings

private val LightColors = lightColorScheme(
    primary = Color(0xFF0088F4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE5FF),
    onPrimaryContainer = Color(0xFF001E33),
    secondary = Color(0xFF00BFA5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA7F0E6),
    onSecondaryContainer = Color(0xFF00201C),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0x146B8BFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAFF),
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF004C75),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFF00E5C8),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFA7F0E6),
    surfaceVariant = Color(0x146B8BFF),
)

/** 当前是否实际启用了动态取色（Monet）。供业务层按需区分 Monet / 静态配色。 */
val LocalDynamicColor = staticCompositionLocalOf { false }

/** 当前是否为深色主题（跟随系统时取系统值）。供业务层按需区分明暗。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun SetAppFullTheme(
    themeMode: String = AppSettings.THEME_SYSTEM_MONET,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppSettings.THEME_DARK, AppSettings.THEME_DARK_MONET -> true
        AppSettings.THEME_LIGHT, AppSettings.THEME_LIGHT_MONET -> false
        else -> isSystemInDarkTheme()
    }
    val dynamicColor = when (themeMode) {
        AppSettings.THEME_SYSTEM_MONET, AppSettings.THEME_DARK_MONET, AppSettings.THEME_LIGHT_MONET -> true
        else -> false
    }
    // 实际是否启用动态取色（API 31 以下即使选择 Monet 也会回退到静态配色）。
    val effectiveDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        effectiveDynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    // 状态栏 / 导航栏图标颜色跟随应用实际明暗主题，而不是系统主题。
    // 否则系统深色 + 应用强制浅色时，白色图标会与浅色背景重叠而看不清。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalDynamicColor provides effectiveDynamic,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/** 全局开关配色：普通模式 + 浅色主题下自定义 track（开关背景）颜色，其余用 Material3 默认。 */
@Composable
fun appSwitchColors(): SwitchColors =
    if (!LocalDynamicColor.current && !LocalDarkTheme.current) {
        SwitchDefaults.colors(
            checkedTrackColor = Color(0xFF3D5F90),
            uncheckedTrackColor = Color(0xFFE1E2E9),
        )
    } else {
        SwitchDefaults.colors()
    }
