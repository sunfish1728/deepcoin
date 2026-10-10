package com.tokencoin.client.writer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFormTest {

    @Test
    fun validFormBuildsCard() {
        val (card, errors) = CardForm(id = "abcd1234", key = "sk-test", models = "a, deepseek-chat，b\nc", balanceUsd = "$1,234.50")
            .toCard(now = 1_000)
        assertTrue(errors.isEmpty())
        assertNotNull(card)
        // 預設模型不重複列入其他模型；全形逗號與換行也能分隔
        assertEquals(listOf("a", "b", "c"), card!!.models)
        assertEquals("1234.50", card.balanceUsd)
        assertEquals(1_000L, card.iat)
    }

    @Test
    fun invalidFieldsReported() {
        val (card, errors) = CardForm(base = "api.example.com", key = "sk x", exp = "2026/1/1", balanceUsd = "abc").toCard()
        assertNull(card)
        assertEquals(4, errors.size)
    }

    @Test
    fun cardRoundTripKeepsFields() {
        val form = CardForm(id = "abcd1234", title = "DeepSeek娘幣", key = "sk-test", char = "鯨魚娘", charId = "whale", avatar = "https://x/y.vrm")
        val card = form.toCard(now = 1).first!!
        val back = CardForm.fromCard(card)
        assertEquals(form.title, back.title)
        assertEquals(form.charId, back.charId)
        assertEquals(form.avatar, back.avatar)
        assertEquals(form.key, back.key)
        assertEquals(form, CardForm.fromJson(form.toJson()))
    }
}
