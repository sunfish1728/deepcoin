package com.tokencoin.client.avatar

import org.junit.Assert.*
import org.junit.Test

class KeywordEmotionAnalyzerTest {
    @Test fun traditionalChinese() {
        assertEquals(Emotion.JOY, KeywordEmotionAnalyzer.analyze("太好了！好開心哈哈！！").emotion)
        assertEquals(Emotion.SAD, KeywordEmotionAnalyzer.analyze("很抱歉，我覺得難過與遺憾。").emotion)
        assertEquals(Emotion.RELAXED, KeywordEmotionAnalyzer.analyze("放鬆，慢慢來。").emotion)
    }

    @Test fun simplifiedChinese() {
        assertEquals(Emotion.ANGRY, KeywordEmotionAnalyzer.analyze("太可恶了，真让人生气！").emotion)
        assertEquals(Emotion.SAD, KeywordEmotionAnalyzer.analyze("对不起，我很难过。").emotion)
        assertEquals(Emotion.THINKING, KeywordEmotionAnalyzer.analyze("让我想想，嗯……").emotion)
    }

    @Test fun english() {
        assertEquals(Emotion.JOY, KeywordEmotionAnalyzer.analyze("I'm happy and glad. Great!").emotion)
        assertEquals(Emotion.SURPRISED, KeywordEmotionAnalyzer.analyze("Wow, really?").emotion)
        assertEquals(Emotion.SAD, KeywordEmotionAnalyzer.analyze("Unfortunately, sorry.").emotion)
        assertEquals(Emotion.THINKING, KeywordEmotionAnalyzer.analyze("Hmm, let me think.").emotion)
    }

    @Test fun emojiAndKaomoji() {
        mapOf("😀🎉❤️" to Emotion.JOY, "😭😞" to Emotion.SAD, "😠💢" to Emotion.ANGRY,
            "😱😲" to Emotion.SURPRISED, "😌🍵☺️" to Emotion.RELAXED, "🤔" to Emotion.THINKING,
            "(｡•̀ᴗ-)✧" to Emotion.JOY, "(╥﹏╥)" to Emotion.SAD, "(╯°□°)╯" to Emotion.ANGRY,
            "( ´∀｀)" to Emotion.JOY).forEach { (text, emotion) ->
            val result = KeywordEmotionAnalyzer.analyze(text)
            assertEquals(text, emotion, result.emotion)
            assertTrue(result.intensity in 0.35f..1f)
        }
    }

    @Test fun fencedCodeIgnoredIncludingUnclosedFence() {
        assertEquals(EmotionResult(Emotion.NEUTRAL, 0f), KeywordEmotionAnalyzer.analyze("範例：\n```kotlin\n// happy 😀 angry\n```"))
        assertEquals(Emotion.SAD, KeywordEmotionAnalyzer.analyze("```happy 😀```抱歉").emotion)
        assertEquals(Emotion.NEUTRAL, KeywordEmotionAnalyzer.analyze("文字```happy 😀").emotion)
    }

    @Test fun neutralAndEnglishWordBoundaries() {
        listOf("", "請參考文件。", "這是測試文字。", "!!", "……", "unhappy", "greatest").forEach {
            assertEquals(it, EmotionResult(Emotion.NEUTRAL, 0f), KeywordEmotionAnalyzer.analyze(it))
        }
    }

    @Test fun endingGetsMoreWeightAndIntensityClamps() {
        assertEquals(Emotion.SAD, KeywordEmotionAnalyzer.analyze("開心" + "字".repeat(250) + "抱歉").emotion)
        assertEquals(1f, KeywordEmotionAnalyzer.analyze("哈哈".repeat(20)).intensity, 0f)
        assertEquals(0.35f, KeywordEmotionAnalyzer.analyze("happy" + "字".repeat(250)).intensity, 0f)
    }
}
