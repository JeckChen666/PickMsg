package com.pickmsg.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JadeLight = lightColorScheme(
    primary = Color(0xFF28684F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBDF),
    onPrimaryContainer = Color(0xFF153D2D),
    secondary = Color(0xFF576B5E),
    secondaryContainer = Color(0xFFE5EBDD),
    onSecondaryContainer = Color(0xFF304536),
    tertiary = Color(0xFF88602A),
    tertiaryContainer = Color(0xFFF7E8CB),
    onTertiaryContainer = Color(0xFF573B14),
    background = Color(0xFFF6F3EA),
    onBackground = Color(0xFF20372D),
    surface = Color(0xFFFCFAF4),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF4F3EA),
    surfaceContainer = Color(0xFFF0F0E6),
    surfaceContainerHigh = Color(0xFFECEDE2),
    surfaceContainerHighest = Color(0xFFE5E8DC),
    onSurface = Color(0xFF20372D),
    surfaceVariant = Color(0xFFE8EBE1),
    onSurfaceVariant = Color(0xFF58665C),
    outline = Color(0xFF747E71),
    outlineVariant = Color(0xFFD2DACC),
)

private val JadeDark = darkColorScheme(
    primary = Color(0xFFA3D4B6),
    onPrimary = Color(0xFF103B29),
    primaryContainer = Color(0xFF2C503D),
    onPrimaryContainer = Color(0xFFD4EEDC),
    secondary = Color(0xFFB6CBB9),
    secondaryContainer = Color(0xFF35483A),
    onSecondaryContainer = Color(0xFFDCE8D9),
    tertiary = Color(0xFFE2BF87),
    tertiaryContainer = Color(0xFF54432A),
    onTertiaryContainer = Color(0xFFF6E4C5),
    background = Color(0xFF131D18),
    onBackground = Color(0xFFE2E9DF),
    surface = Color(0xFF1C2821),
    surfaceContainerLowest = Color(0xFF101813),
    surfaceContainerLow = Color(0xFF19251D),
    surfaceContainer = Color(0xFF1F2C23),
    surfaceContainerHigh = Color(0xFF26352B),
    surfaceContainerHighest = Color(0xFF303F34),
    onSurface = Color(0xFFE2E9DF),
    surfaceVariant = Color(0xFF303E33),
    onSurfaceVariant = Color(0xFFB9C5B8),
    outline = Color(0xFF8D9D8E),
    outlineVariant = Color(0xFF425246),
)

@Composable
fun PickMsgTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) JadeDark else JadeLight
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PickMsgShape.material,
        typography = PickMsgTypography,
        content = content,
    )
}
