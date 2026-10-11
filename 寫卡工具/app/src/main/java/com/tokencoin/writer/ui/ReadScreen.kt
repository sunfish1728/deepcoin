package com.tokencoin.writer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tokencoin.writer.card.Presets
import com.tokencoin.writer.nfc.ReadResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun ReadScreen(state: AppState) {
    val r = state.lastRead
    if (r == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "將 Token幣 卡片靠近手機背面即可讀取\n（任何分頁下刷卡都會自動跳到這裡）",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    ReadResultView(r, state)
}

@Composable
private fun ReadResultView(r: ReadResult, state: AppState) {
    val clipboard = LocalClipboardManager.current
    var showKey by remember(r) { mutableStateOf(false) }
    val d = r.decoded

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (d != null) {
            val label = "已讀取卡片"
            val color = MaterialTheme.colorScheme.primaryContainer
            Card(colors = CardDefaults.cardColors(containerColor = color)) {
                Text(label, Modifier.padding(16.dp).fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
            }

            val c = d.card
            Section(c.title.ifBlank { "Token幣" }) {
                KV("卡片 ID", c.id, mono = true)
                KV("格式版本", "v${c.v}")
                KV("AI 模型供應商", Presets.vendors.firstOrNull { it.id == c.vendor }?.let { "${it.label}（${c.vendor}）" } ?: c.vendor)
                KV("預設模型", c.model, mono = true)
                if (c.models.isNotEmpty()) KV("其他模型", c.models.joinToString(", "), mono = true)
                KV("API 供應商", c.provider)
                KV("API 協議", c.proto)
                KV("API 地址", c.base, mono = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { KV("API 密鑰", if (showKey) c.key else maskKey(c.key), mono = true) }
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隱藏" else "顯示") }
                }
                KV("寫入時間", if (c.iat > 0) timeFmt.format(Instant.ofEpochSecond(c.iat)) else "—")
                KV("到期", c.exp?.let { timeFmt.format(Instant.ofEpochSecond(it)) + if (it < Instant.now().epochSecond) "（已過期）" else "" } ?: "不限")
                if (c.balanceUsd.isNotBlank()) KV("美元餘額", "$" + c.balanceUsd + "（寫入時）")
                if (c.apps.isNotEmpty()) KV("電腦端套用", c.apps.joinToString(", ") { a -> Presets.apps.firstOrNull { it.first == a }?.second ?: a })
                if (c.char.isNotBlank()) KV("角色", c.char)
                if (c.charId.isNotBlank()) KV("角色 ID", c.charId, mono = true)
                if (c.avatar.isNotBlank()) KV("形象 URL", c.avatar, mono = true)
                if (c.note.isNotBlank()) KV("備註", c.note)
                if (c.extra.isNotEmpty()) KV("其他欄位", c.extra.keys.joinToString(", "))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(d.canonicalJson)) },
                    modifier = Modifier.weight(1f),
                ) { Text("複製 JSON") }
                Button(
                    onClick = {
                        state.updateForm(CardForm.fromCard(c))
                        state.tab = 0
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("載入到寫卡頁") }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(r.error ?: "無法讀取", Modifier.padding(16.dp).fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
            }
        }

        Section("卡片資訊") {
            KV("UID", r.info.uid, mono = true)
            KV("類型", r.info.ndefType ?: "—")
            KV("技術", r.info.techs.joinToString(", "))
            KV("容量", "${r.usedBytes} / ${r.info.maxSize ?: "?"} bytes")
            KV("可寫入", when (r.info.writable) { true -> "是"; false -> "否（已鎖定）"; null -> "—" })
            KV("可鎖定", when (r.info.canMakeReadOnly) { true -> "是"; false -> "否"; null -> "—" })
            if (r.records.isNotEmpty()) KV("NDEF 記錄", r.records.joinToString("\n"))
        }
    }
}

private fun maskKey(k: String) =
    if (k.length <= 10) "•".repeat(k.length) else k.take(6) + "•".repeat(8) + k.takeLast(4)

@Composable
private fun KV(k: String, v: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            k,
            Modifier.width(104.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                v,
                style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
