package com.tokencoin.writer.ui

import org.junit.Assert.*
import org.junit.Test

class CardFormTest {
    @Test fun webUrlIsSavedButDoesNotChangeCardJson() {
        val form = CardForm(id = "abcd1234", key = "sk-test", webUrl = "https://example.com/deepcoin/")
        assertEquals(form, CardForm.fromJson(form.toJson()))
        assertEquals(form.copy(webUrl = "").toCard(1).first, form.toCard(1).first)
    }
    @Test fun invalidWebUrlStopsWriting() {
        val (card, errors) = CardForm(key = "sk-test", webUrl = "http://example.com/#old").toCard(1)
        assertNull(card)
        assertTrue(errors.any { it.contains("HTTPS") })
    }
    @Test fun oldDraftKeepsLegacyAarMode() {
        val json = CardForm(key = "sk-test").toJson().apply { remove("webUrl") }
        assertEquals("", CardForm.fromJson(json).webUrl)
        assertTrue(CardForm.fromJson(json).aarEnabled)
    }
}
