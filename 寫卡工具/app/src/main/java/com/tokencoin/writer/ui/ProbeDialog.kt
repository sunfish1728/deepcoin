package com.tokencoin.writer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tokencoin.writer.net.ProbeResult
import java.time.Instant
import java.time.ZoneId

/** 顯示檢測結果：選預設模型（單選）與其他模型（多選），並可填入餘額與到期日。 */
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
    val all = result.models
    var default by remember { mutableStateOf(currentModel.takeIf { it in all } ?: all.firstOrNull().orEmpty()) }
    var others by remember {
        mutableStateOf(currentOthers.split(',', '，').map { it.trim() }.filter { it in all }.toSet())
    }
    var filter by remember { mutableStateOf("") }
    val shown = all.filter { filter.isBlank() || it.contains(filter.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("檢測結果") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("餘額", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (result.balances.isEmpty()) {
                    Text(result.balanceError ?: "無資料", style = MaterialTheme.typography.bodySmall)
                }
                result.balances.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${b.amount} ${b.currency}", style = MaterialTheme.typography.bodyLarge)
                            Text(b.label, style = MaterialTheme.typography.bodySmall)
                        }
                        when {
                            b.currency == "USD" && b.amount.toDoubleOrNull() != null ->
                                TextButton(onClick = { onApplyBalance(b.amount) }) { Text("填入") }
                            b.currency != "USD" -> Text("非美元", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                result.expiresAt?.let { exp ->
                    val date = Instant.ofEpochSecond(exp).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("密鑰到期：$date", Modifier.weight(1f))
                        TextButton(onClick = { onApplyExpiry(date) }) { Text("填入") }
                    }
                }

                HorizontalDivider()
                Text(
                    "模型（${all.size}）　◉ 預設　☑ 其他可用",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (all.isEmpty()) {
                    Text(result.modelsError ?: "沒有模型", style = MaterialTheme.typography.bodySmall)
                } else {
                    if (all.size > 8) {
                        OutlinedTextField(
                            filter, { filter = it },
                            label = { Text("搜尋") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it }) { m ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { default = m },
                            ) {
                                RadioButton(selected = default == m, onClick = { default = m })
                                Text(
                                    m,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                )
                                Checkbox(
                                    checked = m in others && m != default,
                                    enabled = m != default,
                                    onCheckedChange = { others = if (it) others + m else others - m },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApplyModels(default, all.filter { it in others && it != default }); onDismiss() },
                enabled = default.isNotEmpty(),
            ) { Text("套用模型") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}
