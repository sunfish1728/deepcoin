package com.tokencoin.client

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tokencoin.client.ui.ClientApp
import com.tokencoin.client.avatar.KeywordEmotionAnalyzer
import com.tokencoin.client.avatar.createAvatarRepository
import com.tokencoin.client.ui.avatar.AvatarChatLink
import com.tokencoin.client.ui.avatar.AvatarHost
import com.tokencoin.client.ui.theme.DarkTc
import com.tokencoin.client.ui.theme.LightTc
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.grainBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import com.tokencoin.client.chat.createChatEngine
import com.tokencoin.client.ui.theme.TcTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.tokencoin.client.card.ShaStatus
import com.tokencoin.client.nfc.NfcIo
import com.tokencoin.client.net.BalanceTracker
import com.tokencoin.client.net.ModelCatalog
import com.tokencoin.client.writer.WriteStatus
import com.tokencoin.client.writer.WriterState

/**
 * 卡片來源有兩種：
 * 1. App 未在前景：系統依 NDEF_DISCOVERED / AAR 開啟本 App，從 intent 取得卡片。
 * 2. App 在前景：reader mode 直接讀卡，不經系統分派。
 */
class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfc: NfcAdapter? = null
    private lateinit var state: ClientState
    private lateinit var avatarHost: AvatarHost
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfc = NfcAdapter.getDefaultAdapter(this)
        state = ClientState(
            applicationContext,
            createChatEngine(applicationContext, engineScope),
            createAvatarRepository(applicationContext, engineScope),
            BalanceTracker(applicationContext, engineScope),
            WriterState(applicationContext),
            ModelCatalog(applicationContext, engineScope),
        )
        // WebView 用 Activity context；configChanges 涵蓋旋轉與深淺色，Activity 不會重建
        avatarHost = AvatarHost(this, state.avatars, engineScope)
        AvatarChatLink(state.chat, avatarHost, KeywordEmotionAnalyzer, engineScope)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val dark = when (state.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            // 主題切換時同步狀態列 / 導覽列圖示深淺（uiMode 由 configChanges 處理，不會重建 Activity）
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            TcTheme(dark) {
                val shadow = Tc.colors.structure
                val canvas = Tc.colors.canvas
                LaunchedEffect(dark) {
                    avatarHost.theme(shadow.toArgb(), if (dark) 0.35f else 0.22f)
                    // 形象舞台透出的是原生內容層的背景，和 Compose 畫布用同一張顆粒圖對齊
                    findViewById<android.view.View>(android.R.id.content).background = android.graphics.drawable.LayerDrawable(arrayOf(
                        android.graphics.drawable.ColorDrawable(canvas.toArgb()),
                        android.graphics.drawable.BitmapDrawable(resources, grainBitmap(if (dark) DarkTc else LightTc)).apply {
                            setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
                        },
                    ))
                }
                ClientApp(state, avatarHost)
            }
        }
        // 要在 setContent 之後：setContent 會清空內容層
        avatarHost.install(findViewById(android.R.id.content))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        avatarHost.destroy()
        engineScope.cancel()
    }

    override fun onResume() {
        super.onResume()
        avatarHost.onActivityResume()
        if (state.autoBalance) state.balance.refreshIfStale()
        val adapter = nfc
        state.nfc = when {
            adapter == null -> NfcAvailability.UNSUPPORTED
            !adapter.isEnabled -> NfcAvailability.DISABLED
            else -> NfcAvailability.READY
        }
        adapter?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) },
        )
    }

    override fun onPause() {
        super.onPause()
        avatarHost.onActivityPause()
        nfc?.disableReaderMode(this)
    }

    /** 在 NFC binder 執行緒上執行。寫卡分頁有待寫任務就寫卡，否則讀卡。 */
    override fun onTagDiscovered(tag: Tag) {
        val job = state.writer.pendingWrite
        if (job != null) {
            val status = try {
                val r = NfcIo.write(tag, NfcIo.buildMessage(job.card, job.aarPackage), job.lock)
                val cap = r.info.maxSize?.let { " / $it" } ?: ""
                WriteStatus.Success(
                    "已寫入 ${r.bytes}$cap bytes\nUID ${r.info.uid}" +
                        (if (r.locked) "\n卡片已永久鎖定為唯讀" else "") + "\n回讀驗證通過 ✓",
                    job.card,
                )
            } catch (e: Exception) {
                WriteStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread { state.writer.finishWrite(status) }
            return
        }
        val r = runCatching { NfcIo.read(tag) }.getOrNull()
        runOnUiThread {
            when {
                r == null -> state.message("讀卡失敗，請再刷一次")
                r.decoded != null -> state.acceptCard(r.decoded!!)
                else -> state.message(r.error ?: "不是鯨幣卡片")
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        // debug 版測試用：模擬器沒有 NFC，用 `adb shell am start -n com.tokencoin.client/.MainActivity --es debug_card '<卡片 JSON>'` 模擬刷卡
        val debugCard = intent?.getStringExtra("debug_card")
        if (debugCard != null && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            runCatching { com.tokencoin.client.card.TokenCardCodec.decode(debugCard.toByteArray(Charsets.UTF_8)) }
                .onSuccess { state.acceptCard(it) }
                .onFailure { state.message("debug_card 解析失敗：${it.message}") }
            return
        }
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val messages = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.filterIsInstance<NdefMessage>()?.toTypedArray()
        }
        val msg = messages?.firstOrNull() ?: return state.message("讀不到卡片資料")
        runCatching { NfcIo.decodeMessage(msg) }
            .onSuccess { state.acceptCard(it) }
            .onFailure { state.message(it.message ?: "卡片資料損毀") }
    }
}

/** SHA 不符的卡片不採用。 */
internal fun ShaStatus.acceptable() = this == ShaStatus.OK
