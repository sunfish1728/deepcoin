package com.tokencoin.client.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokencoin.client.chat.ConversationSummary
import com.tokencoin.client.ui.components.BrandMark
import com.tokencoin.client.ui.components.TcButton
import com.tokencoin.client.ui.components.TcButtonKind
import com.tokencoin.client.ui.components.TcInput
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import com.tokencoin.client.ui.theme.tcCanvas
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zone = ZoneId.systemDefault()
private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val md = DateTimeFormatter.ofPattern("MM/dd")
private val ymd = DateTimeFormatter.ofPattern("yyyy/MM/dd")

fun shortTime(epochMs: Long): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        t.toLocalDate() == today -> t.format(hm)
        t.year == today.year -> t.format(md)
        else -> t.format(ymd)
    }
}

private val mdNoise = Regex("""(```\w*|[*_~`>#|]+|^\s*[-+]\s+|\[([^\]]*)]\([^)]*\))""", RegexOption.MULTILINE)

/** 預覽文字去掉 Markdown 符號 */
fun plainPreview(s: String): String =
    s.replace(mdNoise) { m -> m.groups[2]?.value ?: " " }.replace(Regex("\\s+"), " ").trim()

@Composable
fun ConversationDrawer(
    conversations: List<ConversationSummary>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (ConversationSummary) -> Unit,
    onDelete: (ConversationSummary) -> Unit,
) {
    val c = Tc.colors
    var query by remember { mutableStateOf("") }
    val shown = conversations.filter {
        query.isBlank() || it.title.contains(query, true) || it.preview.contains(query, true)
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(304.dp)
            .tcCanvas()
            .drawBehind {
                drawRect(c.structure, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BrandMark()
        Spacer(Modifier.height(14.dp))
        TcButton("新對話", onNew, kind = TcButtonKind.Primary, icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        TcInput(query, { query = it }, placeholder = "搜尋對話", singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        TcSectionLabel("HISTORY · ${conversations.size}")
        Spacer(Modifier.height(6.dp))
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (conversations.isEmpty()) "還沒有對話" else "找不到符合的對話", color = c.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.id }) { s ->
                    DrawerItem(s, s.id == currentId, { onSelect(s.id) }, { onRename(s) }, { onDelete(s) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(
    s: ConversationSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Tc.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(TcShape.control)
                .then(if (selected) Modifier.background(c.inset) else Modifier)
                .drawBehind { if (selected) drawRect(c.action, size = Size(3.dp.toPx(), size.height)) }
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.title, color = c.structure, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (s.preview.isNotBlank()) {
                    Text(
                        s.preview.replace('\n', ' '), color = c.textMuted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(shortTime(s.updatedAt), color = c.textMuted.copy(alpha = 0.8f), style = TcLabelStyle)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("重新命名") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("刪除", color = c.danger) }, onClick = { menu = false; onDelete() })
        }
    }
}
