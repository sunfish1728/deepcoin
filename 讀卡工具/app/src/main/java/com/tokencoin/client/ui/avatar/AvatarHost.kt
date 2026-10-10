package com.tokencoin.client.ui.avatar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewAssetLoader
import com.tokencoin.client.avatar.AvatarInfo
import com.tokencoin.client.avatar.AvatarRepository
import com.tokencoin.client.avatar.Emotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 渲染器狀態（給 Compose 顯示載入中 / 錯誤）。 */
sealed interface StageStatus {
    data object Empty : StageStatus
    data class Loading(val progress: Float?) : StageStatus
    data class Ready(val name: String) : StageStatus
    data class Failed(val message: String) : StageStatus
}

/** 角色說的一句話（點擊反應），id 遞增讓 UI 重新觸發動畫。 */
data class StageLine(val id: Long, val text: String)

/**
 * 整個 App 共用一個 WebView 渲染器（模型很大，不重複載入）。
 *
 * 「傳送門」做法：WebView 不放進 Compose（放在 AndroidView 裡不會更新畫面，且兩個畫面同時存在時會搶同一個 View），
 * 而是放在 Compose 底下的原生 View 層（[install]）。主頁舞台 / 聊天面板以 [showSlot] 回報自己在視窗中的位置，
 * WebView 移過去對齊；整頁畫布在該處留洞（[hole] → LocalCanvasHole），觸控由舞台轉發（[dispatchTouch]）。
 *
 * 渲染器在 assets/avatar/（原始碼在 avatar-web/），模型由 [AvatarRepository.rootDir] 提供，
 * 兩者都經 WebViewAssetLoader 以 https://appassets.androidplatform.net/ 存取。
 */
class AvatarHost(private val context: Context, private val repo: AvatarRepository, private val scope: CoroutineScope) {
    var status by mutableStateOf<StageStatus>(StageStatus.Empty)
        private set
    var line by mutableStateOf<StageLine?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private val pending = mutableListOf<String>()
    private var loadedKey: String? = null
    private val slots = mutableListOf<Pair<Any, android.graphics.Rect>>()
    private var activityPaused = false
    private var lineSeq = 0L

    private val loader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/avatars/", WebViewAssetLoader.InternalStoragePathHandler(context, repo.rootDir.apply { mkdirs() }))
        .build()

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            // 渲染器頁面內不允許導航到別處
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                status = StageStatus.Failed("渲染器已停止，請重新開啟 App")
                return true
            }
        }
        // 渲染器的 console 轉到 logcat（tag TcStage）；debug 版可用 chrome://inspect 除錯
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d("TcStage", "${m.messageLevel()} ${m.message()} @${m.lineNumber()}")
                return true
            }
        }
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        addJavascriptInterface(Bridge(), "TcBridge")
        loadUrl("https://appassets.androidplatform.net/assets/avatar/index.html")
    }

    private val container = FrameLayout(context).apply {
        visibility = View.INVISIBLE
        addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** 目前舞台在視窗中的位置；整頁畫布在這裡留洞。 */
    var hole by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        private set

    /** 放到 Activity 內容層的最底下（ComposeView 之下）。 */
    fun install(content: ViewGroup) {
        (container.parent as? ViewGroup)?.removeView(container)
        content.addView(container, 0, FrameLayout.LayoutParams(1, 1))
    }

    init {
        scope.launch {
            repo.active.distinctUntilChangedBy { it?.let { a -> a.id + "/" + a.modelPath + "/" + a.manifestJson } }
                .collect { load(it) }
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(json: String) {
            main.post { onEvent(json) }
        }
    }

    private fun onEvent(json: String) {
        val e = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (e.optString("type")) {
            "ready" -> {
                ready = true
                pending.forEach { webView.evaluateJavascript(it, null) }
                pending.clear()
                applyPause()
            }
            "progress" -> {
                val total = e.optDouble("total", 0.0)
                status = StageStatus.Loading(if (total > 0) (e.optDouble("loaded") / total).toFloat().coerceIn(0f, 1f) else null)
            }
            "log" -> android.util.Log.w("TcStage", e.optString("message"))
            "loaded" -> status = StageStatus.Ready(e.optString("name"))
            "error" -> status = StageStatus.Failed(e.optString("message", "模型載入失敗"))
            "react" -> e.optString("line").takeIf { it.isNotBlank() }?.let { line = StageLine(++lineSeq, it) }
        }
    }

    private fun call(fn: String, vararg args: Any?) {
        val js = "window.tc&&tc.$fn(" + args.joinToString(",") { arg ->
            when (arg) {
                null -> "null"
                is String -> JSONObject.quote(arg)
                is Boolean, is Number, is JSONObject -> arg.toString()
                else -> JSONObject.quote(arg.toString())
            }
        } + ")"
        if (ready) webView.evaluateJavascript(js, null) else pending += js
    }

    private fun load(info: AvatarInfo?) {
        val key = info?.let { it.id + "/" + it.modelPath + "/" + it.manifestJson }
        if (key == loadedKey) return
        loadedKey = key
        if (info == null) {
            status = StageStatus.Empty
            call("clear")
            return
        }
        status = StageStatus.Loading(null)
        val path = (listOf(info.id) + info.modelPath.split('/')).joinToString("/") { Uri.encode(it) }
        // manifest 原文以字串傳入，渲染器自行解析（解析失敗時用預設值）
        call("load", "https://appassets.androidplatform.net/avatars/$path", info.manifestJson)
        // 刷卡後換了形象：渲染器會在載入完成時跳出
        if (appearPending) {
            appearPending = false
            call("appear")
        }
    }

    private var appearPending = false
    private var appearJob: Job? = null

    /**
     * 刷卡時呼叫：讓形象「跳出來」打招呼。
     * 換卡可能切換形象或開始下載，先等形象倉庫決定好、下載完成；換了模型的話由 [load] 接手。
     */
    fun appear() {
        appearPending = true
        appearJob?.cancel()
        appearJob = scope.launch {
            delay(400)
            repo.task.first { it == null }
            delay(400)
            if (appearPending) {
                appearPending = false
                call("appear")
            }
        }
    }

    // ---- 舞台位置：最後登記的舞台優先（換畫面的動畫期間兩個舞台會同時存在） ----

    /** rect 為視窗座標 */
    fun showSlot(key: Any, rect: android.graphics.Rect) {
        val i = slots.indexOfFirst { it.first === key }
        if (i >= 0) slots[i] = key to rect else slots += key to rect
        layoutSlot()
    }

    fun hideSlot(key: Any) {
        slots.removeAll { it.first === key }
        layoutSlot()
    }

    private fun layoutSlot() {
        val r = slots.lastOrNull()?.second
        if (r == null || r.isEmpty) {
            container.visibility = View.INVISIBLE
            hole = null
        } else {
            val parent = container.parent as? View
            val origin = IntArray(2).also { parent?.getLocationInWindow(it) }
            val lp = container.layoutParams as FrameLayout.LayoutParams
            if (lp.width != r.width() || lp.height != r.height()) {
                lp.width = r.width()
                lp.height = r.height()
                container.layoutParams = lp
            }
            container.translationX = (r.left - origin[0]).toFloat()
            container.translationY = (r.top - origin[1]).toFloat()
            container.visibility = View.VISIBLE
            hole = androidx.compose.ui.geometry.Rect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
        }
        applyPause()
    }

    /** 舞台收到的觸控（座標已是舞台內座標）轉給 WebView */
    fun dispatchTouch(e: MotionEvent): Boolean = webView.dispatchTouchEvent(e)

    // ---- 可見性：沒有任何舞台在畫面上，或 Activity 暫停時，停止渲染省電 ----

    fun onActivityPause() { activityPaused = true; webView.onPause(); applyPause() }
    fun onActivityResume() { activityPaused = false; webView.onResume(); applyPause() }

    private fun applyPause() = call("pause", activityPaused || slots.isEmpty())

    // ---- 指令 ----

    fun camera(preset: String, animate: Boolean = true) = call("camera", preset, animate)
    fun interactive(on: Boolean) = call("interactive", on)
    fun rotatable(on: Boolean) = call("rotatable", on)
    fun emote(emotion: Emotion, intensity: Float = 1f, holdMs: Int = 4000) = call("emote", emotion.key, intensity, holdMs)
    fun gesture(name: String) = call("gesture", name)
    fun think(on: Boolean) = call("think", on)
    fun talk(on: Boolean) = call("talk", on)
    fun speak(text: String) { if (text.isNotEmpty()) call("speak", text) }
    fun react(zone: String) = call("react", zone)

    fun theme(shadow: Int, shadowAlpha: Float) =
        call("theme", JSONObject().put("shadow", String.format("#%06X", shadow and 0xFFFFFF)).put("shadowAlpha", shadowAlpha.toDouble()))

    fun destroy() {
        (container.parent as? ViewGroup)?.removeView(container)
        container.removeView(webView)
        webView.destroy()
    }
}
