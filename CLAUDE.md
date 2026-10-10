# Token幣：核心記憶（全專案）

> 這份文件是所有開發對話共用的**單一事實來源**，只記錄全專案層級的資訊。
> 各工具自己的細節、開發紀錄和待辦，寫在各自資料夾的 `CLAUDE.md`：
> - [寫卡工具/CLAUDE.md](寫卡工具/CLAUDE.md)
> - [讀卡工具/CLAUDE.md](讀卡工具/CLAUDE.md)
>
> 卡片格式的權威規格在 [SPEC.md](SPEC.md)。

## 1. 專案是什麼

這是自己做的小玩具，會當成玩具的附帶品，例如「DeepSeek娘幣」附 1M token。

- NFC 卡上存 API 密鑰、地址、模型等資訊。
- **手機刷卡**：自動打開讀卡工具（Token幣 客戶端）。
- **電腦刷卡**：透過改版 CC Switch 套用設定。
- **日後**：刷卡時跳出這個 AI 的虛擬形象。

## 2. 使用者的指令與已定案決策（不要再重新提案）

| 日期 | 決策 / 指令 |
|---|---|
| 2026-10-09 | 是個人小玩具。**API 密鑰直接明文寫入卡片沒問題**，因為供應商本身可以限額、限時。不做中轉後端，不用 NTAG 424 DNA |
| 2026-10-09 | 先做 Android 寫卡工具。電腦端用**改 CC Switch** 的方式做。日後再做一個「刷卡後跳出 AI 虛擬形象」的專用配套軟體 |
| 2026-10-09 | 卡片要記錄：AI 模型供應商、預設模型、API 供應商名稱、API 地址、API 密鑰、SHA256，其他欄位盡量補完整 |
| 2026-10-10 | 建立核心記憶 md，讓多個對話可以平行開發 |
| 2026-10-10 | 做手機客戶端，並作為寫卡工具預設的 AAR。刷卡後自動跳出介面，底部三個分頁：主頁、跳轉、設定。主頁和設定先留空 |
| 2026-10-10 | **RikkaHub 跳轉先擱置** |
| 2026-10-10 | 虛擬形象欄位只留名稱、ID、URL。額度改成美元餘額。寫卡工具要能檢測模型列表和餘額 |
| 2026-10-10 | **寫卡工具和讀卡工具的資料夾、資料完全分離**：兩個獨立的 Gradle 專案，各自保有一份卡片格式程式碼，**不共用模組**，以 SPEC.md 作為共同規格 |
| 2026-10-10 | 讀卡工具主頁偏底部放「聊天」按鈕，上方大片空間預留給 MMD（之後做）。點進去是完整的聊天系統，可參考或改用開源程式（最後選擇自己寫，參考 RikkaHub 的功能範圍） |
| 2026-10-10 | 介面風格參考 `前端風格md/MaiBot-UI-Style-Guide.zh-TW.md`，**參考但不照抄，直角改成微弧角** |
| 2026-10-10 | 分工：**可以叫 Codex 處理後端和雜事；規劃、審查、前端由 Claude 執行**。可以反覆迭代 |
| 2026-10-10 | 讀卡工具的「跳轉」分頁改為「寫卡」，**把寫卡工具的功能移植進讀卡工具**（複製程式碼，不共用模組；寫卡工具專案保留）。主頁顯示卡片餘額；設定頁加入卡片詳細資訊等設定 |
| 2026-10-10 | 讀卡工具 App **改名「鯨幣」**，字標去掉彩色階梯（套件名 com.tokencoin.client 不變） |
| 2026-10-10 | 卡片「其他可用模型」可設為 **API 的所有模型**：卡上記 `models: ["*"]`，讀卡時自動檢測（SPEC.md） |
| 2026-10-10 | 專案推上 GitHub **公開**倉庫 `deepcoin`（帳號 sunfish1728）。VRM 形象放在**使用者自建的公開 GitHub 倉庫**（形象庫），App 能抓任何符合格式的倉庫；格式見 讀卡工具/AVATAR.md §6，流程與工具見 形象倉庫/ |
| 2026-10-10 | 有 URL 的 VRM：刷卡時自動下載並「跳出」、快取，下次刷同一張卡跳出同一個 |
| 2026-10-10 | 開始做主頁的形象互動（使用者稱 MMD），用示範 VRM 模型測試，要**公式化、模組化**；後端同樣交給 Codex。最後做成 VRM 渲染器 + 角色包格式（讀卡工具/AVATAR.md） |

使用者偏好：
- 用**繁體中文**溝通。
- 先討論方案再動手；改動範圍以使用者交代的為準。

## 3. 目錄結構

```
Token幣/
├─ CLAUDE.md        ← 本檔（全專案核心記憶）
├─ SPEC.md          ← 卡片格式規格 v1（權威）
├─ 寫卡工具/         ← 獨立 Gradle 專案，App ID com.tokencoin.writer
│   ├─ CLAUDE.md    ← 寫卡工具的開發紀錄 / 待辦
│   ├─ app/
│   └─ dist/        ← 打包好的 APK
├─ 讀卡工具/         ← 獨立 Gradle 專案，App ID com.tokencoin.client（App 顯示名稱「鯨幣」）
│   ├─ CLAUDE.md    ← 讀卡工具的開發紀錄 / 待辦
│   ├─ AVATAR.md    ← 角色包格式 + 形象庫格式（權威）
│   ├─ avatar-web/  ← VRM 渲染器原始碼
│   ├─ app/
│   └─ dist/
└─ 形象倉庫/         ← 自建形象庫的流程說明、範本與 deepcoin_avatar.py 工具
```

GitHub：https://github.com/sunfish1728/deepcoin （公開）。
- `build/`、`.gradle/`、`local.properties`、`node_modules/`、`dist/*.apk`、`*.vrm` 不進 git（見 `.gitignore`）。APK 要給人下載的話放 GitHub Releases。
- **任何模型檔都不要提交**（示範模型禁止再配布）。

日後的新元件也比照辦理，放在獨立資料夾，例如 `PC端/`、`虛擬形象/`。

## 4. 元件狀態總覽

| 元件 | 狀態 | 詳見 |
|---|---|---|
| 卡片格式 v1 | ✅ 完成 | SPEC.md |
| 寫卡工具 | ✅ 可編譯，⚠ 未真機測試 | 寫卡工具/CLAUDE.md |
| 讀卡工具（鯨幣 客戶端） | ✅ 0.5.0：聊天系統 + VRM 虛擬形象互動 + 寫卡 + 即時餘額 + 設定頁 + 形象庫 / 刷卡跳出（模擬器驗證，⚠ 未用真實密鑰、真卡、真機測試） | 讀卡工具/CLAUDE.md |
| 角色包格式 v1 | ✅ 完成 | 讀卡工具/AVATAR.md |
| 形象庫格式 v1（GitHub） | ✅ 規格 + 工具完成，⚠ 還沒有真的公開形象庫（缺允許再配布的模型） | 讀卡工具/AVATAR.md §6、形象倉庫/README.md |
| RikkaHub 跳轉 | ⏸ 擱置 | 讀卡工具/CLAUDE.md §研究筆記 |
| PC 端：改 CC Switch | ⬜ 未開始 | 需要 USB NFC 讀卡機（型號未定，要問使用者） |
| 虛擬形象配套軟體（PC） | ⬜ 未開始 | 手機端已用卡上的 `char_id` / `avatar`；PC 端可沿用 avatar-web 渲染器與角色包格式 |

## 5. 卡片格式的同步規則（重要）

兩個工具各有一份卡片格式程式碼，位置是 `app/src/main/java/com/tokencoin/{writer|client}/card/TokenCard.kt`，兩份內容應該一致，只差在 package 名稱。
讀卡工具 0.4.0 起也有寫卡功能，`card/Presets.kt`、`net/ApiProbe.kt`、`nfc/NfcIo.kt` 的寫入部分和寫卡表單（`writer/WriterState.kt` 的 CardForm）都是從寫卡工具複製的，改其中一邊時記得評估另一邊要不要跟著改。

只要改了卡片格式：
1. 先改 SPEC.md。
2. **兩個工具都要改**，各自的 `TokenCardCodecTest` 也要改，兩邊都要跑測試。
3. 在本檔 §7 記錄。

## 6. 建置環境（Windows，兩個專案共通）

- 使用 Gradle 下載的 JDK 17：`~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`
  - 系統預設的 `java` 是 1.8，不能用。
  - Android Studio 附的 jbr 已損壞。
- 版本組合：Gradle 8.14.3、AGP 8.9.2、Kotlin 2.0.21、Compose BOM 2024.12.01、compileSdk 35、minSdk 26。
- 路徑含中文，所以 `gradle.properties` 有 `android.overridePathCheck=true`。
- `local.properties` 的 `sdk.dir` 要用正斜線：`C:/Users/hhjju/AppData/Local/Android/Sdk`。
- 偶爾會出現 `FileLockContentionHandler ... BindException`，重跑一次就好。

建置指令（在 Git Bash 中執行，把 `<工具資料夾>` 換成實際資料夾名稱）：

```bash
cd <工具資料夾> && JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2" ./gradlew testDebugUnitTest assembleDebug
```

## 7. 全專案開發紀錄

- **2026-10-09**：討論方案，經歷三版：純 URI → 424 DNA 加中轉後端 → 定案明文寫卡。定好卡片格式 v1（MIME JSON + SHA-256）。
- **2026-10-10**：
  - 卡片格式移除 `quota`/`theme`/`persona`，新增 `balance_usd`（字串）和 `char_id`。
  - 建立核心記憶。
  - 先做成多模組專案，後來依使用者指示**拆成 `寫卡工具/`、`讀卡工具/` 兩個完全獨立的專案**，刪除舊的 `android/` 資料夾。
  - 讀卡工具 0.2.0：完整聊天系統（Codex 寫後端，Claude 寫前端並審查）＋ MaiBot 風格介面，詳見讀卡工具/CLAUDE.md。
  - 讀卡工具 0.3.0：VRM 虛擬形象（Codex 寫 avatar/ 後端，Claude 寫契約、渲染器與前端並審查），新增角色包格式 讀卡工具/AVATAR.md。
  - 讀卡工具 0.4.0：跳轉分頁改為寫卡（移植寫卡工具）、主頁即時餘額、設定頁。
  - 讀卡工具 0.5.0：改名鯨幣、models `*`（SPEC 補充）、形象庫格式與 形象倉庫/ 工具、刷卡跳出形象；推上 GitHub `sunfish1728/deepcoin`。
  - Codex 的叫用方式：子代理 `codex:codex-rescue` 這次卡住、沒有產生任何工作。改為直接執行 `node <plugin>/scripts/codex-companion.mjs task --write "<prompt>"`，在背景跑，成功。Codex 的沙箱無法寫入 `~/.gradle`，會把快取複製到專案的 build 資料夾自建（可能好幾 GB），事後要停掉它的 Gradle daemon 再刪除。

## 8. 平行開發規則

- 開工前，先在 §4 把要做的元件標成「🔧 進行中（對話主題）」。
- 一個對話只動自己負責的工具資料夾。改卡片格式的話，依 §5 處理。
- 完成後，更新該工具的 `CLAUDE.md`；有全專案層級的變更，也要更新本檔 §4 和 §7。
