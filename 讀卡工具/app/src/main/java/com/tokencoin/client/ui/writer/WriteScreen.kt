package com.tokencoin.client.ui.writer

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tokencoin.client.NfcAvailability
import com.tokencoin.client.avatar.AvatarRepository
import com.tokencoin.client.card.Presets
import com.tokencoin.client.ui.avatar.AvatarRepoBrowser
import com.tokencoin.client.card.TokenCard
import com.tokencoin.client.net.ApiProbe
import com.tokencoin.client.net.ProbeResult
import com.tokencoin.client.nfc.NfcIo
import com.tokencoin.client.ui.chat.ConfirmDialog
import com.tokencoin.client.ui.chat.TextEditDialog
import com.tokencoin.client.ui.components.StatusDot
import com.tokencoin.client.ui.components.TcButton
import com.tokencoin.client.ui.components.TcButtonKind
import com.tokencoin.client.ui.components.TcIconButton
import com.tokencoin.client.ui.components.TcPanel
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.components.TcSwitchRow
import com.tokencoin.client.ui.components.TcTag
import com.tokencoin.client.ui.components.TcTextAction
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.writer.CardForm
import com.tokencoin.client.writer.WriteJob
import com.tokencoin.client.writer.WriterState
import com.tokencoin.client.writer.newCardId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 寫卡分頁（移植自寫卡工具）：表單 → 檢測模型與餘額 → 刷卡寫入。
 * @param current 本機目前的卡片，可載入為底稿
 * @param onApplyWritten 寫入成功後，使用者選擇把它設為本機卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteScreen(
    w: WriterState,
    nfc: NfcAvailability,
    current: TokenCard?,
    avatars: AvatarRepository,
    onApplyWritten: (TokenCard) -> Unit,
) {
    val c = Tc.colors
    val f = w.form
    fun set(block: CardForm.() -> CardForm) = w.updateForm(f.block())

    var showKey by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var profilesSheet by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmLoad by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }
    var pickAvatar by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ProbeResult?>(null) }
    val scope = rememberCoroutineScope()

    val (card, errors) = remember(f) { f.toCard() }
    val aar = f.aarPackage.trim().ifEmpty { Presets.DEFAULT_AAR }.takeIf { f.aarEnabled }
    val size = card?.let { NfcIo.buildMessage(it, aar, f.webUrl).byteArrayLength }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NfcBanner(nfc)

        Row(verticalAlignment = Alignment.CenterVertically) {
            TcSectionLabel("WRITE CARD", Modifier.weight(1f))
            if (current != null) TcTextAction("載入本機卡片", { confirmLoad = true })
            TcTextAction("設定檔 · ${w.profiles.size}", { profilesSheet = true })
        }

        FormSection("卡片") {
            FormField(
                "卡片 ID", f.id, { set { copy(id = it) } }, mono = true,
                trailing = { TcIconButton(Icons.Default.Refresh, "重新產生 ID", { set { copy(id = newCardId()) } }, size = 34.dp) },
            )
            FormField("卡片名稱", f.title, { set { copy(title = it) } }, placeholder = "例如：DeepSeek娘幣")
        }

        FormSection("模型") {
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
                mono = true,
            )
            val allModels = f.models.trim() == Presets.ALL_MODELS
            TcSwitchRow(
                "使用此 API 的所有模型",
                "卡上只記一個「*」，讀卡時自動向 API 取得模型清單，供應商新增的模型也會出現",
                allModels,
                { set { copy(models = if (it) Presets.ALL_MODELS else "") } },
            )
            if (!allModels) {
                FormField("其他可用模型", f.models, { set { copy(models = it) } }, hint = "以逗號分隔，可留空；也可以用下方「檢測」挑選", singleLine = false, mono = true)
            }
        }

        FormSection("API") {
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
                mono = true,
            )
            FormField("API 地址（Base URL） *", f.base, { set { copy(base = it) } }, keyboard = KeyboardType.Uri, mono = true)
            FormField(
                "API 密鑰 *", f.key, { set { copy(key = it.trim()) } },
                keyboard = KeyboardType.Password, mono = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = { TcTextAction(if (showKey) "隱藏" else "顯示", { showKey = !showKey }) },
            )
            TcButton(
                text = if (probing) "檢測中…" else "檢測模型列表與餘額",
                onClick = {
                    probing = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { ApiProbe.probe(f.proto, f.base, f.key.trim()) }
                        probing = false
                        probe = r
                    }
                },
                icon = if (probing) null else Icons.Default.Search,
                enabled = !probing && f.key.isNotBlank() && f.base.startsWith("http"),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormSection("餘額與期限", note = "僅記錄顯示，實際限制以供應商為準") {
            FormField("到期日", f.exp, { set { copy(exp = it) } }, placeholder = "YYYY-MM-DD，留空為不限", mono = true)
            FormField("美元餘額（USD）", f.balanceUsd, { set { copy(balanceUsd = it) } }, placeholder = "例如 5.00，可用上方檢測填入", keyboard = KeyboardType.Decimal, mono = true)
        }

        FormSection("電腦端（CC Switch）建議套用到") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Presets.apps.forEach { (id, label) ->
                    TcTag(label, selected = id in f.apps, onClick = { set { copy(apps = if (id in apps) apps - id else apps + id) } })
                }
            }
        }

        FormSection("虛擬形象", note = "選填。讀卡時依角色 ID 記住形象，URL 可指向 .vrm 或角色包 .zip") {
            FormField("角色名稱", f.char, { set { copy(char = it) } }, placeholder = "例如：DeepSeek娘")
            FormField("角色 ID", f.charId, { set { copy(charId = it) } }, mono = true)
            FormField(
                "形象 URL", f.avatar, { set { copy(avatar = it) } }, keyboard = KeyboardType.Uri, mono = true,
                hint = "可填 .vrm / .zip 網址，或形象庫的 GitHub 倉庫網址（依角色 ID 挑角色）",
            )
            TcButton("從形象庫選擇", { pickAvatar = true }, icon = Icons.Default.Search, modifier = Modifier.fillMaxWidth())
        }

        FormSection("其他") {
            FormField("備註", f.note, { set { copy(note = it) } }, singleLine = false)
            FormField("網頁版網址（未安裝 App 時使用）", f.webUrl, { set { copy(webUrl = it) } }, keyboard = KeyboardType.Uri,
                hint = "留空使用 https://sunfish1728.github.io/deepcoin-web/；也可填自己的 HTTPS 網站。卡片只存一筆網址，不重複存資料。")
        }

        SizePanel(size, errors)

        TcSwitchRow("寫入後永久鎖定", "鎖定後卡片無法再修改，無法復原", w.lockAfterWrite, { w.lockAfterWrite = it })

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TcButton("存為設定檔", { saveDialog = true }, modifier = Modifier.weight(1f), height = 48.dp)
            TcButton(
                "寫入卡片",
                { if (w.lockAfterWrite) confirmLock = true else w.startWrite(WriteJob(card!!, aar, false, f.webUrl)) },
                kind = TcButtonKind.Primary,
                icon = Icons.Default.Edit,
                enabled = card != null && nfc == NfcAvailability.READY,
                modifier = Modifier.weight(1f),
                height = 48.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (saveDialog) TextEditDialog(
        title = "存為設定檔",
        initial = f.title.ifBlank { "${f.provider} · ${f.model}" },
        confirm = "儲存",
        hint = "同名會覆蓋。設定檔以明文存在本機（含密鑰）。",
        singleLine = true,
        onDismiss = { saveDialog = false },
        onConfirm = { w.saveProfile(it, f); saveDialog = false },
    )

    if (profilesSheet) ProfilesSheet(
        profiles = w.profiles,
        onLoad = {
            // 載入設定檔時換新卡片 ID，避免多張卡重複
            w.updateForm(it.form.copy(id = newCardId()))
            profilesSheet = false
        },
        onDelete = { w.deleteProfile(it) },
        onDismiss = { profilesSheet = false },
    )

    if (confirmLoad && current != null) ConfirmDialog(
        title = "載入本機卡片？",
        text = "目前表單會被本機卡片的內容取代（卡片 ID 會換新）。",
        confirm = "載入",
        onDismiss = { confirmLoad = false },
        onConfirm = { w.loadFromCard(current); confirmLoad = false },
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

    if (confirmLock && card != null) ConfirmDialog(
        title = "確定要永久鎖定？",
        text = "寫入後卡片會變成唯讀，之後無法修改或清除，包含 API 密鑰。",
        confirm = "寫入並鎖定",
        onDismiss = { confirmLock = false },
        onConfirm = { confirmLock = false; w.startWrite(WriteJob(card, aar, true, f.webUrl)) },
    )

    if (pickAvatar) AvatarRepoBrowser(avatars, onDismiss = { pickAvatar = false }, onPick = { index, e ->
        // 卡上寫倉庫網址就好（比 Releases 下載網址短很多），角色由角色 ID 決定
        set { copy(char = e.name, charId = e.charId, avatar = index.url) }
        pickAvatar = false
    })

    WriteDialog(w, onApplyWritten)
}

@Composable
private fun NfcBanner(nfc: NfcAvailability) {
    if (nfc == NfcAvailability.READY) return
    val c = Tc.colors
    val context = LocalContext.current
    TcPanel(Modifier.fillMaxWidth(), borderColor = c.danger) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(c.danger)
            Spacer(Modifier.width(8.dp))
            Text(
                if (nfc == NfcAvailability.UNSUPPORTED) "此裝置不支援 NFC，無法寫卡" else "NFC 尚未開啟",
                color = c.structure, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (nfc == NfcAvailability.DISABLED) {
                TcTextAction("前往開啟", { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }, color = c.action)
            }
        }
    }
}

@Composable
private fun SizePanel(size: Int?, errors: List<String>) {
    val c = Tc.colors
    TcPanel(Modifier.fillMaxWidth(), rail = true, railColor = if (size == null) c.danger else c.structure) {
        if (size == null) {
            Text("尚有欄位未完成", style = MaterialTheme.typography.titleSmall, color = c.danger)
            errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.textMuted) }
        } else {
            Text("DATA · $size BYTES", color = c.textMuted, style = TcLabelStyle)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Presets.tagCapacities.forEach { (n, cap) ->
                    val ok = size <= cap
                    TcTag("$n ${if (ok) "✓" else "✗"}", color = if (ok) c.success else c.danger)
                }
            }
        }
    }
}

@Composable
internal fun Spinner(size: androidx.compose.ui.unit.Dp = 36.dp) {
    CircularProgressIndicator(Modifier.size(size), color = Tc.colors.action, trackColor = Tc.colors.track, strokeWidth = 3.dp)
}
