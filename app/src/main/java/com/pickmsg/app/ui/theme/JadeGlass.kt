package com.pickmsg.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp

@Composable
fun JadeBackdrop(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(colors.background).drawWithCache {
            // Static diffused light is compatible with API 26 and avoids per-row blur on scroll.
            val jade = Brush.radialGradient(
                listOf(colors.primary.copy(alpha = PickMsgOpacity.jadeGlow), Color.Transparent),
                center = Offset(size.width * 1.05f, size.height * 0.22f),
                radius = size.width * 1.1f,
            )
            val warm = Brush.radialGradient(
                listOf(colors.tertiary.copy(alpha = PickMsgOpacity.warmGlow), Color.Transparent),
                center = Offset(0f, size.height * 0.72f),
                radius = size.width * 0.95f,
            )
            onDrawBehind {
                drawRect(jade)
                drawRect(warm)
            }
        },
        content = content,
    )
}

@Composable
fun JadeGlass(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    shape: Shape = PickMsgShape.card,
    shadowElevation: Dp = PickMsgElevation.flat,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val fill = colors.surface.copy(alpha = if (emphasized) PickMsgOpacity.glass else PickMsgOpacity.card)
    val border = BorderStroke(
        PickMsgSize.border,
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) PickMsgOpacity.darkEdge else PickMsgOpacity.lightEdge),
                colors.outlineVariant.copy(alpha = PickMsgOpacity.edgeBottom)),
        ),
    )
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = fill, contentColor = colors.onSurface,
            border = border, shadowElevation = shadowElevation, content = content)
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = fill, contentColor = colors.onSurface,
            border = border, shadowElevation = shadowElevation, content = content)
    }
}
