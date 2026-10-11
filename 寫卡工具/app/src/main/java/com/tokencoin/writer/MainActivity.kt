package com.tokencoin.writer

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tokencoin.writer.nfc.NfcIo
import com.tokencoin.writer.ui.AppScreen
import com.tokencoin.writer.ui.AppState
import com.tokencoin.writer.ui.TokenCoinTheme
import com.tokencoin.writer.ui.WriteStatus

enum class NfcAvailability { UNSUPPORTED, DISABLED, READY }

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfc: NfcAdapter? = null
    private lateinit var state: AppState
    private var nfcAvailability by mutableStateOf(NfcAvailability.READY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfc = NfcAdapter.getDefaultAdapter(this)
        state = AppState(applicationContext)
        setContent {
            TokenCoinTheme {
                AppScreen(
                    state = state,
                    nfc = nfcAvailability,
                    openNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfc
        nfcAvailability = when {
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
        nfc?.disableReaderMode(this)
    }

    /** 在 NFC binder 執行緒上執行。有待寫任務就寫卡，否則讀卡。 */
    override fun onTagDiscovered(tag: Tag) {
        val job = state.pendingWrite
        if (job != null) {
            val status = try {
                val msg = NfcIo.buildMessage(job.card, job.aarPackage, job.webUrl)
                val r = NfcIo.write(tag, msg, job.lock)
                val cap = r.info.maxSize?.let { " / $it" } ?: ""
                WriteStatus.Success(
                    "已寫入 ${r.bytes}$cap bytes\nUID ${r.info.uid}" +
                        (if (r.locked) "\n卡片已永久鎖定為唯讀" else "") + "\n回讀驗證通過 ✓",
                )
            } catch (e: Exception) {
                WriteStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread { state.finishWrite(status) }
        } else {
            val result = runCatching {
                val read = NfcIo.read(tag)
                val decoded = read.decoded
                if (decoded != null) try { read.copy(decoded = com.tokencoin.writer.net.HostedCards.resolve(applicationContext, decoded).first) } catch (e: Exception) { read.copy(decoded = null, error = e.message ?: "託管設定讀取失敗") } else read
            }
            runOnUiThread {
                result.onSuccess {
                    state.lastRead = it
                    state.tab = 1
                }
            }
        }
    }
}
