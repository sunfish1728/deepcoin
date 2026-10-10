package com.tokencoin.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcShape

/** 微弧角細框輸入框，內層底色略深，不使用浮動標籤。 */
@Composable
fun TcInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 6,
    minLines: Int = 1,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Tc.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border = tcFieldBorder(focused)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interaction,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = textStyle.copy(color = if (enabled) c.structure else c.textMuted),
        cursorBrush = SolidColor(c.action),
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(TcShape.control)
            .background(c.inset)
            .border(border, TcShape.control),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).padding(start = 12.dp, end = if (trailing != null) 4.dp else 12.dp, top = 10.dp, bottom = 10.dp),
                    contentAlignment = if (minLines > 1) Alignment.TopStart else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = textStyle, color = c.textMuted.copy(alpha = 0.75f))
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}
