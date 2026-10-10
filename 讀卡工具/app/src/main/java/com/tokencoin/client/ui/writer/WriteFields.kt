package com.tokencoin.client.ui.writer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tokencoin.client.ui.components.TcIconButton
import com.tokencoin.client.ui.components.TcInput
import com.tokencoin.client.ui.components.TcPanel
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle

/** 表單區塊：細框面板 + 區段標題 */
@Composable
internal fun FormSection(label: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    TcPanel(Modifier.fillMaxWidth(), borderColor = Tc.colors.borderMuted) {
        TcSectionLabel(label)
        if (note != null) Text(note, color = Tc.colors.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** 上方小標籤 + 輸入框 + 下方說明 */
@Composable
internal fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    hint: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Tc.colors
    Column(modifier) {
        Text(label, color = c.textMuted, style = TcLabelStyle, modifier = Modifier.padding(bottom = 4.dp))
        TcInput(
            value, onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            textStyle = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = Tc.mono) else MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = visualTransformation,
            trailing = trailing,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hint != null) Text(hint, color = c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
    }
}

/** 可自由輸入、也可從下拉清單挑選的欄位。options: (值, 顯示文字) */
@Composable
internal fun PresetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    hint: String? = null,
    mono: Boolean = false,
) {
    val c = Tc.colors
    var open by remember { mutableStateOf(false) }
    Box {
        FormField(
            label, value, onValueChange, hint = hint, mono = mono,
            trailing = if (options.isEmpty()) null else {
                { TcIconButton(Icons.Default.ArrowDropDown, "選擇", { open = true }, size = 34.dp) }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.canvas) {
            options.forEach { (v, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = c.structure, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { open = false; onPick(v) },
                )
            }
        }
    }
}
