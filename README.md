# 鯨幣（deepcoin）

**讓一張 NFC 卡帶著 AI 的設定，也帶著它的角色。**

鯨幣是一個自己做的實體小玩具配套：把 API 密鑰、模型設定與角色資訊放進 NFC 卡，搭配玩具或收藏品送出去。手機刷卡後，就能載入對應的 AI，讓角色的 VRM 形象出現在畫面上，再進入聊天。

例如，一枚「DeepSeek 娘幣」可以附上供應商已設定額度的 API key，以及 DeepSeek 鯨魚娘的角色資訊。拿到的人刷卡，就能用這組設定聊天，不必重新抄寫 API 地址和模型名稱。「幣」是這個實體小玩具的叫法；可用額度、有效期與實際計費，仍由 API 供應商管理。

[下載 Android App](https://github.com/sunfish1728/deepcoin/releases/tag/v0.7.0) · [直接開啟網頁版](https://sunfish1728.github.io/deepcoin-web/) · [角色形象庫](https://github.com/sunfish1728/deepcoin-avatars)

## 一張卡，串起三件事

鯨幣把「用哪個 AI」「怎麼連線」和「顯示誰」放在同一張卡裡，但三者可以各自替換。

| 卡片帶著的資訊 | 用途 | 例子 |
|---|---|---|
| 模型資訊 | 決定預設模型與可選模型 | DeepSeek、其他供應商的模型，或自動取得 API 的全部模型 |
| API 連線資訊 | 決定請求送到哪裡、用哪種協議與密鑰 | 官方 API、相容服務、區網中的模型服務 |
| 角色資訊 | 決定刷卡後出現的形象 | 角色名稱、角色 ID、VRM／角色包／形象庫網址 |

模型原廠與 API 供應商分開記錄，因為同一個模型可能由不同服務提供。角色 ID 與模型網址也分開：ID 用來記住角色和使用者的形象選擇，網址負責取得資源。

一張卡可以有自己的名字、備註、美元餘額參考值與角色；換另一張卡，就換成另一組 AI 設定。聊天請求由 App 或瀏覽器直接送往卡片指定的 API，鯨幣沒有代轉聊天的中轉後端。

## 從寫卡到聊天

1. **準備 API key。** 在供應商那裡設定可用額度、有效期等限制；卡片裡的餘額與到期欄位只供顯示，不會替供應商強制限額。
2. **在 Android 的「寫卡」頁填設定。** 選供應商、協議、API 地址和模型，也可以檢測模型列表與支援查詢的餘額。
3. **選擇形象與儲存方式。** 角色可以稍後手動匯入；設定可以全部放卡上，也能開啟外部託管節省容量。
4. **確認容量，再靠卡寫入。** App 會估算 NDEF 大小，寫入後回讀核對。永久鎖定是可選操作，鎖定後無法再改卡片內容。
5. **刷卡使用。** 載入設定後回到主頁，角色出場，點「聊天」進入完整對話畫面。

寫卡網址留空時，0.7.0 會使用 `https://sunfish1728.github.io/deepcoin-web/`；也可填自己的 HTTPS 網站。沒有安裝 App 的手機可透過卡片網址開網頁，系統可能要求點擊 NFC 通知。已安裝 App 時是否直接進 App，仍取決於 Android 的連結設定；網頁也提供「用 App 開啟」。

GitHub 專案 Pages 無法在帳號網域根目錄提供本站所需的 Digital Asset Links，因此目前不保證每部手機都能自動開 App。真機上的 NFC 與連結開啟行為仍需確認。

## Android App 與網頁版

Android App 顯示名稱是「鯨幣」，套件名維持 `com.tokencoin.client`。主頁上方是角色舞台，下方是卡片資訊、餘額與聊天入口；底部有主頁、寫卡、設定三個分頁。

網頁版延續相同的舞台、色彩、微弧角元件與聊天布局。即使在桌面上，也保留 App 的單欄介面；省略寫卡後，底部只有主頁與設定。

| 功能 | Android 鯨幣 | 網頁版 |
|---|---|---|
| 載入卡片設定 | NFC、卡片連結 | 卡片網址、貼上 JSON／連結、匯入 JSON；支援的瀏覽器另可使用 Web NFC |
| 寫卡與容量估算 | 支援，也有獨立寫卡工具 | 不提供 |
| 外部託管卡 | 上傳設定、讀取與快取 | 讀取與快取 |
| 文字聊天 | 四種協議、串流回覆、模型切換、對話管理 | 四種協議、串流回覆、模型切換、對話管理與匯出匯入 |
| VRM 與角色包 | 匯入、下載、快取、互動 | 匯入、下載、快取、互動 |
| 連線限制 | 可用 HTTPS，也允許區網 HTTP API | API 與模型來源需允許 CORS；HTTPS 頁面不能直接連 HTTP API |
| 本機資料 | 保存在 App 內 | 保存在目前瀏覽器；清除網站資料會移除本機紀錄與快取 |

聊天支援 OpenAI Chat Completions 相容、OpenAI Responses、Anthropic Messages、Gemini 四種協議。可查看能由供應商回傳的思考內容、token 用量和耗時，也能停止回覆、重新產生、編輯重送及調整每段對話的參數。不同 API 的支援程度可能不同。

餘額查詢也依供應商而定。有些 API 沒有可用的查詢介面，這時會顯示不支援、卡片上的參考值或上次結果；卡片上的數字不代表供應商的即時帳單。

## 卡片容量與外部託管

NFC 卡的空間有限。API 地址、長密鑰、模型清單、角色網址和備註一起寫入時，很容易超出容量。鯨幣提供兩種儲存方式：

| 方式 | 卡上放什麼 | 適合的情況 | 取捨 |
|---|---|---|---|
| 完整卡片 v1 | API key 與完整設定 | 設定較短，希望讀設定時不依賴託管站 | 資料越多，卡片需要的空間越大 |
| 託管卡片 v2 | 版本、卡片 ID、API key、設定網址 `ref` | 角色網址、備註等設定較長 | 首次讀取需要連網，公開設定也依賴託管站可用 |

完整模式不依賴設定託管，但聊天仍需連線到 API；尚未下載的形象也需要取得模型檔。

0.7.0 寫入的新卡只保留一筆 HTTPS URI，將正規化 JSON 經 Base64url 放在 `#card=` 後面，避免舊版「網址與 MIME 各存一份資料」的重複開銷。Base64url 是連結編碼，不是加密或壓縮；主要節省來自移除重複資料，以及把較大的設定搬到外部。

託管卡的邏輯內容如下，實際寫入時會正規化並編碼：

```json
{
  "v": 2,
  "id": "a1b2c3d4",
  "key": "僅供示意的卡片 API key",
  "ref": "https://raw.githubusercontent.com/sunfish1728/deepcoin-web-API/main/cards/example.json"
}
```

開啟外部託管後，App 會先把**不含 API key** 的設定上傳，再從公開網址下載並核對；確認成功才交給寫卡流程。每次上傳使用新檔名，不會在正常操作中覆寫舊卡的設定。

讀卡時會下載 `ref`，檢查版本、卡片 ID、內容大小及是否混入密鑰或另一層託管連結，再在本機補入卡上的 key。連線失敗時，只使用同一網址上次成功讀取並驗證的快取；第一次就讀取失敗，則停止套用。

以本專案的預設網址、一般設定網址及 64 字元 API key 為例，託管卡的 NDEF 約 **343 bytes**，不含標籤 TLV 開銷。密鑰與網址長度仍會影響容量，請以寫卡頁估算和實際晶片容量為準。

完整格式與相容規則見 [0.7.0 的 SPEC.md](https://github.com/sunfish1728/deepcoin/blob/v0.7.0/SPEC.md)。舊 MIME／AAR 卡仍可讀；v2 託管卡需要新版讀取端。自 0.6.1 起不再寫入或校驗卡片 SHA-256，舊卡的頂層 `sha` 會忽略並移除；角色資源下載的雜湊核對仍保留。

## 角色如何跟著卡片出現

角色可以是一個 VRM 0.x／1.0 模型，也可以是附有 `avatar.json` 的 ZIP 角色包。單一 VRM 會依標準骨架與表情推導基本行為；角色包則能進一步設定表情對應、台詞、待機與鏡頭。

舞台已有呼吸、眨眼、視線、觸控反應、轉身與縮放等互動。聊天時形象可顯示思考狀態、依文字串流動嘴，並在回覆完成後用關鍵詞推測情緒；目前動嘴是文字驅動的視覺效果，情緒也是簡單推測。

卡片可直接指定模型或角色包網址，也可指向帶有索引的 GitHub 形象庫，由 `char_id` 挑選角色。使用者替角色選過的形象會優先保留；下載後快取，讓再次刷卡不必重抓同一個模型。角色資源和公開卡片設定使用各自的快取，兩者的更新與連線狀態分開處理。

- [角色包與形象庫格式](讀卡工具/AVATAR.md)：資源結構、選擇順序、角色參數與索引格式。
- [建立自己的形象庫](形象倉庫/README.md)：建庫流程、範本與打包工具。
- [木子惡魔・鯨幣形象庫](https://github.com/sunfish1728/deepcoin-avatars)：DeepSeek 鯨魚娘、白紫龍娘、Claude 娘。

上述三個模型的作者為 **木子惡魔（木子恶魔）**，使用模型製作內容發布時請署名作者。模型本體放在形象庫的 Releases，本專案不附任何 VRM 模型；其他模型應依各自的授權與發布許可使用，不能把這三個模型的許可套用到別的模型上。

## 資料放在哪裡

鯨幣目前將 App、網頁、公開設定與模型分開維護：

| 倉庫 | 職責 |
|---|---|
| [deepcoin](https://github.com/sunfish1728/deepcoin) | Android 鯨幣、獨立寫卡工具、卡片與角色規格、形象庫工具；APK 由 Releases 提供 |
| [deepcoin-web](https://github.com/sunfish1728/deepcoin-web) | 網頁版的獨立原始碼與 GitHub Pages 部署 |
| [deepcoin-web-API](https://github.com/sunfish1728/deepcoin-web-API) | 公開卡片設定 JSON，供 App／網頁讀取；不轉送聊天請求 |
| [deepcoin-avatars](https://github.com/sunfish1728/deepcoin-avatars) | 形象索引、作者與許可說明，以及 Releases 中的角色包 |

GitHub Pages 只提供網頁靜態檔案。設定上傳則由 Android App 呼叫 GitHub API，在資料倉庫建立檔案；讀卡端再透過公開 raw 網址取得 JSON。因此靜態網頁和可新增的設定資料可以並存，不需要讓 Pages 執行後端程式。

### 明文密鑰與共用上傳授權

卡片 API key 採明文儲存，讀取卡片的人可以取得它。本機載入的卡片與寫卡草稿也包含密鑰；遮住畫面上的 key 不等於加密。請使用供應商端可限額、限時、可撤銷的密鑰，並把帶有卡片資料的連結視為包含密鑰的內容。

卡片資料放在 URL fragment，瀏覽器不會隨 HTTP 網頁請求把 fragment 傳給網站主機，但網頁程式可以讀取它。若自行填入別的網頁網址，該網站也能讀到卡上的密鑰，因此應使用自己信任的網頁。

託管模式只排除卡片的 API key，**其餘上傳設定都是公開資料**，包含名稱、地址、角色資訊和備註。聊天時，key 與訊息由使用端直接送往卡片指定的 API。

目前發布 APK 依專案使用者的決定，內建一份共用 GitHub 上傳 token，只授權 `deepcoin-web-API` 的檔案內容讀寫。這份 token 可以從 APK 擷取，取得的人也能修改或刪除該資料倉庫的內容；它不是「只有這個 App 才能使用」的授權。ID 與格式檢查只能檢查內容是否符合規格，不能證明公開設定沒有被他人改過。這是目前個人玩具採用的取捨。

共用 token 不放進 Git 原始碼或網頁。自行建置時，透過根目錄被忽略的 `hosting.properties` 注入；沒有設定 token 仍可讀卡和讀取公開託管設定，只有上傳功能不可用。

## 原始碼與建置

兩個 Android 工具是**各自獨立的 Gradle 專案**，不共用模組。卡片格式程式各保留一份，以 [SPEC.md](SPEC.md) 為共同規格；修改格式時，需要同步兩端與網頁的編解碼及測試。

| 路徑 | 內容 |
|---|---|
| [讀卡工具/](讀卡工具/) | Kotlin／Jetpack Compose 的 Android 鯨幣；含讀卡、聊天、形象與寫卡 |
| [讀卡工具/avatar-web/](讀卡工具/avatar-web/) | WebView 內的 VRM 渲染器原始碼 |
| [寫卡工具/](寫卡工具/) | 獨立 Android 寫卡工具，套件名 `com.tokencoin.writer`，資料與鯨幣分離 |
| [網頁版/](網頁版/) | 網頁移植版的歷史來源與同步副本；後續開發、部署以獨立 deepcoin-web 倉庫為準 |
| [形象倉庫/](形象倉庫/) | 形象庫範本與打包工具 |
| [SPEC.md](SPEC.md) | 卡片格式的權威規格 |
| [讀卡工具/AVATAR.md](讀卡工具/AVATAR.md) | 角色包與形象庫的權威規格 |

### Android

需要 **JDK 17、Android SDK 35**；App 最低支援 Android 8.0（API 26）。以下使用已發布的 0.7.0 原始碼：

```bash
git clone --branch v0.7.0 https://github.com/sunfish1728/deepcoin.git
cd deepcoin/讀卡工具
./gradlew testDebugUnitTest assembleDebug
```

Windows PowerShell 使用 `./gradlew.bat testDebugUnitTest assembleDebug`，並確認 `JAVA_HOME` 指向 JDK 17。在各 Android 專案的 `local.properties` 設定自己的 `sdk.dir`；APK 輸出於各自的 `app/build/outputs/apk/debug/`。

若要自行建置具有上傳功能的 APK，在儲存庫根目錄建立 `hosting.properties`：

```properties
token=你的專用 GitHub token
```

目前上傳程式的目標是 `sunfish1728/deepcoin-web-API`。若改用自己的資料倉庫，需同步調整兩個 Android 專案 `net/HostedCards.kt` 的 `REPO`；預設網站則在各自的 `card/WebCardLink.kt`。不要把授權檔加入版本庫。

修改 Android 的 VRM 渲染器後，在 `讀卡工具/avatar-web/` 執行 `npm install`、`npm run build`，重新產生 App 使用的渲染檔案，再建置 APK。

### 網頁

網頁獨立倉庫需要 Node.js 20.19+ 或 22.12+：

```bash
git clone https://github.com/sunfish1728/deepcoin-web.git
cd deepcoin-web
npm ci
npm test
npm run dev
```

`npm run build` 產生 `dist/`，可放到 HTTPS 靜態主機；獨立倉庫已有 GitHub Actions 的 Pages 部署流程。請經由開發伺服器或 HTTPS 開啟，不要直接雙擊 HTML。

## 目前進度與界線

已發布版本為 **鯨幣 0.7.0、獨立寫卡工具 0.4.0、網頁版 0.2.0**。0.7.0 主要加入可選外部託管、單筆 URI 寫卡與預設網頁入口；舊版卡片的讀取相容保留。

這次版本已通過鯨幣 78 項、獨立寫卡工具 17 項、網頁 20 項測試，並完成 Android／網頁建置、公開設定上傳與回讀、Pages 部署，以及瀏覽器與 Android API 34 模擬器的託管卡載入驗證。**尚未使用真實 API key、實體 NFC 卡與真機完成整條流程測試。** 網頁的跨來源 API／模型下載、不同手機的刷卡開啟行為，也需要依實際環境確認。

PC 端「刷卡後套用到改版 CC Switch」與專用桌面虛擬形象配套仍在規劃，尚未實作；目前能使用的是 Android 與網頁。RikkaHub 跳轉暫時擱置。

這個專案會繼續圍繞「一枚小玩具，刷出它的 AI 與角色」往前做。卡片、聊天與形象各自保留清楚的格式和接口，讓之後換模型、換角色或補上電腦端時，可以沿用同一張卡承載的設定。
