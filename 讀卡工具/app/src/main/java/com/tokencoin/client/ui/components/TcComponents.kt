package com.tokencoin.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape

/**
 * 線框面板。rail=true 時左側有粗條，rivet 為粗條頂端的鉚點小圓。
 * 粗條以繪製方式實作，不需量測內容高度。
 */
@Composable
fun TcPanel(
    modifier: Modifier = Modifier,
    rail: Boolean = false,
    railColor: Color = Tc.colors.structure,
    borderColor: Color = Tc.colors.structure,
    background: Color = Color.Transparent,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Tc.colors
    val railW = 8.dp
    Column(
        modifier
            .clip(TcShape.panel)
            .background(background)
            .border(1.dp, borderColor, TcShape.panel)
            .drawBehind {
                if (rail) {
                    val w = railW.toPx()
                    drawRect(railColor, size = Size(w, size.height))
                    drawCircle(c.canvas, radius = 2.dp.toPx(), center = Offset(w / 2, 7.dp.toPx()))
                }
            }
            .padding(start = if (rail) railW + contentPadding else contentPadding, end = contentPadding)
            .padding(vertical = contentPadding),
        content = content,
    )
}

enum class TcButtonKind { Primary, Secondary, Ghost }

@Composable
fun TcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: TcButtonKind = TcButtonKind.Secondary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 40.dp,
    trailing: String? = null,
) {
    val c = Tc.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg, border) = when (kind) {
        TcButtonKind.Primary -> Triple(if (pressed) c.data else c.action, c.onAction, null)
        TcButtonKind.Secondary -> Triple(if (pressed) c.track else c.inset.copy(alpha = 0.6f), c.structure, c.borderMuted)
        TcButtonKind.Ghost -> Triple(if (pressed) c.inset else Color.Transparent, c.structure, null)
    }
    Row(
        modifier
            .defaultMinSize(minHeight = height)
            .clip(TcShape.control)
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .then(if (border != null) Modifier.border(1.dp, border, TcShape.control) else Modifier)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) fg else fg.copy(alpha = 0.45f)
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.weight(1f, fill = false).width(10.dp))
            Text(trailing, color = tint.copy(alpha = 0.8f), style = TcLabelStyle)
        }
    }
}

@Composable
fun TcIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = false,
    tint: Color = Tc.colors.structure,
    size: Dp = 38.dp,
) {
    val c = Tc.colors
    Box(
        modifier
            .size(size)
            .clip(TcShape.control)
            .then(if (bordered) Modifier.border(1.dp, c.borderMuted, TcShape.control) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

/** 小型線框標籤 */
@Composable
fun TcTag(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = Tc.colors.textMuted,
    onClick: (() -> Unit)? = null,
) {
    val c = Tc.colors
    Text(
        text,
        modifier
            .clip(TcShape.control)
            .then(
                if (selected) Modifier.background(c.action)
                else Modifier.border(1.dp, color.copy(alpha = 0.6f), TcShape.control),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = if (selected) c.onAction else color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 小型文字動作（複製 / 重新產生 等），比按鈕輕量 */
@Composable
fun TcTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Tc.colors.textMuted) {
    Text(
        text,
        modifier
            .clip(TcShape.control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** 品牌字標 */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Text(
        "鯨幣",
        modifier,
        color = Tc.colors.action,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
    )
}

/** 纖薄頂部工具列，底部一條結構色細線 */
@Composable
fun TcTopBar(
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable RowScope.() -> Unit,
) {
    val c = Tc.colors
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .drawWithContent {
                drawContent()
                drawRect(c.structure, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) navigation() else Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides c.structure) { title() }
        }
        actions()
    }
}

data class TcNavItem(val label: String, val icon: ImageVector)

/** 底部分頁：等分矩形格，選中格為橘色微弧角色塊 */
@Composable
fun TcBottomNav(items: List<TcNavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Tc.colors
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(c.structure, size = Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { i, item ->
            val sel = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(TcShape.control)
                    .then(
                        if (sel) Modifier.background(c.action)
                        else Modifier.border(1.dp, c.borderMuted.copy(alpha = 0.5f), TcShape.control),
                    )
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fg = if (sel) c.onAction else c.textMuted
                Icon(item.icon, null, Modifier.size(20.dp), tint = fg)
                Text(item.label, color = fg, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 區段小標題：短橘條 + 大寫標記字 */
@Composable
fun TcSectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = Tc.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 12.dp).background(c.action))
        Spacer(Modifier.width(6.dp))
        Text(text, color = c.textMuted, style = TcLabelStyle)
    }
}

/** 狀態小圓點 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(7.dp).clip(CircleShape).background(color))
}

/** 細框（給輸入框等使用） */
@Composable
fun tcFieldBorder(focused: Boolean) = BorderStroke(
    if (focused) 1.5.dp else 1.dp,
    if (focused) Tc.colors.structure else Tc.colors.borderMuted,
)

/** 主題色開關 */
@Composable
fun TcSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Tc.colors
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedTrackColor = c.action, checkedThumbColor = c.onAction,
            uncheckedTrackColor = c.track, uncheckedThumbColor = c.textMuted, uncheckedBorderColor = c.borderMuted,
        ),
    )
}

/** 標題 + 說明 + 開關的一列 */
@Composable
fun TcSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Tc.colors
    Row(
        modifier.fillMaxWidth().clip(TcShape.control).clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = c.structure, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = c.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        TcSwitch(checked, onChange)
    }
}

/** 一組互斥選項（以標籤呈現） */
@Composable
fun <T> TcSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) ->
            TcTag(label, selected = v == selected, onClick = { onSelect(v) }, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
