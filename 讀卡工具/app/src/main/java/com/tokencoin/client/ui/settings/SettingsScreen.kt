package com.tokencoin.client.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tokencoin.client.ClientState
import com.tokencoin.client.ThemeMode
import com.tokencoin.client.card.DecodedCard
import com.tokencoin.client.card.Presets
import com.tokencoin.client.card.ShaStatus
import com.tokencoin.client.card.TokenCard
import com.tokencoin.client.ui.avatar.AvatarRepoBrowser
import com.tokencoin.client.ui.avatar.AvatarSheet
import com.tokencoin.client.ui.chat.ConfirmDialog
import com.tokencoin.client.ui.components.StatusDot
import com.tokencoin.client.ui.components.TcButton
import com.tokencoin.client.ui.components.TcPanel
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.components.TcSegmented
import com.tokencoin.client.ui.components.TcSwitchRow
import com.tokencoin.client.ui.components.TcTextAction
import com.tokencoin.client.ui.home.cameraPresets
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 設定：卡片詳細資訊、餘額、外觀、形象、聊天、關於。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: ClientState) {
    val c = Tc.colors
    val conversations by state.chat.conversations.collectAsState()
    val avatarList by state.avatars.avatars.collectAsState()
    val activeAvatar by state.avatars.active.collectAsState()
    val avatarTask by state.avatars.task.collectAsState()
    val card = state.card
    var confirmForget by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }
    var avatarSheet by remember { mutableStateOf(false) }
    var browse by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { state.avatars.import(it) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- 卡片 ----
        Group("CARD · 卡片資訊") {
            if (card == null) {
                Text("尚未刷入卡片", color = c.structure, style = MaterialTheme.typography.titleMedium)
                Text("刷卡後這裡會顯示卡上的完整內容。", color = c.textMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                CardDetails(card, state.detectedModels, onCopied = { state.message(it) })
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TcButton("載入到寫卡", { state.writer.loadFromCard(card.card); state.tab = 1 })
                    TcButton("移除本機卡片", { confirmForget = true })
                }
            }
        }

        // ---- 餘額 ----
        Group("BALANCE · 餘額") {
            TcSwitchRow(
                "自動查詢餘額",
                "換卡、回到 App 時（距上次 5 分鐘以上）向供應商查詢。支援 DeepSeek、OpenRouter、SiliconFlow、Moonshot 與 new-api / one-api 中轉站",
                state.autoBalance, { state.updateAutoBalance(it) },
            )
            val b = state.balance.state
            if (card != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            b.loading -> "查詢中…"
                            b.error != null -> b.error
                            b.snapshot != null -> "上次查詢：" + timeFmt.format(Instant.ofEpochMilli(b.snapshot.fetchedAt))
                            else -> "尚未查詢"
                        },
                        color = if (b.error != null) c.danger else c.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (!b.loading) TcTextAction("立即查詢", { state.balance.refresh() }, color = c.action)
                }
            }
        }

        // ---- 外觀 ----
        Group("APPEARANCE · 外觀") {
            Label("主題")
            TcSegmented(ThemeMode.entries.map { it to it.label }, state.themeMode, { state.updateThemeMode(it) })
        }

        // ---- 形象 ----
        Group("AVATAR · 虛擬形象") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(activeAvatar?.name ?: "沒有形象", color = c.structure, style = MaterialTheme.typography.bodyLarge)
                    Text("已安裝 ${avatarList.size} 個", color = c.textMuted, style = MaterialTheme.typography.bodySmall)
                }
                TcButton("管理形象", { avatarSheet = true })
            }
            Label("主頁預設鏡頭")
            TcSegmented(cameraPresets, state.homeCamera, { state.updateHomeCamera(it) })
            TcSwitchRow("觸控互動", "點擊反應、摸頭、拖曳轉身、雙指縮放", state.avatarTouch, { state.updateAvatarTouch(it) })
            TcSwitchRow("聊天時顯示形象面板", "也可以在聊天畫面右上角切換", state.avatarInChat, { state.toggleAvatarInChat() })
        }

        // ---- 聊天 ----
        Group("CHAT · 聊天") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("對話紀錄 ${conversations.size} 段", color = c.structure, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TcButton("全部清除", { confirmClearChat = true }, enabled = conversations.isNotEmpty())
            }
            Text("對話只存在本機，不包含卡片密鑰。", color = c.textMuted, style = MaterialTheme.typography.bodySmall)
        }

        // ---- 關於 ----
        Group("ABOUT · 關於") {
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            InfoRow("版本", "鯨幣 $version")
            InfoRow("卡片格式", "v${TokenCard.VERSION} · ${TokenCard.MIME}", mono = true)
            InfoRow("角色包格式", "v1（VRM 0.x / 1.0）")
            InfoRow("開源元件", "three.js、three-vrm（MIT）· OkHttp（Apache-2.0）")
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmForget) ConfirmDialog(
        title = "移除本機卡片？",
        text = "只會清除手機上保存的卡片資料，實體卡和對話紀錄不受影響。之後刷卡即可重新載入。",
        confirm = "移除",
        onDismiss = { confirmForget = false },
        onConfirm = { confirmForget = false; state.forgetCard() },
    )
    if (confirmClearChat) ConfirmDialog(
        title = "清除所有對話？",
        text = "${conversations.size} 段對話會從本機刪除，無法復原。",
        confirm = "全部清除",
        onDismiss = { confirmClearChat = false },
        onConfirm = {
            confirmClearChat = false
            conversations.forEach { state.chat.delete(it.id) }
            state.message("已清除所有對話")
        },
    )
    if (avatarSheet) AvatarSheet(
        avatars = avatarList,
        activeId = activeAvatar?.id,
        task = avatarTask,
        rootDir = state.avatars.rootDir,
        cardCharId = card?.card?.charId.orEmpty(),
        onSelect = { state.avatars.select(it) },
        onDelete = { state.avatars.delete(it) },
        onImport = { picker.launch(arrayOf("*/*")) },
        onBrowse = { avatarSheet = false; browse = true },
        onDismiss = { avatarSheet = false },
    )
    if (browse) AvatarRepoBrowser(state.avatars, onDismiss = { browse = false })
}

private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

/** 卡上的每個欄位；密鑰預設遮住 */
@Composable
private fun CardDetails(d: DecodedCard, detected: List<String>, onCopied: (String) -> Unit) {
    val c = Tc.colors
    val t = d.card
    val clipboard = LocalClipboardManager.current
    var showKey by remember { mutableStateOf(false) }
    fun copy(text: String, what: String) {
        clipboard.setText(AnnotatedString(text))
        onCopied("已複製$what")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (d.shaStatus == ShaStatus.OK) c.success else c.danger)
        Spacer(Modifier.width(6.dp))
        Text(
            when (d.shaStatus) {
                ShaStatus.OK -> "SHA-256 校驗通過"
                ShaStatus.MISMATCH -> "SHA-256 不符"
                ShaStatus.MISSING -> "沒有 SHA-256"
            },
            color = c.textMuted, style = TcLabelStyle, modifier = Modifier.weight(1f),
        )
        TcTextAction("複製 JSON", { copy(d.canonicalJson, " JSON（含密鑰）") })
    }
    Text(t.title.ifBlank { t.provider }, color = c.structure, style = MaterialTheme.typography.titleLarge)

    SubHead("卡片")
    InfoRow("卡片 ID", t.id, mono = true)
    InfoRow("發行時間", timeFmt.format(Instant.ofEpochSecond(t.iat)))
    InfoRow(
        "到期日",
        t.exp?.let { (if (it < Instant.now().epochSecond) "已過期 " else "") + dateFmt.format(Instant.ofEpochSecond(it)) } ?: "不限",
    )
    InfoRow("卡片記錄餘額", t.balanceUsd.takeIf { it.isNotBlank() }?.let { "$$it" } ?: "—", mono = true)
    InfoRow("SHA-256", d.computedSha.take(16) + "…", mono = true)

    SubHead("模型")
    InfoRow("模型供應商", Presets.vendors.firstOrNull { it.id == t.vendor }?.label?.let { "$it（${t.vendor}）" } ?: t.vendor)
    InfoRow("預設模型", t.model, mono = true)
    InfoRow(
        "其他模型",
        if (Presets.ALL_MODELS in t.models) "全部（*，自動檢測" + (if (detected.isEmpty()) "中）" else "到 ${detected.size} 個）")
        else t.models.joinToString("\n").ifBlank { "—" },
        mono = Presets.ALL_MODELS !in t.models,
    )

    SubHead("API")
    InfoRow("API 供應商", t.provider)
    InfoRow("協議", t.proto, mono = true)
    InfoRow("地址", t.base, mono = true, onCopy = { copy(t.base, "地址") })
    InfoRow(
        "密鑰", if (showKey) t.key else maskKey(t.key), mono = true,
        trailing = { TcTextAction(if (showKey) "隱藏" else "顯示", { showKey = !showKey }) },
        onCopy = { copy(t.key, "密鑰") },
    )

    SubHead("其他")
    InfoRow("電腦端套用", t.apps.map { a -> Presets.apps.firstOrNull { it.first == a }?.second ?: a }.joinToString("、").ifBlank { "—" })
    InfoRow("角色", t.char.ifBlank { "—" })
    InfoRow("角色 ID", t.charId.ifBlank { "—" }, mono = true)
    InfoRow("形象 URL", t.avatar.ifBlank { "—" }, mono = true)
    InfoRow("備註", t.note.ifBlank { "—" })
    if (t.extra.isNotEmpty()) InfoRow("擴充欄位", t.extra.keys.joinToString(", "), mono = true)
}

private fun maskKey(k: String) = if (k.length <= 10) "•".repeat(k.length) else k.take(6) + "••••••" + k.takeLast(4)

@Composable
private fun Group(label: String, content: @Composable ColumnScope.() -> Unit) {
    TcPanel(Modifier.fillMaxWidth(), borderColor = Tc.colors.borderMuted) {
        TcSectionLabel(label)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Tc.colors.textMuted, style = TcLabelStyle)
}

@Composable
private fun SubHead(text: String) {
    val c = Tc.colors
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = c.action, style = TcLabelStyle)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(Modifier.weight(1f), color = c.borderMuted.copy(alpha = 0.5f))
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
) {
    val c = Tc.colors
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp).padding(top = 2.dp))
        Text(
            value,
            color = c.structure,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = Tc.mono) else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(top = 1.dp),
        )
        trailing?.invoke()
        if (onCopy != null) TcTextAction("複製", onCopy)
    }
}

