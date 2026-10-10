package com.tokencoin.client.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tokencoin.client.chat.ChatSettings
import com.tokencoin.client.chat.Conversation
import com.tokencoin.client.ui.components.TcButton
import com.tokencoin.client.ui.components.TcButtonKind
import com.tokencoin.client.ui.components.TcInput
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import com.tokencoin.client.ui.theme.tcCanvas
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsSheet(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onSave: (title: String, settings: ChatSettings) -> Unit,
) {
    val c = Tc.colors
    val s = conversation.settings
    var title by remember { mutableStateOf(conversation.title) }
    var prompt by remember { mutableStateOf(s.systemPrompt) }
    var customTemp by remember { mutableStateOf(s.temperature != null) }
    var temp by remember { mutableFloatStateOf((s.temperature ?: 1.0).toFloat()) }
    var maxTokens by remember { mutableStateOf(s.maxTokens?.toString().orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
        shape = TcShape.large,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .tcCanvas()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("對話設定", style = MaterialTheme.typography.titleLarge, color = c.structure)

            TcSectionLabel("TITLE")
            TcInput(title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth())

            TcSectionLabel("SYSTEM PROMPT")
            TcInput(
                prompt, { prompt = it },
                placeholder = "例如：你是 DeepSeek娘，說話活潑、愛用顏文字。",
                minLines = 4, maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )

            TcSectionLabel("TEMPERATURE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (customTemp) "自訂：%.1f".format(temp) else "使用供應商預設",
                    color = c.structure, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = customTemp,
                    onCheckedChange = { customTemp = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.action, checkedThumbColor = c.onAction,
                        uncheckedTrackColor = c.track, uncheckedThumbColor = c.textMuted, uncheckedBorderColor = c.borderMuted,
                    ),
                )
            }
            if (customTemp) {
                Slider(
                    value = temp,
                    onValueChange = { temp = (it * 10).roundToInt() / 10f },
                    valueRange = 0f..2f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = c.action, activeTrackColor = c.action, inactiveTrackColor = c.track,
                        activeTickColor = c.onAction.copy(alpha = 0.4f), inactiveTickColor = c.borderMuted,
                    ),
                )
                Row {
                    Text("精確 0.0", color = c.textMuted, style = TcLabelStyle, modifier = Modifier.weight(1f))
                    Text("2.0 發散", color = c.textMuted, style = TcLabelStyle)
                }
            }

            TcSectionLabel("MAX OUTPUT TOKENS")
            TcInput(
                maxTokens, { v -> maxTokens = v.filter { it.isDigit() }.take(7) },
                placeholder = "留空使用預設",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TcButton("取消", onDismiss, modifier = Modifier.weight(1f))
                TcButton(
                    "儲存",
                    {
                        onSave(
                            title.trim(),
                            s.copy(
                                systemPrompt = prompt.trim(),
                                temperature = if (customTemp) temp.toDouble() else null,
                                maxTokens = maxTokens.toIntOrNull()?.takeIf { it > 0 },
                            ),
                        )
                    },
                    kind = TcButtonKind.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun TextEditDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    hint: String? = null,
    singleLine: Boolean = false,
) {
    val c = Tc.colors
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.canvas,
        shape = TcShape.large,
        title = { Text(title, color = c.structure) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TcInput(text, { text = it }, singleLine = singleLine, minLines = if (singleLine) 1 else 3, modifier = Modifier.fillMaxWidth())
                if (hint != null) Text(hint, color = c.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TcButton(confirm, { onConfirm(text.trim()) }, kind = TcButtonKind.Primary, enabled = text.isNotBlank())
        },
        dismissButton = { TcButton("取消", onDismiss, kind = TcButtonKind.Ghost) },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = Tc.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.canvas,
        shape = TcShape.large,
        title = { Text(title, color = c.structure) },
        text = { Text(text, color = c.textMuted) },
        confirmButton = { TcButton(confirm, onConfirm, kind = TcButtonKind.Primary) },
        dismissButton = { TcButton("取消", onDismiss, kind = TcButtonKind.Ghost) },
    )
}
