# 鯨幣（deepcoin）

把 AI 的 API 密鑰寫進 NFC 卡，當成玩具的附帶品：手機一刷，就打開聊天 App，角色的 VRM 形象跳出來打招呼。

| 資料夾 | 內容 |
|---|---|
| [讀卡工具/](讀卡工具/) | Android App「鯨幣」：刷卡讀取、聊天（OpenAI / Anthropic / Gemini 等協議）、VRM 虛擬形象、寫卡、即時餘額 |
| [寫卡工具/](寫卡工具/) | 獨立的 Android 寫卡工具（功能已併入鯨幣的「寫卡」分頁） |
| [形象倉庫/](形象倉庫/) | 自建 GitHub 形象庫的流程、範本與打包工具 |
| [SPEC.md](SPEC.md) | NFC 卡片格式 v1 |
| [讀卡工具/AVATAR.md](讀卡工具/AVATAR.md) | 角色包與形象庫格式 |

## 建置

需要 JDK 17 與 Android SDK（compileSdk 35）。

```bash
cd 讀卡工具 && ./gradlew assembleDebug
```

VRM 渲染器（`讀卡工具/avatar-web/`）改過之後要重新打包：`npm install && npm run build`。

## 注意

- 卡片上的 API 密鑰是明文，請使用供應商可限額、限時的密鑰。
- 本倉庫不含任何 VRM 模型。形象請放在你自己的公開形象庫，且模型授權必須允許再配布。
