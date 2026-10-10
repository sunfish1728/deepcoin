# Token幣 卡片格式 v1

寫卡工具（Android）、Token幣 客戶端（Android）、CC Switch 改版（PC）、日後的虛擬形象配套軟體，全部依此規格讀寫。

## NDEF 結構

| 順序 | 記錄 | 說明 |
|---|---|---|
| 1 | MIME `application/vnd.tokencoin+json` | 卡片資料（UTF-8 JSON） |
| 2（預設附加） | Android App Record（AAR） | 預設 `com.tokencoin.client`（Token幣 客戶端），Android 刷卡時直接開啟該 App（未安裝時 Android 會開 Play 商店，但本 App 不在商店上） |

讀取端應依 MIME 類型尋找記錄，不要假設它在第幾筆。

## 欄位

| key | 型別 | 必填 | 說明 |
|---|---|---|---|
| `v` | int | ✓ | 格式版本，目前為 `1` |
| `id` | string | ✓ | 卡片 ID，8 位十六進位 |
| `title` | string | | 卡片名稱，例如「DeepSeek娘幣」 |
| `vendor` | string | ✓ | AI 模型供應商（模型原廠）：`deepseek` `openai` `anthropic` `google` `moonshot` `qwen` `zhipu` `minimax` `xai` `mistral` `meta` `other` 或自訂 |
| `model` | string | ✓ | 預設模型 |
| `models` | string[] | | 其他可用模型。只有一項 `"*"` 時表示「使用 API 的所有模型」：讀取端自行向 API 查模型清單（2026-10-10 新增，`"models":["*"]` 只佔 15 bytes） |
| `provider` | string | ✓ | API 供應商名稱 |
| `proto` | string | ✓ | API 協議：`openai`（Chat Completions 相容）、`openai-responses`、`anthropic`（Messages）、`gemini` |
| `base` | string | ✓ | API 地址（Base URL），結尾不含 `/` |
| `key` | string | ✓ | API 密鑰 |
| `apps` | string[] | | 電腦端建議套用的工具：`claude`（Claude Code）、`codex`、`gemini`（Gemini CLI） |
| `iat` | int | ✓ | 寫入時間（Unix 秒） |
| `exp` | int | | 到期時間（Unix 秒），只用於顯示和提醒 |
| `balance_usd` | string | | 美元餘額，十進位字串（例如 `"5.00"`），寫卡當下的參考值。用字串是為了避免浮點數在不同語言序列化結果不同，導致 SHA 不一致 |
| `char` | string | | 虛擬形象：角色名稱 |
| `char_id` | string | | 虛擬形象：角色 ID |
| `avatar` | string | | 虛擬形象：`.vrm` / `.zip` 網址，或形象庫網址（GitHub 倉庫或 `deepcoin-avatars.json`，依 `char_id` 挑角色）。格式見 讀卡工具/AVATAR.md §6 |
| `note` | string | | 備註 |
| `sha` | string | ✓ | 完整性校驗，計算方式見下節 |

- 值為空的欄位直接省略。
- 讀取端遇到未知欄位要原樣保留，並納入 SHA 驗證。
- 擴充欄位建議加 `x_` 前綴。
- `models` 的 `"*"` 不影響編解碼與 SHA，只是約定：不認得的讀取端會把它當成一個名叫 `*` 的模型，應該過濾掉。
- 舊版欄位 `quota`、`theme`、`persona` 已在 2026-10-10 移除；舊卡片讀取時會被當成未知欄位保留。

## 正規化 JSON 與 SHA-256

1. 取出除了 `sha` 以外的所有欄位。
2. 序列化成正規化 JSON：
   - key 依字典序排列（遞迴套用）
   - 不加任何空白
   - 字串只跳脫 `"`、`\` 與控制字元（`\b \f \n \r \t`，其他用 `\u00xx` 小寫）
   - 非 ASCII 字元和 `/` 原樣輸出
   - 數字一律是整數
3. `sha` = 對上述字串的 UTF-8 位元組做 SHA-256，輸出小寫十六進位。
4. 卡片上存的是加入 `sha` 後，**再次正規化**的整份 JSON。

參考實作（Python）：

```python
import json, hashlib
d = json.loads(payload); sha = d.pop("sha")
canon = json.dumps(d, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
assert hashlib.sha256(canon.encode()).hexdigest() == sha
```

> SHA-256 只用來檢查資料是否損毀或被誤改，**不是**防偽或加密：任何人都能重新計算。卡上的 API 密鑰是明文，任何人刷卡都讀得到，請用有限額、有期限的密鑰。

## 容量參考

| 晶片 | 可用 NDEF 容量（約） |
|---|---|
| NTAG213 | 137 bytes（通常不夠） |
| NTAG215 | 496 bytes |
| NTAG216 | 868 bytes（建議） |
