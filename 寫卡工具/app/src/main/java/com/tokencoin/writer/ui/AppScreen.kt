package com.tokencoin.writer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tokencoin.writer.NfcAvailability

private data class TabDef(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabDef("寫卡", Icons.Default.Edit),
    TabDef("讀卡", Icons.Default.Search),
    TabDef("設定檔", Icons.AutoMirrored.Filled.List),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(state: AppState, nfc: NfcAvailability, openNfcSettings: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Token幣 寫卡工具") }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = state.tab == i,
                        onClick = { state.tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NfcBanner(nfc, openNfcSettings)
            when (state.tab) {
                0 -> WriteScreen(state)
                1 -> ReadScreen(state)
                else -> ProfilesScreen(state)
            }
        }
    }

    WriteDialog(state)
}

@Composable
private fun NfcBanner(nfc: NfcAvailability, openNfcSettings: () -> Unit) {
    if (nfc == NfcAvailability.READY) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                if (nfc == NfcAvailability.UNSUPPORTED) "此裝置不支援 NFC" else "NFC 尚未開啟",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (nfc == NfcAvailability.DISABLED) TextButton(onClick = openNfcSettings) { Text("前往開啟") }
        }
    }
}

@Composable
private fun WriteDialog(state: AppState) {
    when (val s = state.writeStatus) {
        null -> Unit
        WriteStatus.Waiting -> AlertDialog(
            onDismissRequest = {},
            title = { Text("請將卡片靠近手機") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(48.dp))
                    Text("把 NFC 卡貼在手機背面 NFC 感應區，寫入完成前請勿移開。")
                    if (state.pendingWrite?.lock == true) {
                        Text("⚠ 寫入後將永久鎖定", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { state.cancelWrite() }) { Text("取消") } },
        )
        is WriteStatus.Success -> AlertDialog(
            onDismissRequest = { state.writeStatus = null },
            title = { Text("寫入成功") },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = {
                    state.writeStatus = null
                    // 下一張卡換新 ID
                    state.updateForm(state.form.copy(id = newCardId()))
                }) { Text("完成（下一張換新 ID）") }
            },
            dismissButton = { TextButton(onClick = { state.writeStatus = null }) { Text("關閉") } },
        )
        is WriteStatus.Failed -> AlertDialog(
            onDismissRequest = { state.writeStatus = null },
            title = { Text("寫入失敗") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { state.writeStatus = null }) { Text("確定") } },
        )
    }
}
