package com.tokencoin.client.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokencoin.client.ui.components.TcTextAction
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcColors
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape

// ---------------- 區塊解析 ----------------

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class ListBlock(val items: List<ListItem>) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

data class ListItem(val indent: Int, val marker: String, val text: String)

private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
private val ruleRe = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val listRe = Regex("^(\\s*)([-*+]|\\d{1,3}[.)])\\s+(.*)$")
private val tableSepRe = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")
private val quoteRe = Regex("^\\s*>\\s?(.*)$")

private fun isFence(l: String) = l.trimStart().startsWith("```")

fun parseMarkdown(src: String): List<MdBlock> {
    val lines = src.replace("\r\n", "\n").split('\n')
    val out = mutableListOf<MdBlock>()
    var i = 0

    fun startsBlock(l: String, next: String?) =
        isFence(l) || headingRe.matches(l) || ruleRe.matches(l) || quoteRe.matches(l) || listRe.matches(l) ||
            (l.trimStart().startsWith("|") && next != null && tableSepRe.matches(next))

    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() -> i++
            isFence(line) -> {
                val lang = line.trimStart().removePrefix("```").trim()
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !isFence(lines[i])) body += lines[i++]
                i++ // 跳過結尾 fence（串流中可能還沒出現）
                out += MdBlock.Code(lang, body.joinToString("\n"))
            }
            headingRe.matches(line) -> {
                val m = headingRe.find(line)!!
                out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim().trimEnd('#').trim())
                i++
            }
            ruleRe.matches(line) -> { out += MdBlock.Rule; i++ }
            line.trimStart().startsWith("|") && i + 1 < lines.size && tableSepRe.matches(lines[i + 1]) -> {
                val rows = mutableListOf(splitRow(line))
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) rows += splitRow(lines[i++])
                out += MdBlock.Table(rows)
            }
            quoteRe.matches(line) -> {
                val body = mutableListOf<String>()
                while (i < lines.size && quoteRe.matches(lines[i])) body += quoteRe.find(lines[i++])!!.groupValues[1]
                out += MdBlock.Quote(body.joinToString("\n"))
            }
            listRe.matches(line) -> {
                val items = mutableListOf<ListItem>()
                while (i < lines.size) {
                    val l = lines[i]
                    val m = listRe.find(l)
                    when {
                        m != null -> {
                            items += ListItem(m.groupValues[1].replace("\t", "    ").length / 2, m.groupValues[2], m.groupValues[3])
                            i++
                        }
                        // 縮排的續行併入上一項
                        l.isNotBlank() && l.startsWith("  ") && items.isNotEmpty() -> {
                            val last = items.removeAt(items.lastIndex)
                            items += last.copy(text = last.text + "\n" + l.trim())
                            i++
                        }
                        // 空行後若還是清單，繼續同一個清單
                        l.isBlank() && i + 1 < lines.size && listRe.matches(lines[i + 1]) -> i++
                        else -> break
                    }
                }
                out += MdBlock.ListBlock(items)
            }
            else -> {
                val body = mutableListOf(line)
                i++
                while (i < lines.size && lines[i].isNotBlank() && !startsBlock(lines[i], lines.getOrNull(i + 1))) {
                    body += lines[i++]
                }
                out += MdBlock.Paragraph(body.joinToString("\n"))
            }
        }
    }
    return out
}

private fun splitRow(l: String): List<String> =
    l.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

// ---------------- 行內解析 ----------------

fun inlineMarkdown(text: String, c: TcColors): AnnotatedString = buildAnnotatedString {
    appendInline(text, c)
}

private fun AnnotatedString.Builder.appendInline(s: String, c: TcColors) {
    var i = 0
    val plain = StringBuilder()
    fun flush() { if (plain.isNotEmpty()) { append(plain.toString()); plain.clear() } }

    while (i < s.length) {
        val ch = s[i]
        // 跳脫
        if (ch == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_~[]()#>!|") {
            plain.append(s[i + 1]); i += 2; continue
        }
        // 行內程式碼
        if (ch == '`') {
            val end = s.indexOf('`', i + 1)
            if (end > i) {
                flush()
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.track, fontSize = 13.sp, color = c.data)) {
                    append(" " + s.substring(i + 1, end) + " ")
                }
                i = end + 1; continue
            }
        }
        // 粗體
        if ((s.startsWith("**", i) || s.startsWith("__", i))) {
            val mark = s.substring(i, i + 2)
            val end = s.indexOf(mark, i + 2)
            if (end > i + 2) {
                flush()
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(s.substring(i + 2, end), c) }
                i = end + 2; continue
            }
        }
        // 刪除線
        if (s.startsWith("~~", i)) {
            val end = s.indexOf("~~", i + 2)
            if (end > i + 2) {
                flush()
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(s.substring(i + 2, end), c) }
                i = end + 2; continue
            }
        }
        // 斜體：* 或 _（_ 需在詞邊界，避免 snake_case）
        if ((ch == '*' || ch == '_') && i + 1 < s.length && !s[i + 1].isWhitespace() &&
            (ch == '*' || i == 0 || !s[i - 1].isLetterOrDigit())
        ) {
            val end = s.indexOf(ch, i + 1)
            if (end > i + 1 && !s[end - 1].isWhitespace() &&
                (ch == '*' || end + 1 >= s.length || !s[end + 1].isLetterOrDigit())
            ) {
                flush()
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(s.substring(i + 1, end), c) }
                i = end + 1; continue
            }
        }
        // 連結 [文字](網址)
        if (ch == '[') {
            val close = s.indexOf("](", i + 1)
            val paren = if (close > 0) s.indexOf(')', close + 2) else -1
            if (close > i && paren > close) {
                flush()
                val url = s.substring(close + 2, paren).trim().substringBefore(' ')
                withLink(LinkAnnotation.Url(url, linkStyles(c))) { appendInline(s.substring(i + 1, close), c) }
                i = paren + 1; continue
            }
        }
        // 裸網址
        if (ch == 'h' && (s.startsWith("https://", i) || s.startsWith("http://", i)) &&
            (i == 0 || !s[i - 1].isLetterOrDigit())
        ) {
            var end = i
            while (end < s.length && !s[end].isWhitespace() && s[end] !in "）)」』，。>") end++
            flush()
            val url = s.substring(i, end)
            withLink(LinkAnnotation.Url(url, linkStyles(c))) { append(url) }
            i = end; continue
        }
        plain.append(ch)
        i++
    }
    flush()
}

private fun linkStyles(c: TcColors) =
    TextLinkStyles(SpanStyle(color = c.action, textDecoration = TextDecoration.Underline))

// ---------------- 繪製 ----------------

@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier, textColor: androidx.compose.ui.graphics.Color = Tc.colors.structure) {
    val c = Tc.colors
    val blocks = remember(text) { parseMarkdown(text) }
    val body = MaterialTheme.typography.bodyLarge.copy(color = textColor)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    inlineMarkdown(b.text, c),
                    style = body.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = when (b.level) { 1 -> 21.sp; 2 -> 19.sp; 3 -> 17.sp; else -> 16.sp },
                        lineHeight = 27.sp,
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                )
                is MdBlock.Paragraph -> Text(inlineMarkdown(b.text, c), style = body)
                is MdBlock.Code -> CodeBlock(b)
                is MdBlock.Quote -> Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind { drawRect(c.borderMuted, size = Size(3.dp.toPx(), size.height)) }
                        .padding(start = 12.dp),
                ) {
                    MarkdownView(b.text, textColor = c.textMuted)
                }
                is MdBlock.ListBlock -> ListView(b, body)
                is MdBlock.Table -> TableView(b, body)
                MdBlock.Rule -> HorizontalDivider(color = c.borderMuted)
            }
        }
    }
}

@Composable
private fun ListView(b: MdBlock.ListBlock, body: TextStyle) {
    val c = Tc.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        b.items.forEach { item ->
            Row(Modifier.padding(start = (item.indent * 16).dp)) {
                val ordered = item.marker.first().isDigit()
                Text(
                    if (ordered) item.marker else if (item.indent == 0) "■" else "▪",
                    style = body.copy(
                        color = c.action,
                        fontSize = if (ordered) body.fontSize else 9.sp,
                        fontFamily = if (ordered) FontFamily.Monospace else null,
                    ),
                    modifier = Modifier.width(if (ordered) 26.dp else 16.dp).padding(top = if (ordered) 0.dp else 3.dp),
                )
                Text(inlineMarkdown(item.text, c), style = body, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CodeBlock(b: MdBlock.Code) {
    val c = Tc.colors
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TcShape.panel)
            .background(c.inset)
            .border(1.dp, c.borderMuted.copy(alpha = 0.6f), TcShape.panel),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.track)
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(b.lang.ifBlank { "code" }.uppercase(), color = c.textMuted, style = TcLabelStyle, modifier = Modifier.weight(1f))
            TcTextAction("複製", { clipboard.setText(AnnotatedString(b.code)) })
        }
        Text(
            b.code,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = c.structure),
            softWrap = false,
        )
    }
}

@Composable
private fun TableView(b: MdBlock.Table, body: TextStyle) {
    val c = Tc.colors
    val cols = b.rows.maxOf { it.size }
    // 依各欄最長文字估算寬度，讓欄位對齊
    val widths = (0 until cols).map { col ->
        val len = b.rows.maxOf { r -> r.getOrNull(col)?.let { cell -> cell.sumOf { ch -> (if (ch.code > 0x2E80) 2 else 1).toInt() } } ?: 0 }
        (len * 7 + 20).coerceIn(56, 240).dp
    }
    Column(
        Modifier
            .clip(TcShape.panel)
            .border(1.dp, c.borderMuted.copy(alpha = 0.7f), TcShape.panel)
            .horizontalScroll(rememberScrollState()),
    ) {
        b.rows.forEachIndexed { ri, row ->
            Row(if (ri == 0) Modifier.background(c.inset) else Modifier) {
                (0 until cols).forEach { ci ->
                    Text(
                        inlineMarkdown(row.getOrNull(ci).orEmpty(), c),
                        style = body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (ri == 0) FontWeight.SemiBold else null),
                        modifier = Modifier.width(widths[ci]).padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            if (ri < b.rows.lastIndex) HorizontalDivider(color = c.borderMuted.copy(alpha = 0.35f))
        }
    }
}
