package com.tokencoin.client.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/*
 * 鯨幣 介面主題：參考 MaiBot「復古工業控制台 + 暖紙顆粒」風格。
 * 與原風格的差異：直角改為微弧角（控制元件 4dp、面板 6dp、大型容器 10dp）。
 */

@Immutable
data class TcColors(
    val isDark: Boolean,
    /** 整頁底色 */
    val canvas: Color,
    /** 內層底：輸入框、使用者訊息、程式碼區 */
    val inset: Color,
    /** 進度條軌道、更深一層的內底 */
    val track: Color,
    /** 主要結構色：主文字、強邊框、面板粗條 */
    val structure: Color,
    val textMuted: Color,
    val borderMuted: Color,
    /** 操作橘：主按鈕、選中 */
    val action: Color,
    /** 數據橘：數值、次要強調 */
    val data: Color,
    val onAction: Color,
    val chartSecondary: Color,
    val success: Color,
    val danger: Color,
    /** 顆粒色與最大不透明度 */
    val grain: Color,
    val grainAlpha: Float,
)

val LightTc = TcColors(
    isDark = false,
    canvas = Color(0xFFF3E6D4),
    inset = Color(0xFFEADCC9),
    track = Color(0xFFE2CFB4),
    structure = Color(0xFF0A4550),
    textMuted = Color(0xFF4F7173),
    borderMuted = Color(0xFF7C9A99),
    action = Color(0xFFC24D24),
    data = Color(0xFFB4461F),
    onAction = Color(0xFFF7EDDF),
    chartSecondary = Color(0xFFC99A3E),
    success = Color(0xFF2E8B57),
    danger = Color(0xFFC0392B),
    grain = Color(0xFF5A3E2B),
    grainAlpha = 0.07f,
)

val DarkTc = TcColors(
    isDark = true,
    canvas = Color(0xFF1F1715),
    inset = Color(0xFF29201C),
    track = Color(0xFF342B26),
    structure = Color(0xFFD2C0A9),
    textMuted = Color(0xFF9C8D7D),
    borderMuted = Color(0xFF675B4D),
    action = Color(0xFFC04D27),
    data = Color(0xFFC4835F),
    onAction = Color(0xFF140B07),
    chartSecondary = Color(0xFF7D8D6C),
    success = Color(0xFF4CAF7A),
    danger = Color(0xFFE5604F),
    grain = Color(0xFFF0DCC0),
    grainAlpha = 0.06f,
)

object TcShape {
    val control = RoundedCornerShape(4.dp)
    val panel = RoundedCornerShape(6.dp)
    val large = RoundedCornerShape(10.dp)
}

val LocalTc = staticCompositionLocalOf { LightTc }
private val LocalGrain = staticCompositionLocalOf<Brush?> { null }

object Tc {
    val colors: TcColors @Composable get() = LocalTc.current
    val mono = FontFamily.Monospace
}

/** 細密暖色顆粒的噪點圖（96px，平鋪使用）。Activity 的原生背景也用同一張，和 Compose 畫的對齊。 */
fun grainBitmap(c: TcColors, size: Int = 96): Bitmap {
    val rnd = Random(20261010)
    val base = c.grain.copy(alpha = 1f).toArgb() and 0x00FFFFFF
    val pixels = IntArray(size * size) {
        val a = (rnd.nextFloat() * rnd.nextFloat() * c.grainAlpha * 255 * 2.2f).toInt().coerceIn(0, 255)
        (a shl 24) or base
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

private fun grainBrush(c: TcColors): Brush =
    ShaderBrush(ImageShader(grainBitmap(c).asImageBitmap(), TileMode.Repeated, TileMode.Repeated))

/**
 * 形象舞台在視窗中的位置（視窗座標）。舞台的 WebView 在 Compose 底下，
 * 整頁畫布要在這裡留洞讓它透出來（見 ui/avatar/AvatarHost.kt）。
 */
val LocalCanvasHole = staticCompositionLocalOf<() -> Rect?> { { null } }

/**
 * 畫布底色 + 顆粒。用在整頁、抽屜、底部面板等大面積表面。
 * hole=true 時在形象舞台的位置留洞（只用於主視窗的整頁畫布，對話框等其他視窗不要用）。
 */
@Composable
fun Modifier.tcCanvas(color: Color = Tc.colors.canvas, hole: Boolean = false): Modifier {
    val grain = LocalGrain.current
    if (!hole) return drawBehind {
        drawRect(color)
        if (grain != null) drawRect(grain)
    }
    val holeRect = LocalCanvasHole.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInWindow() }.drawBehind {
        val h = holeRect()?.translate(-origin)
        if (h == null) {
            drawRect(color)
            if (grain != null) drawRect(grain)
        } else clipRect(h.left, h.top, h.right, h.bottom, ClipOp.Difference) {
            drawRect(color)
            if (grain != null) drawRect(grain)
        }
    }
}

@Composable
fun TcTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkTc else LightTc
    val grain = remember(dark) { grainBrush(c) }
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.action, onPrimary = c.onAction,
            primaryContainer = c.data, onPrimaryContainer = c.onAction,
            secondary = c.structure, onSecondary = c.canvas,
            secondaryContainer = c.inset, onSecondaryContainer = c.structure,
            background = c.canvas, onBackground = c.structure,
            surface = c.canvas, onSurface = c.structure,
            surfaceVariant = c.inset, onSurfaceVariant = c.textMuted,
            surfaceContainer = c.inset, surfaceContainerHigh = c.inset, surfaceContainerHighest = c.track,
            surfaceContainerLow = c.canvas, surfaceContainerLowest = c.canvas,
            outline = c.borderMuted, outlineVariant = c.track,
            error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.action, onPrimary = c.onAction,
            primaryContainer = c.track, onPrimaryContainer = c.structure,
            secondary = c.structure, onSecondary = c.canvas,
            secondaryContainer = c.inset, onSecondaryContainer = c.structure,
            background = c.canvas, onBackground = c.structure,
            surface = c.canvas, onSurface = c.structure,
            surfaceVariant = c.inset, onSurfaceVariant = c.textMuted,
            surfaceContainer = c.inset, surfaceContainerHigh = c.inset, surfaceContainerHighest = c.track,
            surfaceContainerLow = c.canvas, surfaceContainerLowest = c.canvas,
            outline = c.borderMuted, outlineVariant = c.track,
            error = c.danger,
        )
    }
    val base = Typography()
    val typography = Typography(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
    )
    CompositionLocalProvider(LocalTc provides c, LocalGrain provides grain) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = Shapes(
                extraSmall = TcShape.control, small = TcShape.control,
                medium = TcShape.panel, large = TcShape.large, extraLarge = TcShape.large,
            ),
            content = content,
        )
    }
}

/** 技術標記用的小型大寫字（例如 STAGE、MODEL） */
val TcLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
    )
