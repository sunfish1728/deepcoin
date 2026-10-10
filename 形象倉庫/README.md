# 鯨幣形象庫：建立與發布流程

鯨幣 App 刷卡時會讓角色的 VRM 形象「跳出來」。形象放在**你自己的公開 GitHub 倉庫**裡，
任何符合格式的倉庫都能用，App 的「線上形象庫」也搜得到。格式規格見 [讀卡工具/AVATAR.md §6](../讀卡工具/AVATAR.md)。

本資料夾：

| 檔案 | 用途 |
|---|---|
| `deepcoin_avatar.py` | 工具：檢查 VRM、打包角色包、更新索引、發布到 GitHub Releases（只用 Python 標準函式庫） |
| `template/deepcoin-avatars.json` | 空白索引範本 |
| `template/README.md` | 形象庫倉庫的 README 範本 |

## 需要準備

- 一個 VRM 0.x / 1.0 模型，**授權必須允許再配布**（VRoid Studio 匯出時可以在授權設定裡選）。
- Python 3.8 以上、git、[GitHub CLI](https://cli.github.com/)（`gh auth login` 登入過）。

## 流程

### 1. 建倉庫（只做一次）

```bash
gh repo create my-deepcoin-avatars --public --clone
cd my-deepcoin-avatars
python <本資料夾>/deepcoin_avatar.py init --name "我的形象庫" --description "角色介紹"
git add -A && git commit -m "建立形象庫" && git push -u origin HEAD
```

`init` 會建立 `deepcoin-avatars.json`、README，並幫倉庫加上 topic `deepcoin-avatar`。

### 2. 檢查模型

```bash
python <本資料夾>/deepcoin_avatar.py check model.vrm
```

會列出 VRM 版本、名稱、作者、授權、是否允許再配布。**顯示「再配布：禁止」或「未註明」的模型不能放到公開形象庫。**

### 3. 打包

```bash
python <本資料夾>/deepcoin_avatar.py pack model.vrm --char-id whale --name 鯨魚娘 --version 1.0.0
```

- 產生 `dist/whale-v1.0.0.zip`（`avatar.json` + `model.vrm` + 縮圖），縮圖預設從 VRM 抽出，另存一份到 `thumbs/whale.png`。
- 在 `deepcoin-avatars.json` 加入（或更新）這個角色，`file` 指向 Releases 的下載網址，並寫好 `sha256` 與 `size`。
- 想客製表情對應、台詞、鏡頭：加 `--avatar-json my.json`（欄位見 AVATAR.md §3）。
- `char_id` 只能用英數字、`_`、`.`、`-`。之後寫卡時「角色 ID」就填它。

### 4. 發布

```bash
python <本資料夾>/deepcoin_avatar.py publish whale
```

建立 Release `whale-v1.0.0`、上傳角色包，再提交並推送索引與縮圖。

### 5. 寫卡

鯨幣 App →「寫卡」→「虛擬形象」→「從形象庫選擇」，貼上倉庫網址（或搜尋），選角色，會自動填好：

- 角色名稱：鯨魚娘
- 角色 ID：whale
- 形象 URL：`https://github.com/<你>/my-deepcoin-avatars`

寫入卡片後，第一次刷卡會下載並快取，之後每次刷卡都直接跳出同一個形象。

## 更新角色

模型改了就**換新版本**重新打包、發布（例如 `--version 1.1.0`），舊版的 Release 留著不要刪，已經寫好的卡片不會壞。
已經下載過舊版的手機不會自動更新；要換新版，在 App 的形象清單刪掉舊的再刷卡。

## 規則與限制

- 倉庫必須公開，App 才下載得到。
- 角色包上限 300 MB（App 的下載上限）。50 MB 以下也可以用 `pack --host repo` 直接放進倉庫的 `files/`，但**不要用 Git LFS**。
- 只放你有權散布的模型；`pack` 會擋下授權不允許再配布的 VRM。`--private` 只給自己本機測試用，打出來的包不可公開。
