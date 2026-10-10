package com.tokencoin.client.ui.writer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokencoin.client.card.Presets
import com.tokencoin.client.card.TokenCard
import com.tokencoin.client.net.ProbeResult
import com.tokencoin.client.ui.chat.ConfirmDialog
import com.tokencoin.client.ui.components.TcButton
import com.tokencoin.client.ui.components.TcButtonKind
import com.tokencoin.client.ui.components.TcIconButton
import com.tokencoin.client.ui.components.TcInput
import com.tokencoin.client.ui.components.TcPanel
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.components.TcTextAction
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import com.tokencoin.client.ui.theme.tcCanvas
import com.tokencoin.client.writer.Profile
import com.tokencoin.client.writer.WriteStatus
import com.tokencoin.client.writer.WriterState
import com.tokencoin.client.writer.newCardId
import java.time.Instant
import java.time.ZoneId

/** 檢測結果：選預設模型（單選）與其他模型（多選），並可填入餘額與到期日。 */
@Composable
fun ProbeDialog(
    result: ProbeResult,
    currentModel: String,
    currentOthers: String,
    onDismiss: () -> Unit,
    onApplyModels: (default: String, others: List<String>) -> Unit,
    onApplyBalance: (String) -> Unit,
    onApplyExpiry: (String) -> Unit,
) {
    val c = Tc.colors
    val all = result.models
    var default by remember { mutableStateOf(currentModel.takeIf { it in all } ?: all.firstOrNull().orEmpty()) }
    var others by remember {
        mutableStateOf(currentOthers.split(',', '，').map { it.trim() }.filter { it in all }.toSet())
    }
    var filter by remember { mutableStateOf("") }
    val shown = all.filter { filter.isBlank() || it.contains(filter.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.canvas,
        shape = TcShape.large,
        title = { Text("檢測結果", color = c.structure) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TcSectionLabel("BALANCE")
                if (result.balances.isEmpty()) {
                    Text(result.balanceError ?: "無資料", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
                result.balances.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${b.amount} ${b.currency}", style = MaterialTheme.typography.bodyLarge, color = c.data)
                            Text(b.label, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                        when {
                            b.currency == "USD" && b.amount.toDoubleOrNull() != null ->
                                TcTextAction("填入", { onApplyBalance(b.amount) }, color = c.action)
                            b.currency != "USD" -> Text("非美元", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                    }
                }
                result.expiresAt?.let { exp ->
                    val date = Instant.ofEpochSecond(exp).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("密鑰到期：$date", Modifier.weight(1f), color = c.structure)
                        TcTextAction("填入", { onApplyExpiry(date) }, color = c.action)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TcSectionLabel("MODELS · ${all.size}　◉ 預設　☑ 其他", Modifier.weight(1f))
                    if (all.isNotEmpty()) TcTextAction("其他全選", { others = all.toSet() - default }, color = c.action)
                }
                if (all.isNotEmpty()) TcTextAction(
                    "預設模型 + 使用 API 的所有模型（卡上記「*」）",
                    { onApplyModels(default, listOf(Presets.ALL_MODELS)); onDismiss() },
                    color = c.action,
                )
                if (all.isEmpty()) {
                    Text(result.modelsError ?: "沒有模型", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                } else {
                    if (all.size > 8) {
                        TcInput(filter, { filter = it }, placeholder = "搜尋", singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it }) { m ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { default = m },
                            ) {
                                RadioButton(
                                    selected = default == m, onClick = { default = m },
                                    colors = RadioButtonDefaults.colors(selectedColor = c.action, unselectedColor = c.borderMuted),
                                )
                                Text(
                                    m, Modifier.weight(1f),
                                    color = c.structure,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Tc.mono),
                                )
                                Checkbox(
                                    checked = m in others && m != default,
                                    enabled = m != default,
                                    onCheckedChange = { others = if (it) others + m else others - m },
                                    colors = CheckboxDefaults.colors(checkedColor = c.action, uncheckedColor = c.borderMuted, checkmarkColor = c.onAction),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TcButton(
                "套用模型",
                { onApplyModels(default, all.filter { it in others && it != default }); onDismiss() },
                kind = TcButtonKind.Primary,
                enabled = default.isNotEmpty(),
            )
        },
        dismissButton = { TcButton("關閉", onDismiss, kind = TcButtonKind.Ghost) },
    )
}

/** 設定檔清單：點一下載入表單 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesSheet(
    profiles: List<Profile>,
    onLoad: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tc.colors
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
        shape = TcShape.large,
        dragHandle = null,
    ) {
        Column(Modifier.tcCanvas().navigationBarsPadding().padding(20.dp)) {
            Text("設定檔", style = MaterialTheme.typography.titleLarge, color = c.structure)
            Text("常用的表單內容，以明文存在本機（含密鑰）。載入時會換新的卡片 ID。", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            Spacer(Modifier.height(12.dp))
            if (profiles.isEmpty()) {
                Text("還沒有設定檔。在寫卡頁按「存為設定檔」即可保存。", color = c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
            }
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it.name }) { p ->
                    TcPanel(Modifier.fillMaxWidth().clickable { onLoad(p) }, borderColor = c.borderMuted, contentPadding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, color = c.structure, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${p.form.provider} · ${p.form.model}" + (p.form.char.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TcIconButton(Icons.Default.Delete, "刪除", { toDelete = p }, tint = c.textMuted, size = 34.dp)
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { p ->
        ConfirmDialog(
            title = "刪除設定檔？",
            text = "「${p.name}」將從本機移除，已寫入的卡片不受影響。",
            confirm = "刪除",
            onDismiss = { toDelete = null },
            onConfirm = { onDelete(p); toDelete = null },
        )
    }
}

/** 寫卡流程：等待刷卡 → 成功 / 失敗 */
@Composable
fun WriteDialog(w: WriterState, onApplyWritten: (TokenCard) -> Unit) {
    val c = Tc.colors
    when (val s = w.writeStatus) {
        null -> Unit
        WriteStatus.Waiting -> AlertDialog(
            onDismissRequest = {},
            containerColor = c.canvas,
            shape = TcShape.large,
            title = { Text("請將卡片靠近手機", color = c.structure) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Spinner(48.dp)
                    Text("把 NFC 卡貼在手機背面的感應區，寫入完成前請勿移開。", color = c.textMuted)
                    if (w.pendingWrite?.lock == true) Text("⚠ 寫入後將永久鎖定", color = c.danger)
                }
            },
            confirmButton = {},
            dismissButton = { TcButton("取消", { w.cancelWrite() }, kind = TcButtonKind.Ghost) },
        )
        is WriteStatus.Success -> AlertDialog(
            onDismissRequest = { w.dismissStatus() },
            containerColor = c.canvas,
            shape = TcShape.large,
            title = { Text("寫入成功", color = c.structure) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.message, color = c.textMuted, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Tc.mono))
                    TcButton(
                        "同時設為本機卡片",
                        { onApplyWritten(s.card); w.dismissStatus() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("不設定的話，之後刷這張卡也會自動讀入。", color = c.textMuted, style = TcLabelStyle)
                }
            },
            confirmButton = {
                TcButton("完成（下一張換新 ID）", {
                    w.dismissStatus()
                    w.updateForm(w.form.copy(id = newCardId()))
                }, kind = TcButtonKind.Primary)
            },
            dismissButton = { TcButton("關閉", { w.dismissStatus() }, kind = TcButtonKind.Ghost) },
        )
        is WriteStatus.Failed -> AlertDialog(
            onDismissRequest = { w.dismissStatus() },
            containerColor = c.canvas,
            shape = TcShape.large,
            title = { Text("寫入失敗", color = c.danger) },
            text = { Text(s.message, color = c.textMuted) },
            confirmButton = { TcButton("確定", { w.dismissStatus() }, kind = TcButtonKind.Primary) },
        )
    }
}
