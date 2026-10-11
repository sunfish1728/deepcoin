# 寫卡工具（com.tokencoin.writer）

> 全專案資訊見上層的 [../CLAUDE.md](../CLAUDE.md)，卡片格式見 [../SPEC.md](../SPEC.md)。本檔只記錄寫卡工具本身。

> 2026-10-10：讀卡工具 0.4.0 已把本工具的功能移植為「寫卡」分頁（複製程式碼，不共用模組）。本專案保留；改這裡的表單、預設清單或 ApiProbe 時，評估讀卡工具要不要跟著改。

## 功能

App 有三個分頁：

- **寫卡**
  - 表單欄位：
    - 卡片：ID、名稱
    - 模型：AI 模型供應商、預設模型、其他可用模型
    - API：供應商名稱、協議、地址、密鑰
    - 餘額與期限：到期日、美元餘額
    - 電腦端要套用的工具
    - 虛擬形象：名稱、ID、URL
    - 備註
    - AAR 開關：預設開啟，套件名 `com.tokencoin.client`
  - 寫入後回讀驗證；可選擇寫入後永久鎖定，會二次確認。
  - 即時估算資料大小，並顯示 NTAG213 / 215 / 216 是否放得下。
  - **檢測模型列表與餘額**（`net/ApiProbe.kt` + `ui/ProbeDialog.kt`）：單選預設模型、多選其他模型；美元餘額和到期日可以一鍵填入。
- **讀卡**：在任何分頁刷卡都會跳到這裡。顯示欄位、驗證 SHA-256、密鑰預設遮住、可複製 JSON、可載入回寫卡頁。
- **設定檔**：本機用明文保存常用表單（SharedPreferences `tokencoin`），載入時會換新的卡片 ID。

## 程式結構（app/src/main/java/com/tokencoin/writer/）

| 路徑 | 內容 |
|---|---|
| `card/TokenCard.kt` | 卡片格式編解碼與 SHA。**跟讀卡工具各有一份，要保持一致** |
| `card/Presets.kt` | 模型供應商、API 供應商、協議預設清單；預設 AAR；各晶片容量 |
| `nfc/NfcIo.kt` | NFC 讀寫、格式化、鎖定 |
| `net/ApiProbe.kt` | 模型列表與餘額查詢 |
| `ui/` | AppState（表單、設定檔）、WriteScreen、ReadScreen、ProfilesScreen、ProbeDialog、AppScreen |
| `MainActivity.kt` | NFC reader mode：有待寫任務就寫卡，沒有就讀卡 |

## 餘額查詢介面（ApiProbe）

- DeepSeek：`/user/balance`
- OpenRouter：`/api/v1/key`，失敗時改用 `/api/v1/credits`
- SiliconFlow：`/v1/user/info`
- Moonshot：`/v1/users/me/balance`
- 其他網域一律當成 new-api / one-api 中轉站：`{origin}/v1/dashboard/billing/subscription` 和 `/usage`，以密鑰為單位計算剩餘美元，`access_until` 當到期日。
- 模型列表的查法依協議而定：
  - openai：`/models`，失敗時再試 `/v1/models`
  - anthropic：`/v1/models`
  - gemini：`/v1beta/models`
- ⚠ 以上都**還沒有用真實密鑰驗證過**。

## 開發紀錄

- **2026-10-11，0.3.0**：與鯨幣同步網頁回退寫卡功能。
  - 新增可設定「網頁版網址」，保存在草稿／設定檔；空白沿用 AAR。
  - 有網址時 URI 放第一筆，`#card=` 帶完整正規化 JSON 的 Base64url，保留 MIME、不附 AAR。
  - 容量估算、一般寫入與永久鎖定皆帶同一網址；卡片 JSON 沒有新增欄位。
  - `card/WebCardLink.kt` 和連結測試與鯨幣保持一致；新增表單相容性測試。
  - 總共 11 個單元測試通過，assembleDebug 成功；真卡與未安裝 App 的跳轉待驗證。
  - APK：`dist/Token幣寫卡工具-0.3.0-debug.apk`。

- **2026-10-09**：0.1.0 初版：寫卡、讀卡、設定檔。
- **2026-10-10**：0.2.0。
  - 新增模型和餘額檢測。
  - 額度改成美元餘額。
  - 虛擬形象只留名稱、ID、URL。
  - 預設 AAR 改成讀卡工具。
  - 從多模組專案拆出來成為獨立專案。
  - APK：`dist/Token幣寫卡工具-0.2.0.apk`

## 待辦

- [ ] 真機和真卡測試
- [ ] 用真實密鑰驗證 ApiProbe
- [ ] 預設清單裡的模型名稱可能過時，需要更新
