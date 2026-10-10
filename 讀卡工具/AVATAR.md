# 角色包格式 v1（鯨幣 虛擬形象）

> 讀卡工具用來顯示「刷卡後跳出的 AI 虛擬形象」。目標是**公式化**：任何標準 VRM 模型不用設定就能動，
> 想客製再加一份 `avatar.json`。契約程式碼見 `app/src/main/java/com/tokencoin/client/avatar/AvatarApi.kt`。

## 1. 兩種形式

| 形式 | 內容 | 適用 |
|---|---|---|
| 單一模型 | 一個 `.vrm`（或 `.glb` 且含 VRM 擴充），VRM 0.x / 1.0 皆可 | 直接拿現成模型 |
| 角色包 | `.zip`，根目錄（或唯一的子資料夾）有 `avatar.json` 與模型檔，可附縮圖、動作檔 | 要客製表情對應、台詞、鏡頭 |

只有單一模型時，所有行為都從 VRM 標準推導：
- 表情用 VRM 預設表情（happy / angry / sad / surprised / relaxed / blink / aa…oh / lookUp…）。
- 視線用 VRM lookAt；頭髮裙擺用 VRM springBone。
- 鏡頭位置由骨架（head / chest / hips / feet）自動算出，不需要知道模型身高。
- 互動區域（摸頭、戳身體、牽手）依骨骼在畫面上的投影位置判斷。

## 2. 取得方式（決定目前形象的規則）

1. 卡片有 `char_id`，且使用者曾為此 `char_id` 選過形象 → 用它。
2. 卡片有 `char_id`，且已安裝形象的 char_id 相同 → 最新安裝的那個。
3. 卡片有 `avatar` URL → 已下載過就用，沒有就自動下載安裝。URL 可以是：
   - 直接指向 `.vrm` / `.zip` 的網址；
   - **形象庫**的 GitHub 倉庫網址（例如 `https://github.com/<owner>/<repo>`），App 讀倉庫的索引、依卡片 `char_id` 挑角色（見 §6）。
4. 使用者最後手動選擇的形象。
5. 任一已安裝形象。

手動匯入：主頁舞台右上角「形象」→ 匯入 `.vrm` / `.zip`，或「線上形象庫」搜尋 GitHub 上的形象庫。

刷卡時（0.5.0 起）：
- App 會回到主頁，形象從舞台下緣「跳出來」並打招呼（`greeting`）。
- 第一次刷某張卡會下載形象（中斷會自動續傳，最多 3 次），之後**快取在手機上**，再刷同一張卡直接用快取、不連網。
- 快取以「來源」為鍵：直接網址就是網址本身；形象庫是「倉庫網址#char_id」。所以同一個形象庫的不同角色不會混用。
- 形象庫更新了角色（新版本），已下載過的手機**不會自動更新**；要換新版本，在形象清單刪掉舊的再刷卡。

## 3. avatar.json（全部欄位都可省略）

```json
{
  "v": 1,
  "name": "DeepSeek 鯨魚娘",
  "char_id": "deepseek-whale",
  "model": "model.vrm",
  "thumb": "thumb.png",
  "credit": "模型：木子恶魔 / 模之屋",

  "emotions": {
    "joy": "happy", "angry": "angry", "sad": "sad",
    "surprised": "surprised", "relaxed": "relaxed",
    "thinking": { "lookUp": 0.4, "relaxed": 0.2 }
  },
  "pose": { "arm": 72, "elbow": 12 },
  "idle": { "breath": 1, "sway": 1, "blink": [2.5, 6] },
  "camera": { "full": 1, "bust": 1, "face": 1, "yaw": 0 },

  "greeting": { "gesture": "wave", "emotion": "joy", "lines": ["你好呀！"] },
  "reactions": {
    "head": { "gesture": "tilt", "emotion": "joy",  "lines": ["嘿嘿", "再摸一下"] },
    "body": { "gesture": "flinch", "emotion": "surprised", "lines": ["呀！"] },
    "hand": { "gesture": "wave", "emotion": "joy", "lines": [] },
    "leg":  { "gesture": "shake", "emotion": "angry", "lines": ["別鬧"] },
    "pet":  { "gesture": "nod", "emotion": "relaxed", "lines": ["好舒服～"] },
    "annoyed": { "gesture": "shake", "emotion": "angry", "lines": ["夠了喔！"] }
  }
}
```

| 欄位 | 說明 | 讀取方 |
|---|---|---|
| `v` | 格式版本，目前 1 | 後端 |
| `name` | 顯示名稱；省略時用 VRM meta 名稱 | 後端 |
| `char_id` | 對應卡片的 `char_id` | 後端 |
| `model` | 模型檔路徑；省略時取包內唯一的 `.vrm` / `.glb` | 後端 |
| `thumb` | 縮圖路徑；省略時抽出 VRM 內嵌縮圖 | 後端 |
| `credit` | 顯示在形象清單的出處 | 前端 |
| `emotions` | 情緒 → 表情。值可為表情名稱字串，或「表情名稱 → 權重」物件；`null` 表示不做表情 | 渲染器 |
| `pose` | 待機姿勢：手臂下垂角度、手肘彎曲角度（度） | 渲染器 |
| `idle` | 呼吸、搖擺強度（0 關閉，1 預設），眨眼間隔秒數範圍 | 渲染器 |
| `camera` | 三種鏡頭的縮放倍率（1 = 自動）；`yaw` 為初始轉向角度 | 渲染器 |
| `greeting` | 載入完成時的招呼 | 渲染器 |
| `reactions` | 互動反應：`head` 點頭、`body` 戳身體、`hand` 點手、`leg` 點腳、`pet` 在頭上來回摸、`annoyed` 短時間被戳太多次 | 渲染器 |

情緒名稱：`neutral`、`joy`、`angry`、`sad`、`surprised`、`relaxed`、`thinking`。

動作（gesture）：`wave` 揮手、`nod` 點頭、`shake` 搖頭、`tilt` 歪頭、`flinch` 縮一下、`hop` 小跳、`bow` 鞠躬。
動作以骨骼關鍵影格的資料表定義在渲染器內（`avatar-web/src/gestures.js`），新增動作只要加一筆資料。

## 4. 聊天連動

- 助手開始回覆：`thinking`（有思考內容時）→ 串流文字時嘴型依字元變化。
- 回覆完成：依回覆文字推測情緒（關鍵字、表情符號、顏文字），表情維持數秒後回到待機。
- 送出失敗：`sad`。

## 5. 授權注意

VRM 檔內有作者設定的授權（meta）。形象清單會顯示授權摘要。
**不要把別人的模型打包進 APK 或公開散布**；示範模型（蓝色小肥鱼 Deepseek鲸鱼娘，作者木子恶魔）
禁止再配布與商業使用，只能自己匯入測試。

## 6. 形象庫格式 v1（GitHub）

任何人都可以建自己的形象庫：一個**公開**的 GitHub 倉庫，根目錄放 `deepcoin-avatars.json`，
並加上 topic `deepcoin-avatar`（App 的「線上形象庫」靠這個 topic 搜尋）。
建立、打包、發布的完整流程與工具在專案的 [形象倉庫/README.md](../形象倉庫/README.md)。

### 6.1 倉庫結構

```
<你的形象庫>/
├─ deepcoin-avatars.json   ← 索引（必要）
├─ README.md               ← 介紹、授權說明
└─ thumbs/<char_id>.png    ← 縮圖（建議，App 列表顯示用）
```

角色包本體（`<char_id>-v<version>.zip`）放在 **GitHub Releases**，一個版本一個 tag（`<char_id>-v<version>`），
下載網址固定不變。50 MB 以下也可以直接放進倉庫的 `files/`，但**不要用 Git LFS**（App 抓到的會是 LFS 指標檔）。

### 6.2 deepcoin-avatars.json

```json
{
  "format": "deepcoin-avatars/1",
  "name": "鯨魚娘形象庫",
  "description": "說明文字",
  "avatars": [
    {
      "char_id": "whale",
      "name": "鯨魚娘",
      "version": "1.0.0",
      "file": "https://github.com/<owner>/<repo>/releases/download/whale-v1.0.0/whale-v1.0.0.zip",
      "sha256": "64 個十六進位字元",
      "size": 16813707,
      "thumb": "thumbs/whale.png",
      "vrm": "1.0",
      "authors": ["作者"],
      "license": "授權摘要或網址",
      "description": "角色介紹"
    }
  ]
}
```

| 欄位 | 必要 | 說明 |
|---|---|---|
| `format` | ✔ | 固定 `deepcoin-avatars/1`，不符 App 會拒絕 |
| `name`、`description` | | 形象庫名稱與說明 |
| `avatars[].char_id` | ✔ | 英數字、`_`、`.`、`-`，最多 64 字；同一庫內不可重複（重複的只取第一個）。寫卡時的「角色 ID」 |
| `avatars[].name` | ✔ | 顯示名稱 |
| `avatars[].file` | ✔ | 角色包或 `.vrm` 的網址；可寫相對路徑（相對倉庫根目錄，不可有 `..`） |
| `avatars[].version` | | 建議用語意化版本；換模型就換版本與 tag |
| `avatars[].sha256` | | 有填的話 App 下載後會驗證，不符就不安裝 |
| `avatars[].size` | | bytes；超過 300 MB App 不下載 |
| `avatars[].thumb` | | 縮圖網址或相對路徑（2 MB 以下） |
| `vrm`、`authors`、`license`、`description` | | 列表顯示用 |

角色包 zip 的內容就是 §1 的「角色包」：根目錄 `avatar.json`（`model` 指向 `model.vrm`）、`model.vrm`、`thumb.png`。

### 6.3 卡片上的寫法

| 卡片 `avatar` | 卡片 `char_id` | App 的行為 |
|---|---|---|
| `https://github.com/<owner>/<repo>` | `whale` | 讀 `raw.githubusercontent.com/<owner>/<repo>/HEAD/deepcoin-avatars.json`，挑 `whale` |
| `https://github.com/<owner>/<repo>#whale` | 任意 | `#` 指定的角色優先 |
| `https://github.com/<owner>/<repo>/tree/<分支或 tag>` | `whale` | 讀指定分支 / tag 的索引 |
| `https://<任何主機>/.../deepcoin-avatars.json` | `whale` | 自架的形象庫（不必是 GitHub） |
| `https://.../x.vrm` 或 `.zip` | 任意 | 直接下載（舊寫法） |

寫卡頁的「從形象庫選擇」會自動填好角色名稱、角色 ID 與倉庫網址。卡上寫倉庫網址比寫 Releases 下載網址短很多，NTAG215 比較放得下。

### 6.4 授權

公開形象庫裡的模型**必須允許再配布**。打包工具會讀 VRM 的授權欄位，不允許（或沒註明允許）就拒絕打包。
