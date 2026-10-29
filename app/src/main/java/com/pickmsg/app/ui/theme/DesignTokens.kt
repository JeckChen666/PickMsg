package com.pickmsg.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PickMsgSpace {
    val micro = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val empty = 48.dp

    val page = lg
    val card = lg
    val listGap = md
    val fieldGap = md
    val sectionGap = xl
    val dockHorizontal = md
    val dockVertical = sm
    val fabClearance = 96.dp

    val pagePadding = PaddingValues(start = page, top = sm, end = page, bottom = xl)
    val listWithFabPadding = PaddingValues(start = page, top = sm, end = page, bottom = fabClearance)
}

object PickMsgShape {
    val badge = RoundedCornerShape(8.dp)
    val control = RoundedCornerShape(12.dp)
    val icon = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(20.dp)
    val dock = RoundedCornerShape(24.dp)
    val dialog = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)

    val material = Shapes(
        extraSmall = badge,
        small = control,
        medium = RoundedCornerShape(16.dp),
        large = card,
        extraLarge = dialog,
    )
}

object PickMsgElevation {
    val flat = 0.dp
    // Translucent surfaces expose the interior of platform shadows; use the border instead.
    val dock = 0.dp
    val floating = 6.dp
    val overlay = 8.dp
}

object PickMsgSize {
    val border = 1.dp
    val iconSmall = 20.dp
    val icon = 24.dp
    val avatar = 40.dp
    val touchTarget = 48.dp
}

object PickMsgOpacity {
    const val glass = 0.65f
    const val card = 0.88f
    const val field = 0.9f
    const val subtle = 0.5f
    const val tint = 0.12f
    const val lightEdge = 0.9f
    const val darkEdge = 0.19f
    const val edgeBottom = 0.3f
    const val jadeGlow = 0.15f
    const val warmGlow = 0.09f
}

// Platform fonts retain Chinese fallback and accessibility font scaling.
val PickMsgTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)
