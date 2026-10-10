package com.tokencoin.client.ui.avatar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import kotlinx.coroutines.delay

/**
 * 放置共用渲染器的舞台。
 * @param preset 鏡頭：full / bust / face
 * @param rotatable 是否允許水平拖曳轉身（聊天面板關閉，避免和抽屜手勢衝突）
 * @param overlay 疊在模型上的額外內容（工具列、空狀態等）
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AvatarStage(
    host: AvatarHost,
    preset: String,
    rotatable: Boolean,
    modifier: Modifier = Modifier,
    corners: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val key = remember { Any() }
    DisposableEffect(host) { onDispose { host.hideSlot(key) } }
    LaunchedEffect(preset, rotatable) {
        host.camera(preset)
        host.rotatable(rotatable)
    }
    val c = Tc.colors
    Box(modifier) {
        // WebView 在底層原生 View（見 AvatarHost）；這裡只回報位置、轉發觸控
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val b = coords.boundsInWindow()
                    host.showSlot(key, android.graphics.Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt()))
                }
                .pointerInteropFilter { host.dispatchTouch(it) },
        )
        if (corners) Box(Modifier.fillMaxSize().stageCorners(c.borderMuted.copy(alpha = 0.7f)))
        StageStatusOverlay(host.status)
        SpeechBubble(host.line, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        overlay()
    }
}

/** 取景框四角 */
fun Modifier.stageCorners(color: androidx.compose.ui.graphics.Color) = drawBehind {
    val l = 22.dp.toPx()
    val s = 1.5.dp.toPx()
    val w = size.width
    val h = size.height
    listOf(
        Offset(0f, 0f) to Pair(1f, 1f), Offset(w, 0f) to Pair(-1f, 1f),
        Offset(0f, h) to Pair(1f, -1f), Offset(w, h) to Pair(-1f, -1f),
    ).forEach { (o, d) ->
        drawLine(color, o, Offset(o.x + l * d.first, o.y), s, StrokeCap.Square)
        drawLine(color, o, Offset(o.x, o.y + l * d.second), s, StrokeCap.Square)
    }
}

@Composable
private fun BoxScope.StageStatusOverlay(status: StageStatus) {
    val c = Tc.colors
    when (status) {
        is StageStatus.Loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "LOADING" + (status.progress?.let { " ${(it * 100).toInt()}%" } ?: ""),
                color = c.textMuted, style = TcLabelStyle,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(120.dp).height(3.dp).clip(TcShape.control).background(c.track)) {
                Box(Modifier.fillMaxWidth(status.progress ?: 0.3f).height(3.dp).background(c.action))
            }
        }
        is StageStatus.Failed -> Text(
            status.message,
            color = c.danger,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
        )
        else -> {}
    }
}

/** 角色的一句話：從上方滑入，幾秒後淡出 */
@Composable
private fun SpeechBubble(line: StageLine?, modifier: Modifier) {
    val c = Tc.colors
    var shown by remember { mutableStateOf<StageLine?>(null) }
    LaunchedEffect(line?.id) {
        if (line == null) return@LaunchedEffect
        shown = line
        delay(2600)
        shown = null
    }
    var last by remember { mutableStateOf("") }
    shown?.let { last = it.text }
    AnimatedVisibility(
        visible = shown != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut(),
    ) {
        Text(
            last,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clip(TcShape.panel)
                .background(c.canvas.copy(alpha = 0.92f))
                .border(1.dp, c.structure, TcShape.panel)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = c.structure,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
