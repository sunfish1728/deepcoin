# 讀卡工具（鯨幣 客戶端，com.tokencoin.client）

> 全專案資訊見上層的 [../CLAUDE.md](../CLAUDE.md)，卡片格式見 [../SPEC.md](../SPEC.md)。本檔只記錄讀卡工具本身。
> App 在手機上顯示的名稱是「鯨幣」（0.5.0 起；之前叫「Token幣」。套件名不變，舊版可直接覆蓋安裝）。它也是寫卡工具預設寫入卡片的 AAR，所以刷卡時會自動開啟。

## 目前功能

- **刷卡開啟**：
  - App 沒在前景時：靠 manifest 的 `NDEF_DISCOVERED`（mimeType `application/vnd.tokencoin+json`）加上卡上的 AAR 開啟，`launchMode=singleTask`。
  - App 在前景時：用 reader mode 直接讀卡。
- 讀卡會驗證 SHA-256，**不符就不載入**。
- 最後一張卡以正規化 JSON 保存在 SharedPreferences `tokencoin_client`，重開 App 仍在。
- 讀卡結果用 Snackbar 提示。
- 底部三個分頁：
  - **主頁**：上方大片空間是虛擬形象舞台（VRM，見下方「虛擬形象」）；下方是卡片資訊面板（右側顯示即時餘額）和「聊天」按鈕。
  - **寫卡**（0.4.0，原「跳轉」分頁，RikkaHub 擱置）：移植自寫卡工具，見下方「寫卡」。
  - **設定**（0.4.0）：卡片完整資訊、餘額、外觀、形象、聊天、關於，見下方「設定」。
- **即時餘額**（0.4.0）：`net/BalanceTracker.kt` 沿用寫卡工具的 `ApiProbe.queryBalance`。
  - 換卡、回到 App 時，距上次查詢超過 5 分鐘就自動查（設定可關）；點主頁餘額區塊可手動重查。
  - 優先顯示美元；查不到時退回卡片記錄的 `balance_usd`。中轉站回傳的 `access_until` 顯示為「密鑰至」標籤。
  - 官方 OpenAI / Anthropic / Gemini / 百煉 / 智譜 / xAI / Mistral 沒有用密鑰查餘額的介面，直接標「不支援查詢」，不發請求。
  - 每張卡最近一次結果存在 SharedPreferences `tokencoin_balance`，離線時仍顯示上次的數字。
- **寫卡**（0.4.0）：表單、預設清單、檢測模型列表與餘額、容量估算、AAR、寫入後永久鎖定、設定檔，功能與寫卡工具 0.2.0 相同，改用本 App 的 Tc 元件重畫。
  - 有待寫任務時，`MainActivity.onTagDiscovered` 改為寫卡，否則照常讀卡。
  - 「載入本機卡片」可把目前卡片當底稿（換新 ID）；寫入成功後可「同時設為本機卡片」，不用再刷一次。
  - 草稿與設定檔存在獨立的 SharedPreferences `tokencoin_writer`，和寫卡工具 App 的資料不互通。
- **設定**（0.4.0）：
  - 卡片：SHA 狀態、所有欄位（密鑰預設遮住，可顯示 / 複製）、複製 JSON、載入到寫卡、移除本機卡片。
  - 餘額：自動查詢開關、立即查詢。
  - 外觀：主題（跟隨系統 / 淺色 / 深色）。
  - 形象：管理形象、主頁預設鏡頭（和主頁舞台的鏡頭標籤同步）、觸控互動開關、聊天時顯示形象面板。
  - 聊天：清除所有對話。關於：版本、格式版本、開源元件。
- **聊天系統**（點主頁「聊天」進入，2026-10-10 完成）：
  - 支援 4 種協議的串流回覆：openai / openai-responses / anthropic / gemini。
  - 顯示思考過程（可收合），回覆完成後顯示 token 用量和耗時。
  - 可停止、重新產生、編輯並重送、刪除、複製。
  - 自動產生對話標題；對話紀錄抽屜支援搜尋、重新命名、刪除。
  - 可切換模型（來自卡片的預設模型與其他模型）。
  - 每段對話可設系統提示詞、溫度、最大輸出 token。
  - Markdown 渲染：標題、清單、引言、程式碼區塊（附複製）、表格、粗斜體、行內程式碼、連結。
  - 對話存成 JSON，放在 `filesDir/chat/<id>.json`。**不存卡片密鑰**，一律使用目前刷入的卡片。
- **刷卡跳出形象**（0.5.0）：刷卡後回到主頁，形象從舞台下緣彈上來打招呼（`AvatarHost.appear()` → 渲染器 `tc.appear()`）。
  - 卡片 `avatar` 可以是形象庫網址（GitHub 倉庫或 `deepcoin-avatars.json`），App 讀索引依 `char_id` 挑角色、驗證 SHA-256 後安裝；格式見 [AVATAR.md](AVATAR.md) §6，建庫流程見 [../形象倉庫/README.md](../形象倉庫/README.md)。
  - 下載後快取，來源鍵是「倉庫網址#char_id」（直接網址則是網址本身），再刷同一張卡不連網。
  - 下載中斷會用 Range 續傳，最多 3 次（伺服器不支援 Range 就從頭重下）。
  - 「線上形象庫」：形象清單和主頁空舞台都有入口，用 GitHub 搜尋 topic `deepcoin-avatar` 的公開倉庫，或貼上倉庫網址；寫卡頁的「從形象庫選擇」會填好角色名稱、角色 ID 與倉庫網址。
- **所有模型**（0.5.0）：寫卡時「使用此 API 的所有模型」會在卡上記 `models: ["*"]`；讀卡後 `net/ModelCatalog.kt` 向 API 取模型清單（沿用 ApiProbe.listModels，快取在 `tokencoin_models`），補進聊天的模型選單。檢測結果對話框也有「其他全選」與「改用 *」。
- **虛擬形象**（2026-10-10，0.3.0）：格式規格見 [AVATAR.md](AVATAR.md)。
  - 支援 VRM 0.x / 1.0，單一 `.vrm` 不用設定就能動；`.zip` 角色包可附 `avatar.json` 客製表情對應、台詞、鏡頭等。
  - 來源：主頁舞台右上角「形象」→ 匯入；或卡片 `avatar` URL 自動下載。依卡片 `char_id` 記住每張卡用哪個形象（決定規則見 AvatarApi.kt）。
  - 待機：手臂自然下垂、呼吸、搖擺、隨機眨眼、視線跳動；頭和眼睛跟著觸控點。
  - 互動：點頭 / 身體 / 手 / 腳各有反應（動作 + 表情 + 台詞泡泡）；在頭上來回摸會閉眼享受；短時間戳太多次會生氣；水平拖曳轉身（放手有慣性，10 秒後轉回正面）；雙指縮放；雙擊空白處重置。
  - 鏡頭：全身 / 半身 / 特寫，從網格邊界與骨骼自動算出（Q 版大頭與一般比例都適用）。
  - 聊天連動：聊天畫面頂部有半身形象面板（右上角臉形按鈕可收起，鍵盤打開時自動收起）；回覆開始時思考、串流時依文字動嘴、完成後依回覆內容推測情緒、失敗時難過。
  - 形象清單顯示縮圖（從 VRM 抽出）、作者、VRM 版本、大小、授權摘要。
- 允許 `http://` 明文連線（`usesCleartextTraffic=true`），方便使用區網中轉站或本機的 Ollama / LM Studio。

## 介面風格

- 參考 `C:/Users/hhjju/Desktop/前端風格md/MaiBot-UI-Style-Guide.zh-TW.md`（復古工業控制台、暖紙顆粒）。使用者要求「參考但不要照抄，直角改成微弧角」。
- 主題寫在 `ui/theme/TcTheme.kt`：
  - 淺色：奶油底 `#F3E6D4`、深青綠結構色、鏽橘操作色。
  - 深色：深褐底 `#1F1715`、暖米灰結構色。
  - 顆粒是程式產生的 96px 噪點，用 `Modifier.tcCanvas()` 平鋪。
  - 圓角：控制元件 4dp、面板 6dp、大型容器 10dp。
- 共用元件在 `ui/components/`：
  - `TcPanel`：可加左側粗條和鉚點。
  - `TcButton`：Primary / Secondary / Ghost 三種。
  - 其他：`TcIconButton`、`TcTag`、`TcTextAction`、`TcInput`、`BrandMark`（字標加階梯色塊）、`TcTopBar`、`TcBottomNav`、`TcSectionLabel`、`StatusDot`。
- 新畫面請沿用這些元件，不要另外引入一套視覺語言。

## 程式結構（app/src/main/java/com/tokencoin/client/）

| 路徑 | 內容 |
|---|---|
| `card/TokenCard.kt` | 卡片格式編解碼與 SHA。**跟寫卡工具各有一份，要保持一致** |
| `card/Presets.kt` | 模型供應商、API 供應商、協議預設清單（從寫卡工具複製） |
| `nfc/NfcIo.kt` | 讀取：`read`、`decodeMessage`、`findCardRecord`；寫入：`buildMessage`、`write`（從寫卡工具移植） |
| `net/ApiProbe.kt` | 模型列表與餘額查詢（從寫卡工具複製，另加「官方 API 不支援查餘額」的快速判斷） |
| `net/BalanceTracker.kt` | 目前卡片的即時餘額與快取 |
| `net/ModelCatalog.kt` | 卡片 models 為 `*` 時向 API 取模型清單 |
| `writer/WriterState.kt` | 寫卡表單 CardForm（驗證、轉 TokenCard）、設定檔、寫卡任務狀態 |
| `ui/writer/` | 寫卡分頁、表單欄位元件、檢測結果 / 設定檔 / 寫卡流程對話框 |
| `ui/settings/` | 設定頁 |
| `ClientState.kt` | 目前卡片、分頁、聊天是否開啟、Snackbar 訊息、各項設定（主題、自動查餘額、形象觸控、主頁鏡頭）；換卡時通知聊天、形象、餘額 |
| `MainActivity.kt` | intent 和 reader mode 的處理；建立聊天引擎；同步狀態列圖示深淺 |
| `chat/ChatApi.kt` | **前後端契約**：資料型別與 `ChatEngine` 介面。改動時前後端都要同步 |
| `chat/ChatEngineImpl.kt` | 引擎實作：單一指令佇列處理所有狀態與存檔，串流更新每 50ms 節流一次，自動標題 |
| `chat/ChatStreamingClient.kt` | OkHttp 加手寫 SSE 解析，4 種協議；歷史訊息會排除錯誤訊息，並合併相鄰的同角色訊息 |
| `chat/ChatStore.kt` | 對話 JSON 原子寫入 |
| `ui/ClientApp.kt` | 根畫面：分頁和聊天畫面切換（有動畫） |
| `avatar/AvatarApi.kt` | **形象模組前後端契約**：AvatarInfo、AvatarRepository（含形象決定規則）、Emotion、EmotionAnalyzer |
| `avatar/`（其餘） | 後端（Codex）：VrmInspector（只讀 GLB 的 JSON 與縮圖）、AvatarInstaller（原子安裝、zip 防路徑穿越）、AvatarResolver（純函式決定規則）、AvatarRepositoryImpl（單一指令佇列、匯入 / 下載 / 續傳 / 形象庫）、KeywordEmotionAnalyzer；AvatarRepoFormat（形象庫網址與索引解析，純函式，Claude 寫） |
| `ui/avatar/AvatarHost.kt` | 共用的 WebView 渲染器與 JS 橋接；「傳送門」放置（見下方注意事項） |
| `ui/avatar/AvatarStage.kt` | 舞台插槽：回報位置、轉發觸控、載入進度、台詞泡泡 |
| `ui/avatar/AvatarSheet.kt`、`AvatarChatLink.kt` | 形象清單；聊天 → 形象連動 |
| `ui/avatar/AvatarRepoBrowser.kt` | 線上形象庫（安裝模式 / 寫卡挑選模式） |
| `ui/home/` | 主頁（形象舞台、卡片面板與餘額、聊天按鈕） |
| `ui/chat/` | ChatScreen、ConversationDrawer、MessageItems、Markdown、ChatDialogs |
| `ui/theme/`、`ui/components/` | 主題與共用元件 |

## 虛擬形象渲染器（avatar-web/）

- three.js 0.180.0 + @pixiv/three-vrm 3.4.4，用 esbuild 打包成單一檔案 `app/src/main/assets/avatar/stage.js`（約 720KB），完全離線。
- 修改後重新打包：`cd avatar-web && npm install && npm run build`（`npm run dev` 是未壓縮版加 sourcemap，**不要把 .map 留在 assets**）。
- 模組：`main.js`（JS 橋接、觸控）、`stage.js`（場景、鏡頭、部位判斷）、`rig.js`（待機姿勢、頭部跟隨、動作疊加）、`face.js`（表情、眨眼、嘴型）、`gestures.js`（動作資料表，加動作只要加一筆）、`manifest.js`（avatar.json 預設值）。
- Android → JS：`window.tc.load/emote/gesture/think/talk/speak/camera/react/rotatable/pause/theme`；JS → Android：`TcBridge.post(json)`。
- 瀏覽器快速測試：用任何靜態伺服器提供 `app/src/main/assets/avatar/`，開 `index.html?model=<vrm 網址>&camera=bust`；`window.__stage` 可在 console 檢查狀態。

### 注意事項（踩過的坑）

- **WebView 不能放在 Compose 的 AndroidView 裡**：實測畫面只停在第一幀（空白），而且兩個畫面同時存在時搶同一個 View 會閃退。
  現在的做法是「傳送門」：WebView 放在 Compose 底下的原生內容層，舞台插槽回報視窗座標後把它移過去；
  整頁畫布（`tcCanvas(hole = true)`）在舞台位置留洞，原生內容層背景用同一張顆粒圖，所以看不出接縫；觸控由插槽的 `pointerInteropFilter` 轉發。
- `avatarHost.install()` 要在 `setContent` 之後呼叫（setContent 會清空內容層）。
- 新的整頁畫面如果蓋在舞台上，要用 `tcCanvas(hole = true)`，否則形象會被蓋住。對話框、底部面板是另一個視窗，不要開 hole。
- debug 版開了 WebView 遠端除錯：`adb forward tcp:9333 localabstract:webview_devtools_remote_<pid>` 後可用 DevTools Protocol 對頁面下指令；console 會轉到 logcat tag `TcStage`。

## 研究筆記：RikkaHub（2026-10-10 讀過原始碼，github.com/rikkahub/rikkahub）

- 授權 **AGPL-3.0**，改版要開源。套件名 `me.rerere.rikkahub`，debug 版加 `.debug`。
- **原版沒有任何讓外部 App 寫入供應商的入口。** 只能在 App 內「設定 → 提供商」掃 QR Code，或從圖片匯入。
- 分享碼格式是 `ai-provider:v1:` 加上 Base64（標準字元集、有 padding）編碼的 `ProviderSetting` JSON，見 `ui/components/ui/ShareSheet.kt`。
  - JSON 型別判別欄位是 `type`：`openai` / `claude` / `google`。
  - 欄位有 `id`(UUID)、`name`、`apiKey`、`baseUrl`、`models[]`、`useResponseApi` 等。
  - `ignoreUnknownKeys=true`。
  - `Model` 的欄位有 `modelId`、`displayName`、`id`(UUID)、`type`(CHAT)。
- baseUrl 慣例：
  - OpenAI 的地址含 `/v1`。
  - Claude 是 `https://api.anthropic.com/v1`。
  - Google 是 `https://generativelanguage.googleapis.com/v1beta`。
  - 卡片上 anthropic 和 gemini 的 `base` 不含版本路徑，轉換時要補上。
- 預設模型存在 `Settings.chatModelId`，助手層級是 `Assistant.chatModelId`，見 `data/datastore/`。
- 建置需要 Firebase（google-services、crashlytics plugin），改版時要放一個假的 `google-services.json`，或拿掉這些 plugin。
- 規劃中的對接方式（擱置）：
  - 改版 RikkaHub 新增 intent action `com.tokencoin.action.APPLY_CARD`，extra 帶卡片的正規化 JSON。
  - 以 `UUID.nameUUIDFromBytes("tokencoin:provider:"+卡片id)` 產生固定的供應商 ID，再刷同一張卡會更新而不是重複新增。
  - 設好 chatModelId 後直接開新對話。
  - 讀卡工具這一端，用 `queryIntentActivities` 偵測已安裝的改版。Android 11 以上需要在 manifest 加 `<queries>`。

## 測試方法（沒有實體卡時）

- 測試用 AVD 是 `TokenCoin_Test_API34`，port 5556。使用者自己的 BattleCats 和 Medium_Phone 模擬器不要動。
- 注入卡片的方法：
  1. 產生 `shared_prefs/tokencoin_client.xml`，key 是 `card`，值是正規化 JSON（含 sha）。
  2. 用 `adb push` 推到 `/data/local/tmp`。
  3. 用 `run-as com.tokencoin.client` 複製進 App 資料夾。
  - Git Bash 下要設 `MSYS_NO_PATHCONV=1`，否則路徑會被轉換。
- 本機模擬伺服器：卡片 `base` 設為 `http://10.0.2.2:<port>/v1`，模擬器就能連到電腦。
  - 模擬伺服器加上 new-api 風格的 `/v1/dashboard/billing/subscription`、`/usage` 和 `/v1/models`，就能測主頁餘額與寫卡頁的檢測。
- 模擬器沒有 NFC，寫卡分頁會顯示「不支援 NFC」並停用寫入鍵；實際寫卡只能在真機測。
- **模擬刷卡**（debug 版限定，0.5.0）：`adb shell "am start -n com.tokencoin.client/.MainActivity --es debug_card '<正規化卡片 JSON（含 sha）>'"`，走和 NFC 一樣的 acceptCard（含跳出動畫）。JSON 裡不要放中文，adb shell 會亂碼。
- 形象庫測試：用 `形象倉庫/deepcoin_avatar.py pack ... --host repo --private` 在 scratchpad 建一個本機形象庫，`python -m http.server 18090` 提供，卡片 avatar 填 `http://10.0.2.2:18090/deepcoin-avatars.json`。
  - ⚠ 模擬器的網路（Windows 主機）在伺服器關閉連線時常常吃掉最後幾個 bytes，下載會報 `unexpected end of stream`；續傳機制會重試，真機不會有這個問題。縮圖也可能因此顯示不出來。
- 形象測試：`adb push model.vrm /sdcard/Download/`，在 App 裡用「匯入形象」選它。**模型檔不要放進專案或 APK**（示範模型禁止再配布）。

## 開發紀錄

- **2026-10-10**：0.1.0 骨架。
  - 刷卡開啟、讀卡、SHA 驗證、保存卡片、三個分頁。
  - 從多模組專案拆出來成為獨立專案。
  - APK：`dist/Token幣讀卡工具-0.1.0.apk`
- **2026-10-10**：0.2.0 聊天系統與 MaiBot 風格改版。
  - 分工：Claude 負責規劃、契約、前端和審查；Codex 負責後端（chat/ 底下除了 ChatApi.kt 以外的檔案和測試）。
  - Claude 審查後修正了 4 個問題：
    - 自動標題的請求以助手訊息結尾，Anthropic 會當成預填而續寫回答。
    - 相鄰的同角色訊息沒有合併，Gemini 會拒絕。
    - SSE 空白的 data 行會讓整個串流報錯。
    - 標題清理把英文單字之間的空白也吃掉了。
  - 單元測試共 24 個（卡片 4、chat 20），全部通過。
  - 在模擬器上用本機模擬的 OpenAI SSE 伺服器實測：串流、思考過程、用量、自動標題、停止、重開 App 後對話仍在、連線失敗的錯誤顯示，皆正常。淺色和深色主題都截圖確認過。
  - APK：`dist/Token幣讀卡工具-0.2.0.apk`
- **2026-10-10**：0.3.0 虛擬形象（使用者稱 MMD 互動；示範模型為 VRM 1.0「蓝色小肥鱼 Deepseek鲸鱼娘」，作者木子恶魔）。
  - 分工：Claude 負責規劃、契約（AvatarApi.kt、AVATAR.md）、渲染器（avatar-web/）、Android 前端與審查；Codex 負責 avatar/ 後端與 31 個測試。
  - 審查後補了一處：卡片 avatar URL 回傳圖片或網頁時提早放棄，不整個下載。
  - Codex 在沙箱裡無法寫入 ~/.gradle，自己把快取複製到 `app/build/avatar-gradle-home`（2.5GB），已刪除。
  - 單元測試共 55 個（卡片 4、chat 20、形象 31），全部通過。
  - 模擬器實測：匯入、全身 / 半身鏡頭、點擊反應與台詞泡泡、聊天時思考 → 說話、淺色深色主題、頁面切換。
  - APK：`dist/Token幣讀卡工具-0.3.0.apk`

- **2026-10-10**：0.4.0 寫卡移植、即時餘額、設定頁（使用者指示：跳轉改為寫卡並移植寫卡工具、主頁顯示餘額、加設定）。
  - 這次由 Claude 自己完成：大多是把寫卡工具已有的程式複製過來改用 Tc 元件；另外 Codex 跑 Gradle 會和本機建置搶鎖、還會自建好幾 GB 快取，不划算。
  - 新增 CardFormTest 3 個，單元測試共 58 個，全部通過。
  - 模擬器實測：主頁即時餘額（模擬中轉站 $9.29、密鑰到期標籤）、寫卡頁表單與下拉、載入本機卡片、檢測模型與餘額、設定頁卡片資訊、淺色主題切換、主頁預設鏡頭、移除本機卡片。
  - **未測**：實際寫卡（模擬器沒有 NFC）、真實供應商的餘額查詢。
  - APK：`dist/Token幣讀卡工具-0.4.0.apk`

- **2026-10-10**：0.5.0 改名鯨幣、所有模型、形象庫（使用者指示：其他可用模型可設為 API 的所有模型並自動檢測；App 改名鯨幣、去掉字標的彩色階梯；推上 GitHub；有 URL 的 VRM 刷卡時自動下載並跳出、要快取，URL 從 GitHub 取得，制定標準流程與格式，且要能抓使用者自建的任何符合格式的倉庫）。
  - Claude 自己完成（後端改動不大，且 Codex 的 Gradle 會和本機建置衝突）。
  - **修了 0.3.0 的 bug**：角色包的 avatar.json 指定 `model` / `thumb` 時，安裝器在 Android 上會存成 `../../../…/.tmp-…/model.vrm`（filesDir 經過 /data/user/0 → /data/data 符號連結，canonical 與非 canonical 路徑混用），導致模型載入不了。改成暫存目錄一開始就用 canonical 路徑，加了測試。
  - 新增測試：AvatarRepoFormatTest 6 個、安裝器 1 個；單元測試共 65 個，全部通過。
  - 模擬器實測：改名與字標、模擬刷卡跳回主頁、從本機形象庫下載（遇到模擬器斷流，續傳後成功）、安裝後模型路徑正確、再刷同一張卡不連網直接跳出（量到跳出動畫）、設定頁顯示「自動檢測到 3 個」模型、GitHub topic 搜尋（目前沒有倉庫）、寫卡頁從形象庫挑角色自動填欄位。
  - **未測**：真的 GitHub 倉庫與 Releases 下載（需要一個允許再配布的模型）、真機。
  - APK：`dist/Token幣讀卡工具-0.5.0.apk`

## 待辦

- [ ] 真機測試：關掉 App 後刷卡，確認會自動開啟
- [ ] 用真實 API 密鑰實測 4 種協議（目前只用模擬伺服器測過 openai 協議）
- [ ] 真機測試形象的效能與觸控（模擬器的 GPU 和真機差很多）
- [ ] 用允許再配布的模型建一個真的 GitHub 形象庫，實測 Releases 下載與線上形象庫搜尋
- [ ] 形象庫角色更新版本時，App 端目前不會自動更新（可考慮比對索引的 version / sha256）
- [ ] 可能的擴充：PMX（MMD 原生格式）、VRMA 動作檔、語音（TTS）對嘴
- [ ] 真機真卡測試寫卡分頁（寫入、回讀驗證、鎖定、寫完設為本機卡片）
- [ ] 用真實密鑰驗證各家餘額查詢（DeepSeek、OpenRouter、SiliconFlow、Moonshot、中轉站）
- [ ] RikkaHub 跳轉（擱置中；分頁已改為寫卡，日後要做可放在設定或主頁）
