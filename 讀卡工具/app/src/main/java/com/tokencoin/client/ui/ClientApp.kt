package com.tokencoin.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tokencoin.client.ClientState
import com.tokencoin.client.ui.avatar.AvatarHost
import com.tokencoin.client.ui.chat.ChatScreen
import com.tokencoin.client.ui.components.BrandMark
import com.tokencoin.client.ui.components.StatusDot
import com.tokencoin.client.ui.components.TcBottomNav
import com.tokencoin.client.ui.components.TcNavItem
import com.tokencoin.client.ui.components.TcTopBar
import com.tokencoin.client.ui.home.HomeScreen
import com.tokencoin.client.ui.settings.SettingsScreen
import com.tokencoin.client.ui.writer.WriteScreen
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.LocalCanvasHole
import com.tokencoin.client.ui.theme.tcCanvas
import androidx.compose.runtime.CompositionLocalProvider

private val tabs = listOf(
    TcNavItem("主頁", Icons.Default.Home),
    TcNavItem("寫卡", Icons.Default.Edit),
    TcNavItem("設定", Icons.Default.Settings),
)

@Composable
fun ClientApp(state: ClientState, host: AvatarHost) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.avatars) { state.avatars.messages.collect { state.message(it) } }
    val activeAvatar by state.avatars.active.collectAsState()
    LaunchedEffect(state.avatarTouch) { host.interactive(state.avatarTouch) }
    LaunchedEffect(state.cardTapSeq) { if (state.cardTapSeq > 0) host.appear() }
    LaunchedEffect(state.snack) {
        state.snack?.let {
            state.snack = null
            snackbar.showSnackbar(it)
        }
    }

    // 形象舞台的 WebView 在 Compose 底下，整頁畫布要在舞台位置留洞
    CompositionLocalProvider(LocalCanvasHole provides { host.hole }) {
    Box(Modifier.fillMaxSize().tcCanvas(hole = true)) {
        AnimatedContent(
            targetState = state.chatOpen,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))) togetherWith fadeOut(tween(120))
                } else {
                    fadeIn(tween(180)) togetherWith (slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "chat",
        ) { chat ->
            if (chat) {
                ChatScreen(
                    state.chat, onBack = { state.chatOpen = false }, host = host,
                    hasAvatar = activeAvatar != null, showAvatar = state.avatarInChat,
                    onToggleAvatar = { state.toggleAvatarInChat() },
                )
            } else Tabs(state, host)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    }
}

@Composable
private fun Tabs(state: ClientState, host: AvatarHost) {
    val c = Tc.colors
    val conversations by state.chat.conversations.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TcTopBar(
            actions = {
                Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (state.card != null) c.success else c.borderMuted)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.card != null) "CARD READY" else "NO CARD", color = c.textMuted, style = TcLabelStyle)
                }
            },
        ) { BrandMark() }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.tab) {
                0 -> HomeScreen(
                    state.card, conversations.size, onOpenChat = { state.chatOpen = true }, host = host, avatars = state.avatars,
                    balance = state.balance.state, onRefreshBalance = { state.balance.refresh() },
                    camera = state.homeCamera, onCamera = { state.updateHomeCamera(it) },
                )
                1 -> WriteScreen(state.writer, state.nfc, state.card?.card, state.avatars, onApplyWritten = { state.acceptWritten(it) })
                else -> SettingsScreen(state)
            }
        }
        TcBottomNav(tabs, state.tab) { state.tab = it }
    }
}
