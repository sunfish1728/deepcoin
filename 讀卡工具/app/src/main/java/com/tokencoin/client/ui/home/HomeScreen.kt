package com.tokencoin.client.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokencoin.client.avatar.AvatarRepository
import com.tokencoin.client.card.DecodedCard
import com.tokencoin.client.net.BalanceState
import com.tokencoin.client.ui.avatar.AvatarHost
import com.tokencoin.client.ui.avatar.AvatarRepoBrowser
import com.tokencoin.client.ui.avatar.AvatarSheet
import com.tokencoin.client.ui.avatar.AvatarStage
import com.tokencoin.client.ui.avatar.StageStatus
import com.tokencoin.client.ui.components.StatusDot
import com.tokencoin.client.ui.components.TcButton
import com.tokencoin.client.ui.components.TcButtonKind
import com.tokencoin.client.ui.components.TcIconButton
import com.tokencoin.client.ui.components.TcPanel
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.components.TcTag
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 主頁：上方大片空間是虛擬形象舞台，下方是卡片資訊與聊天入口。
 */
@Composable
fun HomeScreen(
    card: DecodedCard?,
    conversationCount: Int,
    onOpenChat: () -> Unit,
    host: AvatarHost,
    avatars: AvatarRepository,
    balance: BalanceState,
    onRefreshBalance: () -> Unit,
    camera: String,
    onCamera: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        HomeStage(card, host, avatars, camera, onCamera, Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp))
        CardPanel(card, balance, onRefreshBalance)
        Spacer(Modifier.height(12.dp))
        TcButton(
            text = "聊天",
            onClick = onOpenChat,
            kind = TcButtonKind.Primary,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            enabled = card != null || conversationCount > 0,
            height = 54.dp,
            trailing = if (conversationCount > 0) "$conversationCount 段對話" else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
    }
}

internal val cameraPresets = listOf("full" to "全身", "bust" to "半身", "face" to "特寫")

/** 形象舞台：模型 + 鏡頭切換 + 形象清單；沒有形象時顯示匯入入口 */
@Composable
private fun HomeStage(
    card: DecodedCard?,
    host: AvatarHost,
    avatars: AvatarRepository,
    preset: String,
    onPreset: (String) -> Unit,
    modifier: Modifier,
) {
    val c = Tc.colors
    val list by avatars.avatars.collectAsState()
    val active by avatars.active.collectAsState()
    val task by avatars.task.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    var browse by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { avatars.import(it) }
    }

    AvatarStage(host, preset, rotatable = true, modifier = modifier) {
        if (active == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("STAGE · VRM", color = c.textMuted.copy(alpha = 0.55f), style = TcLabelStyle)
                card?.card?.char?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = c.textMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                if (task != null) {
                    Text(
                        task!!.label + (task!!.progress?.let { " ${(it * 100).toInt()}%" } ?: "…"),
                        color = c.textMuted, style = TcLabelStyle,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TcButton("匯入形象", { picker.launch(arrayOf("*/*")) }, icon = Icons.Default.Add)
                        TcButton("線上形象庫", { browse = true })
                    }
                }
            }
        } else {
            // 右上：鏡頭與形象清單
            Column(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalAlignment = Alignment.End) {
                TcIconButton(Icons.Default.Person, "形象", { sheet = true }, bordered = true, size = 34.dp)
                Spacer(Modifier.height(6.dp))
                cameraPresets.forEach { (key, label) ->
                    TcTag(label, selected = preset == key, onClick = { onPreset(key) }, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            // 左下：名字
            val name = (host.status as? StageStatus.Ready)?.name?.takeIf { it.isNotBlank() } ?: active!!.name
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (host.status is StageStatus.Ready) c.success else c.borderMuted)
                Spacer(Modifier.width(6.dp))
                Text(name, color = c.textMuted, style = TcLabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (sheet) {
        AvatarSheet(
            avatars = list,
            activeId = active?.id,
            task = task,
            rootDir = avatars.rootDir,
            cardCharId = card?.card?.charId.orEmpty(),
            onSelect = { avatars.select(it) },
            onDelete = { avatars.delete(it) },
            onImport = { picker.launch(arrayOf("*/*")) },
            onBrowse = { sheet = false; browse = true },
            onDismiss = { sheet = false },
        )
    }
    if (browse) AvatarRepoBrowser(avatars, onDismiss = { browse = false })
}

private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardPanel(card: DecodedCard?, balance: BalanceState, onRefreshBalance: () -> Unit) {
    val c = Tc.colors
    TcPanel(rail = true, modifier = Modifier.fillMaxWidth()) {
        if (card == null) {
            TcSectionLabel("NO CARD")
            Spacer(Modifier.height(6.dp))
            Text("尚未刷入卡片", style = MaterialTheme.typography.titleMedium, color = c.structure)
            Text(
                "將鯨幣靠近手機背面的 NFC 感應區",
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
            )
            return@TcPanel
        }
        val t = card.card
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(c.success)
                    Spacer(Modifier.width(6.dp))
                    Text("CARD · ${t.id}", color = c.textMuted, style = TcLabelStyle)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    t.title.ifBlank { t.provider },
                    style = MaterialTheme.typography.titleLarge,
                    color = c.structure,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${t.provider} · ${t.model}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            BalanceBlock(t.balanceUsd, balance, onRefreshBalance)
        }
        val tags = buildList {
            t.exp?.let {
                val expired = it < Instant.now().epochSecond
                add((if (expired) "已過期 " else "至 ") + dateFmt.format(Instant.ofEpochSecond(it)))
            }
            balance.snapshot?.expiresAt?.takeIf { it != t.exp }?.let { add("密鑰至 " + dateFmt.format(Instant.ofEpochSecond(it))) }
            add(t.proto)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { TcTag(it) }
        }
    }
}

/**
 * 右側餘額：優先顯示即時查詢結果（優先美元），查不到時退回卡片上記錄的美元餘額。
 * 點一下重新查詢。
 */
@Composable
private fun BalanceBlock(cardUsd: String, b: BalanceState, onRefresh: () -> Unit) {
    val c = Tc.colors
    val snap = b.snapshot
    val primary = snap?.balances?.let { list -> list.firstOrNull { it.currency == "USD" } ?: list.firstOrNull() }
    val cardAmount = if (cardUsd.isNotBlank()) "$" + cardUsd else null
    val amount: String
    val sub: String
    when {
        primary != null -> {
            amount = money(primary.currency, primary.amount)
            sub = if (b.error != null) "查詢失敗 · 顯示上次" else "即時 · " + ago(snap!!.fetchedAt)
        }
        b.loading -> { amount = cardAmount ?: "…"; sub = "查詢中" }
        cardAmount != null -> { amount = cardAmount; sub = if (b.unsupported) "卡片記錄 · 不支援查詢" else "卡片記錄" }
        else -> { amount = "—"; sub = if (b.unsupported) "不支援查詢" else if (b.error != null) "查詢失敗" else "未查詢" }
    }
    Column(
        Modifier.clip(TcShape.control).clickable(enabled = !b.loading && !b.unsupported, onClick = onRefresh).padding(4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BALANCE", color = c.textMuted, style = TcLabelStyle)
            if (!b.unsupported) {
                Spacer(Modifier.width(4.dp))
                if (b.loading) CircularProgressIndicator(Modifier.size(10.dp), color = c.action, strokeWidth = 1.5.dp)
                else Icon(Icons.Default.Refresh, "重新查詢餘額", Modifier.size(12.dp), tint = c.textMuted)
            }
        }
        Text(
            amount,
            color = c.data,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Tc.mono, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        snap?.balances?.filter { it !== primary }?.take(2)?.forEach {
            Text(money(it.currency, it.amount), color = c.textMuted, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Tc.mono))
        }
        Text(
            sub,
            color = if (b.error != null && primary == null && !b.unsupported) c.danger else c.textMuted,
            style = TcLabelStyle,
            maxLines = 1,
        )
    }
}

private fun money(currency: String, amount: String) = when (currency) {
    "USD" -> "$" + amount
    "CNY" -> "¥" + amount
    else -> "$amount $currency"
}

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "剛剛"
        m < 60 -> "$m 分鐘前"
        m < 24 * 60 -> "${m / 60} 小時前"
        else -> dateFmt.format(Instant.ofEpochMilli(t))
    }
}
