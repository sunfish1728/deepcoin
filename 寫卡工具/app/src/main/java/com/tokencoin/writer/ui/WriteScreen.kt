package com.tokencoin.writer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tokencoin.writer.card.Presets
import com.tokencoin.writer.nfc.NfcIo
import com.tokencoin.writer.net.ApiProbe
import com.tokencoin.writer.net.ProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteScreen(state: AppState) {
    val f = state.form
    fun set(block: CardForm.() -> CardForm) = state.updateForm(f.block())

    var showKey by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ProbeResult?>(null) }
    val scope = rememberCoroutineScope()

    var uploading by remember { mutableStateOf(false) }
    var hostingStatus by remember { mutableStateOf("") }
    var uploaded by remember(f) { mutableStateOf<com.tokencoin.writer.card.TokenCard?>(null) }
    val (fullCard, errors) = remember(f) { f.toCard() }
    val card = if (f.hosted) uploaded else fullCard
    val aar = f.aarPackage.trim().ifEmpty { Presets.DEFAULT_AAR }.takeIf { f.aarEnabled }
    val size = card?.let { NfcIo.buildMessage(it, aar, f.webUrl).byteArrayLength }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("卡片") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field("卡片 ID", f.id, { set { copy(id = it) } }, Modifier.weight(1f), mono = true)
                IconButton(onClick = { set { copy(id = newCardId()) } }) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新產生 ID")
                }
            }
            Field("卡片名稱", f.title, { set { copy(title = it) } }, hint = "例如：DeepSeek娘幣")
        }

        Section("模型") {
            val vendorPreset = Presets.vendors.firstOrNull { it.id == f.vendor }
            PresetField(
                label = "AI 模型供應商 *",
                value = f.vendor,
                onValueChange = { set { copy(vendor = it) } },
                options = Presets.vendors.map { it.id to it.label },
                onPick = { id ->
                    val p = Presets.vendors.first { it.id == id }
                    set { copy(vendor = id, model = p.models.firstOrNull() ?: model) }
                },
                hint = vendorPreset?.label,
            )
            PresetField(
                label = "預設模型 *",
                value = f.model,
                onValueChange = { set { copy(model = it) } },
                options = vendorPreset?.models.orEmpty().map { it to it },
                onPick = { set { copy(model = it) } },
            )
            Field("其他可用模型", f.models, { set { copy(models = it) } }, hint = "以逗號分隔，可留空")
        }

        Section("API") {
            PresetField(
                label = "API 供應商名稱 *",
                value = f.provider,
                onValueChange = { set { copy(provider = it) } },
                options = Presets.providers.mapIndexed { i, p -> i.toString() to "${p.label}\n${p.base}" },
                onPick = { i ->
                    val p = Presets.providers[i.toInt()]
                    set { copy(provider = p.name, base = p.base, proto = p.proto) }
                },
                hint = "從清單選擇會一併填入地址與協議",
            )
            PresetField(
                label = "API 協議 *",
                value = f.proto,
                onValueChange = { set { copy(proto = it) } },
                options = Presets.protocols.map { (id, label) -> id to "$id — $label" },
                onPick = { set { copy(proto = it) } },
                hint = Presets.protocols.firstOrNull { it.first == f.proto }?.second,
            )
            Field(
                "API 地址（Base URL） *", f.base, { set { copy(base = it) } },
                keyboard = KeyboardType.Uri, mono = true,
            )
            OutlinedTextField(
                value = f.key,
                onValueChange = { set { copy(key = it.trim()) } },
                label = { Text("API 密鑰 *") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隱藏" else "顯示") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    probing = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { ApiProbe.probe(f.proto, f.base, f.key.trim()) }
                        probing = false
                        probe = r
                    }
                },
                enabled = !probing && f.key.isNotBlank() && f.base.startsWith("http"),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (probing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("檢測中…")
                } else {
                    Text("檢測模型列表與餘額")
                }
            }
        }

        Section("餘額與期限（僅記錄顯示，實際限制以供應商為準）") {
            Field("到期日", f.exp, { set { copy(exp = it) } }, hint = "YYYY-MM-DD，留空為不限", keyboard = KeyboardType.Number)
            Field("美元餘額（USD）", f.balanceUsd, { set { copy(balanceUsd = it) } }, hint = "例如 5.00，可用上方檢測自動填入", keyboard = KeyboardType.Decimal)
        }

        Section("電腦端（CC Switch）建議套用到") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Presets.apps.forEach { (id, label) ->
                    FilterChip(
                        selected = id in f.apps,
                        onClick = { set { copy(apps = if (id in apps) apps - id else apps + id) } },
                        label = { Text(label) },
                    )
                }
            }
        }

        Section("虛擬形象（選填，供日後配套軟體使用）") {
            Field("角色名稱", f.char, { set { copy(char = it) } }, hint = "例如：DeepSeek娘")
            Field("角色 ID", f.charId, { set { copy(charId = it) } }, mono = true)
            Field("形象 URL", f.avatar, { set { copy(avatar = it) } }, hint = "頭像 / Live2D / VRM 位址", keyboard = KeyboardType.Uri)
        }

        Section("其他") {
            Field("備註", f.note, { set { copy(note = it) } }, singleLine = false)
            Field("網頁版網址（未安裝 App 時使用）", f.webUrl, { set { copy(webUrl = it) } }, keyboard = KeyboardType.Uri,
                hint = "留空使用 https://sunfish1728.github.io/deepcoin-web/；也可填自己的 HTTPS 網址。只存一筆網址。")
            SwitchRow("外部託管", "除 API key 外的卡片設定公開託管至 GitHub", f.hosted) { set { copy(hosted = it) } }
            if (f.hosted) {
                OutlinedButton(onClick = {
                    val source = fullCard ?: return@OutlinedButton
                    uploading = true; hostingStatus = "正在上傳並核對…"
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { com.tokencoin.writer.net.HostedCards.upload(source) }
                            uploaded = result; hostingStatus = "已託管，可寫入卡片"
                        } catch (e: Exception) { hostingStatus = e.message ?: "上傳失敗" }
                        finally { uploading = false }
                    }
                }, enabled = fullCard != null && !uploading) { Text(if (uploading) "正在上傳…" else "上傳託管設定") }
                if (hostingStatus.isNotBlank()) Text(hostingStatus)
            }
        }

        SizeCard(size, errors)

        SwitchRow("寫入後永久鎖定", "鎖定後卡片無法再修改，無法復原", state.lockAfterWrite) {
            state.lockAfterWrite = it
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { saveDialog = true }, modifier = Modifier.weight(1f)) { Text("存為設定檔") }
            Button(
                onClick = { if (state.lockAfterWrite) confirmLock = true else state.startWrite(WriteJob(card!!, aar, false, f.webUrl)) },
                enabled = card != null,
                modifier = Modifier.weight(1f),
            ) { Text("寫入卡片") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (saveDialog) SaveProfileDialog(
        initial = f.title.ifBlank { "${f.provider} · ${f.model}" },
        onDismiss = { saveDialog = false },
        onSave = { state.saveProfile(it, f); saveDialog = false },
    )

    probe?.let { r ->
        ProbeDialog(
            result = r,
            currentModel = f.model,
            currentOthers = f.models,
            onDismiss = { probe = null },
            onApplyModels = { def, others -> set { copy(model = def, models = others.joinToString(", ")) } },
            onApplyBalance = { set { copy(balanceUsd = it) } },
            onApplyExpiry = { set { copy(exp = it) } },
        )
    }

    if (confirmLock && card != null) AlertDialog(
        onDismissRequest = { confirmLock = false },
        title = { Text("確定要永久鎖定？") },
        text = { Text("寫入後卡片會變成唯讀，之後無法修改或清除，包含 API 密鑰。") },
        confirmButton = {
            TextButton(onClick = {
                confirmLock = false
                state.startWrite(WriteJob(card, aar, true, f.webUrl))
            }) { Text("寫入並鎖定", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("取消") } },
    )
}

@Composable
private fun SizeCard(size: Int?, errors: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (size == null) {
                Text("尚有欄位未完成", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            } else {
                Text("資料大小：$size bytes", style = MaterialTheme.typography.titleSmall)
                Text(
                    Presets.tagCapacities.joinToString("　") { (n, c) -> "$n ${if (size <= c) "✓" else "✗"}" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SaveProfileDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存為設定檔") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("名稱") }, singleLine = true)
                Text("同名會覆蓋。設定檔以明文存在本機（含密鑰）。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("儲存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ---- 共用元件 ----

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    hint: String? = null,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}

/** 可自由輸入、也可從下拉清單挑選的欄位。options: (值, 顯示文字) */
@Composable
private fun PresetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    hint: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = hint?.let { { Text(it) } },
            singleLine = true,
            trailingIcon = if (options.isEmpty()) null else {
                { IconButton(onClick = { open = true }) { Icon(Icons.Default.ArrowDropDown, "選擇") } }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
