package com.tokencoin.client.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokencoin.client.chat.ChatMessage
import com.tokencoin.client.chat.MessageStatus
import com.tokencoin.client.ui.components.StatusDot
import com.tokencoin.client.ui.components.TcTag
import com.tokencoin.client.ui.components.TcTextAction
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessage(
    msg: ChatMessage,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Tc.colors
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box {
            Text(
                msg.content,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(10.dp, 10.dp, 3.dp, 10.dp))
                    .background(c.inset)
                    .border(1.dp, c.borderMuted.copy(alpha = 0.7f), RoundedCornerShape(10.dp, 10.dp, 3.dp, 10.dp))
                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = c.structure,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("複製") }, onClick = { menu = false; clipboard.setText(AnnotatedString(msg.content)) })
                if (enabled) {
                    DropdownMenuItem(text = { Text("編輯並重新送出") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("刪除") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text(
            timeFmt.format(Instant.ofEpochMilli(msg.createdAt)),
            color = c.textMuted.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp, end = 2.dp),
        )
    }
}

@Composable
fun AssistantMessage(
    msg: ChatMessage,
    actionsEnabled: Boolean,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Tc.colors
    val clipboard = LocalClipboardManager.current
    val streaming = msg.status == MessageStatus.STREAMING
    val railColor = when (msg.status) {
        MessageStatus.STREAMING -> c.action
        MessageStatus.ERROR -> c.danger
        else -> c.borderMuted.copy(alpha = 0.6f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(railColor, size = Size(2.dp.toPx(), size.height)) }
            .padding(start = 12.dp),
    ) {
        // 標頭：狀態點、模型、耗時與用量
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (msg.status) {
                MessageStatus.STREAMING -> Blinking { StatusDot(c.action) }
                MessageStatus.ERROR -> StatusDot(c.danger)
                MessageStatus.STOPPED -> StatusDot(c.textMuted)
                MessageStatus.DONE -> StatusDot(c.success)
            }
            Spacer(Modifier.width(6.dp))
            Text(msg.model.ifBlank { "assistant" }.uppercase(), color = c.textMuted, style = TcLabelStyle, maxLines = 1)
            Spacer(Modifier.weight(1f))
            val meta = buildList {
                msg.durationMs?.let { add("%.1fs".format(it / 1000.0)) }
                msg.usage?.let { add("↑${it.input} ↓${it.output}") }
                add(timeFmt.format(Instant.ofEpochMilli(msg.createdAt)))
            }.joinToString("  ")
            Text(meta, color = c.textMuted.copy(alpha = 0.7f), style = TcLabelStyle.copy(letterSpacing = 0.sp))
        }
        Spacer(Modifier.height(6.dp))

        if (msg.reasoning.isNotBlank()) {
            ReasoningBlock(msg.reasoning, autoExpand = streaming && msg.content.isEmpty())
            Spacer(Modifier.height(8.dp))
        }

        when {
            msg.content.isNotEmpty() -> SelectionContainer {
                MarkdownView(if (streaming) msg.content + " ▍" else msg.content)
            }
            streaming && msg.reasoning.isBlank() -> Blinking {
                Text("連線中…", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (msg.status == MessageStatus.ERROR) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(TcShape.control)
                    .border(1.dp, c.danger.copy(alpha = 0.8f), TcShape.control)
                    .padding(10.dp),
            ) {
                Text("請求失敗", color = c.danger, style = MaterialTheme.typography.labelLarge)
                Text(msg.error ?: "未知錯誤", color = c.structure, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (!streaming) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (msg.status == MessageStatus.STOPPED) {
                    TcTag("已停止")
                    Spacer(Modifier.width(4.dp))
                }
                if (msg.content.isNotEmpty()) TcTextAction("複製", { clipboard.setText(AnnotatedString(msg.content)) })
                if (actionsEnabled) {
                    TcTextAction(if (msg.status == MessageStatus.ERROR) "重試" else "重新產生", onRegenerate, color = c.action)
                    TcTextAction("刪除", onDelete)
                }
            }
        }
    }
}

@Composable
private fun ReasoningBlock(text: String, autoExpand: Boolean) {
    val c = Tc.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val show = expanded || autoExpand
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TcShape.control)
            .background(c.inset.copy(alpha = 0.7f))
            .animateContentSize(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !show }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("思考過程", color = c.textMuted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Text("${text.length} 字", color = c.textMuted.copy(alpha = 0.7f), style = TcLabelStyle)
            Spacer(Modifier.weight(1f))
            Text(if (show) "收合 ▴" else "展開 ▾", color = c.textMuted, style = MaterialTheme.typography.labelMedium)
        }
        if (show) {
            Text(
                text.trim(),
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = c.textMuted, fontFamily = FontFamily.Default),
            )
        }
    }
}

@Composable
fun Blinking(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "blink")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.alpha(a)) { content() }
}
