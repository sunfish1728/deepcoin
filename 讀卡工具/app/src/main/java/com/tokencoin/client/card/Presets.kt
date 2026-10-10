package com.tokencoin.client.card

/** AI 模型供應商（模型原廠）與建議預設模型。皆可在表單中自行改寫。 */
data class VendorPreset(val id: String, val label: String, val models: List<String>)

/** API 供應商（實際提供端點的服務）。 */
data class ProviderPreset(
    val label: String,
    val name: String,
    val base: String,
    val proto: String,
)

object Presets {
    val vendors = listOf(
        VendorPreset("deepseek", "DeepSeek", listOf("deepseek-chat", "deepseek-reasoner")),
        VendorPreset("openai", "OpenAI", listOf("gpt-5", "gpt-5-mini")),
        VendorPreset("anthropic", "Anthropic", listOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5")),
        VendorPreset("google", "Google Gemini", listOf("gemini-2.5-pro", "gemini-2.5-flash")),
        VendorPreset("moonshot", "Moonshot Kimi", listOf("kimi-k2-0905-preview", "kimi-latest")),
        VendorPreset("qwen", "阿里 Qwen", listOf("qwen-plus", "qwen-max", "qwen3-coder-plus")),
        VendorPreset("zhipu", "智譜 GLM", listOf("glm-4.6", "glm-4.5-air")),
        VendorPreset("minimax", "MiniMax", listOf("MiniMax-M2")),
        VendorPreset("xai", "xAI Grok", listOf("grok-4")),
        VendorPreset("mistral", "Mistral", listOf("mistral-large-latest")),
        VendorPreset("meta", "Meta Llama", listOf("llama-4-maverick")),
        VendorPreset("other", "其他", emptyList()),
    )

    val providers = listOf(
        ProviderPreset("DeepSeek 官方 (OpenAI)", "DeepSeek 官方", "https://api.deepseek.com", "openai"),
        ProviderPreset("DeepSeek 官方 (Anthropic)", "DeepSeek 官方", "https://api.deepseek.com/anthropic", "anthropic"),
        ProviderPreset("OpenAI 官方", "OpenAI", "https://api.openai.com/v1", "openai"),
        ProviderPreset("Anthropic 官方", "Anthropic", "https://api.anthropic.com", "anthropic"),
        ProviderPreset("Google AI Studio", "Google AI Studio", "https://generativelanguage.googleapis.com", "gemini"),
        ProviderPreset("Moonshot 官方", "Moonshot", "https://api.moonshot.cn/v1", "openai"),
        ProviderPreset("阿里雲百煉", "阿里雲百煉", "https://dashscope.aliyuncs.com/compatible-mode/v1", "openai"),
        ProviderPreset("智譜開放平台", "智譜", "https://open.bigmodel.cn/api/paas/v4", "openai"),
        ProviderPreset("xAI 官方", "xAI", "https://api.x.ai/v1", "openai"),
        ProviderPreset("OpenRouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai"),
        ProviderPreset("硅基流動 SiliconFlow", "SiliconFlow", "https://api.siliconflow.cn/v1", "openai"),
    )

    val protocols = listOf(
        "openai" to "OpenAI Chat Completions 相容",
        "openai-responses" to "OpenAI Responses",
        "anthropic" to "Anthropic Messages",
        "gemini" to "Google Gemini",
    )

    val apps = listOf(
        "claude" to "Claude Code",
        "codex" to "Codex",
        "gemini" to "Gemini CLI",
    )

    /** 卡片 models 只有這一項時，表示「使用 API 的所有模型」，讀卡時向 API 取得清單（SPEC.md） */
    const val ALL_MODELS = "*"

    /** 刷卡時自動開啟的 App（鯨幣 客戶端）。 */
    const val DEFAULT_AAR = "com.tokencoin.client"

    /** 常見 NTAG 可用 NDEF 容量（bytes，已扣除 TLV 開銷，約略值）。 */
    val tagCapacities = listOf("NTAG213" to 137, "NTAG215" to 496, "NTAG216" to 868)
}
