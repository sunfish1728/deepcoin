package com.tokencoin.client.avatar

/** 詞表集中於此；一般詞與表情權重為 1，標點只輕微加分。 */
object KeywordEmotionAnalyzer : EmotionAnalyzer {
    private val words = mapOf(
        Emotion.JOY to listOf("開心", "开心", "快樂", "快乐", "哈哈", "太好了", "happy", "glad", "great", "😀", "😄", "😊", "🥰", "😂", "🎉", "❤️", "(｡•̀ᴗ-)✧", "( ´∀｀)"),
        Emotion.SAD to listOf("抱歉", "對不起", "对不起", "難過", "难过", "遺憾", "遗憾", "傷心", "伤心", "sorry", "unfortunately", "😢", "😭", "😞", "(╥﹏╥)"),
        Emotion.ANGRY to listOf("生氣", "生气", "可惡", "可恶", "憤怒", "愤怒", "angry", "😠", "😡", "💢", "(╯°□°)╯"),
        Emotion.SURPRISED to listOf("哇", "竟然", "居然", "真的嗎", "真的吗", "wow", "really?", "😮", "😲", "😱", "❗"),
        Emotion.RELAXED to listOf("放鬆", "放松", "慢慢來", "慢慢来", "relax", "😌", "🍵", "☺️"),
        Emotion.THINKING to listOf("讓我想想", "让我想想", "嗯", "let me think", "hmm", "🤔"),
    )
    private val punctuation = mapOf(
        Emotion.JOY to listOf("！！", "!!"),
        Emotion.SURPRISED to listOf("！！", "!!", "？？", "??"),
        Emotion.THINKING to listOf("……", "..."),
        Emotion.SAD to listOf("……", "..."),
    )
    private val fencedCode = Regex("```[\\s\\S]*?(?:```|$)")

    override fun analyze(text: String): EmotionResult {
        val clean = text.replace(fencedCode, " ")
        val tail = (clean.length - 200).coerceAtLeast(0)
        val scores = mutableMapOf<Emotion, Float>()
        fun add(emotion: Emotion, token: String, weight: Float) {
            val english = token.firstOrNull()?.let { it in 'a'..'z' } == true
            val pattern = if (english) "(?<![A-Za-z])${Regex.escape(token)}(?![A-Za-z])" else Regex.escape(token)
            Regex(pattern, RegexOption.IGNORE_CASE).findAll(clean).forEach { match ->
                val bonus = if (match.range.last >= tail) 1.5f else 1f
                scores[emotion] = (scores[emotion] ?: 0f) + weight * bonus
            }
        }
        words.forEach { (emotion, tokens) -> tokens.forEach { add(emotion, it, 1f) } }
        punctuation.forEach { (emotion, tokens) -> tokens.forEach { add(emotion, it, 0.2f) } }
        val best = scores.maxByOrNull { it.value }
        return if (best == null || best.value < 1f) EmotionResult(Emotion.NEUTRAL, 0f)
        else EmotionResult(best.key, (best.value / 4f).coerceIn(0.35f, 1f))
    }
}
