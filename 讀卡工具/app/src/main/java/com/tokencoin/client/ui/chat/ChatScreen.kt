package com.tokencoin.client.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokencoin.client.chat.ChatEngine
import com.tokencoin.client.chat.ChatMessage
import com.tokencoin.client.chat.ConversationSummary
import com.tokencoin.client.chat.Role
import com.tokencoin.client.ui.avatar.AvatarHost
import com.tokencoin.client.ui.avatar.AvatarStage
import com.tokencoin.client.ui.components.TcIconButton
import com.tokencoin.client.ui.components.TcInput
import com.tokencoin.client.ui.components.TcTopBar
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import com.tokencoin.client.ui.theme.tcCanvas
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    engine: ChatEngine,
    onBack: () -> Unit,
    host: AvatarHost,
    hasAvatar: Boolean,
    showAvatar: Boolean,
    onToggleAvatar: () -> Unit,
) {
    val conversation by engine.current.collectAsState()
    val conversations by engine.conversations.collectAsState()
    val generating by engine.generating.collectAsState()
    val provider by engine.provider.collectAsState()

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleting by remember { mutableStateOf<ConversationSummary?>(null) }

    LaunchedEffect(engine) { engine.errors.collect { snackbar.showSnackbar(it) } }
    // 第一次進入時：沒有開啟中的對話就開最近一段，沒有歷史就開新對話
    LaunchedEffect(Unit) {
        if (engine.current.value == null) {
            engine.conversations.value.firstOrNull()?.let { engine.open(it.id) } ?: engine.newConversation()
        }
    }
    BackHandler { if (drawer.isOpen) scope.launch { drawer.close() } else onBack() }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = Tc.colors.structure.copy(alpha = 0.25f),
        drawerContent = {
            ConversationDrawer(
                conversations = conversations,
                currentId = conversation?.id,
                onSelect = { engine.open(it); scope.launch { drawer.close() } },
                onNew = { engine.newConversation(); scope.launch { drawer.close() } },
                onRename = { renaming = it },
                onDelete = { deleting = it },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().tcCanvas(hole = true)) {
            Column(Modifier.fillMaxSize().imePadding()) {
                ChatTopBar(
                    title = conversation?.title ?: "新對話",
                    model = conversation?.model ?: provider?.defaultModel.orEmpty(),
                    models = provider?.models.orEmpty(),
                    onBack = onBack,
                    onHistory = { scope.launch { drawer.open() } },
                    onNew = { engine.newConversation() },
                    onSettings = { showSettings = true },
                    onModel = { engine.setModel(it) },
                    avatarShown = if (hasAvatar) showAvatar else null,
                    onToggleAvatar = onToggleAvatar,
                )
                // 形象面板：半身鏡頭；鍵盤打開時收起，留空間給訊息
                val divider = Tc.colors.borderMuted
                AnimatedVisibility(hasAvatar && showAvatar && !WindowInsets.isImeVisible) {
                    AvatarStage(
                        host, "bust", rotatable = false, corners = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .drawWithContent {
                                drawContent()
                                drawRect(divider, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
                            },
                    )
                }
                val messages = conversation?.messages.orEmpty()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        EmptyState(
                            name = provider?.characterName?.takeIf { it.isNotBlank() } ?: conversation?.model ?: provider?.defaultModel,
                            subtitle = provider?.let { "${it.providerName} · ${conversation?.model ?: it.defaultModel}" },
                            hasProvider = provider != null,
                            onSuggestion = { engine.send(it) },
                        )
                    } else key(conversation?.id) {
                        // 換對話時重建清單狀態，捲動位置從新對話的底部開始
                        MessageList(
                            messages = messages,
                            generating = generating,
                            onEdit = { editing = it },
                            onDeleteMessage = { engine.deleteMessage(it.id) },
                            onRegenerate = { engine.regenerate(it.id) },
                        )
                    }
                }
                InputBar(
                    enabled = provider != null,
                    generating = generating,
                    onSend = { engine.send(it) },
                    onStop = { engine.stop() },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
        }
    }

    if (showSettings) conversation?.let { conv ->
        ChatSettingsSheet(
            conversation = conv,
            onDismiss = { showSettings = false },
            onSave = { title, settings ->
                engine.updateSettings(settings)
                if (title.isNotBlank() && title != conv.title) engine.rename(conv.id, title)
                showSettings = false
            },
        )
    }
    editing?.let { m ->
        TextEditDialog(
            title = "編輯訊息",
            initial = m.content,
            confirm = "重新送出",
            hint = "之後的訊息會被刪除並重新產生回覆",
            onDismiss = { editing = null },
            onConfirm = { engine.editAndResend(m.id, it); editing = null },
        )
    }
    renaming?.let { s ->
        TextEditDialog(
            title = "重新命名",
            initial = s.title,
            confirm = "儲存",
            singleLine = true,
            onDismiss = { renaming = null },
            onConfirm = { engine.rename(s.id, it); renaming = null },
        )
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = "刪除對話？",
            text = "「${s.title}」的所有訊息都會被刪除，無法復原。",
            confirm = "刪除",
            onDismiss = { deleting = null },
            onConfirm = { engine.delete(s.id); deleting = null },
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    model: String,
    models: List<String>,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onModel: (String) -> Unit,
    avatarShown: Boolean?,
    onToggleAvatar: () -> Unit,
) {
    val c = Tc.colors
    var pick by remember { mutableStateOf(false) }
    TcTopBar(
        navigation = { TcIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack) },
        actions = {
            if (avatarShown != null) {
                TcIconButton(Icons.Default.Face, if (avatarShown) "隱藏形象" else "顯示形象", onToggleAvatar,
                    tint = if (avatarShown) c.action else c.structure)
            }
            TcIconButton(Icons.AutoMirrored.Filled.List, "對話紀錄", onHistory)
            TcIconButton(Icons.Default.Add, "新對話", onNew)
            TcIconButton(Icons.Default.Settings, "對話設定", onSettings)
        },
    ) {
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.structure, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box {
                Text(
                    "${model.ifBlank { "未選擇模型" }} ▾",
                    color = c.action,
                    style = TcLabelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(TcShape.control)
                        .clickable(enabled = models.isNotEmpty()) { pick = true }
                        .padding(vertical = 2.dp),
                )
                DropdownMenu(expanded = pick, onDismissRequest = { pick = false }) {
                    (models + listOfNotNull(model.takeIf { it.isNotBlank() && it !in models })).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, color = if (m == model) c.action else c.structure) },
                            onClick = { pick = false; onModel(m) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    generating: Boolean,
    onEdit: (ChatMessage) -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit,
    onRegenerate: (ChatMessage) -> Unit,
) {
    val c = Tc.colors
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (messages.size - 1).coerceAtLeast(0))
    var follow by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // 使用者往上捲時停止跟隨；回到底部時恢復
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, canForward) ->
            if (!canForward) follow = true else if (scrolling) follow = false
        }
    }
    val last = messages.lastOrNull()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) { follow = true; list.animateScrollToItem(messages.lastIndex, Int.MAX_VALUE / 4) }
    }
    LaunchedEffect(last?.content?.length, last?.reasoning?.length, last?.status) {
        if (follow && messages.isNotEmpty()) list.scrollToItem(messages.lastIndex, Int.MAX_VALUE / 4)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(messages, key = { it.id }) { m ->
                if (m.role == Role.USER) {
                    UserMessage(m, enabled = !generating, onEdit = { onEdit(m) }, onDelete = { onDeleteMessage(m) })
                } else {
                    AssistantMessage(
                        m,
                        actionsEnabled = !generating,
                        onRegenerate = { onRegenerate(m) },
                        onDelete = { onDeleteMessage(m) },
                    )
                }
            }
        }
        if (!follow) {
            Text(
                "↓ 最新",
                color = c.onAction,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .clip(TcShape.control)
                    .background(c.action)
                    .clickable {
                        follow = true
                        scope.launch { list.animateScrollToItem(messages.lastIndex, Int.MAX_VALUE / 4) }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(name: String?, subtitle: String?, hasProvider: Boolean, onSuggestion: (String) -> Unit) {
    val c = Tc.colors
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("鯨幣 · CHAT", color = c.textMuted, style = TcLabelStyle)
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasProvider) "和 ${name ?: "AI"} 聊天" else "尚未刷入卡片",
            style = MaterialTheme.typography.titleLarge,
            color = c.structure,
            textAlign = TextAlign.Center,
        )
        Text(
            if (hasProvider) subtitle.orEmpty() else "刷一下鯨幣才能送出訊息，目前只能瀏覽對話紀錄",
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (hasProvider) {
            Spacer(Modifier.height(22.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                listOf("介紹一下你自己", "幫我寫一首短詩", "用三句話解釋量子糾纏", "今天晚餐吃什麼？").forEach {
                    Text(
                        it,
                        color = c.structure,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(TcShape.control)
                            .border(1.dp, c.borderMuted, TcShape.control)
                            .clickable { onSuggestion(it) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(enabled: Boolean, generating: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    val c = Tc.colors
    var text by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(c.structure, size = Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TcInput(
            value = text,
            onValueChange = { text = it },
            placeholder = if (enabled) "輸入訊息…" else "請先刷入鯨幣",
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (generating) {
            // 停止：橘框 + 實心方塊
            Box(
                Modifier
                    .size(44.dp)
                    .clip(TcShape.control)
                    .border(1.5.dp, c.action, TcShape.control)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).clip(TcShape.control).background(c.action))
            }
        } else {
            val canSend = enabled && text.isNotBlank()
            Box(
                Modifier
                    .size(44.dp)
                    .clip(TcShape.control)
                    .background(if (canSend) c.action else c.track)
                    .clickable(enabled = canSend) { onSend(text.trim()); text = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "送出", tint = if (canSend) c.onAction else c.textMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
