package com.tokencoin.client.card

import org.junit.Assert.*
import org.junit.Test

class WebCardLinkTest {
    private val card = TokenCard(id = "a1b2c3d4", title = "鯨幣😀", vendor = "deepseek",
        model = "deepseek-chat", provider = "測試", proto = "openai", base = "https://api.example.com",
        key = "sk-test", iat = 1760000000, extra = mapOf("x_nested" to mapOf("z" to 1L, "a" to "保留")))

    @Test fun roundTripUnicodeAndUnknownFields() {
        val url = WebCardLink.create("https://example.com/deepcoin/?mode=web", card)
        assertFalse(url.substringBefore('#').contains(card.key))
        assertFalse(url.substringAfter('#').contains("=" .repeat(2)))
        val decoded = WebCardLink.decode(url)
        assertEquals(TokenCardCodec.canonicalJson(TokenCardCodec.fields(card)), TokenCardCodec.canonicalJson(TokenCardCodec.fields(decoded.card)))
        assertEquals(ShaStatus.OK, decoded.shaStatus)
    }
    @Test fun manualOpenAppLink() {
        val payload = WebCardLink.create("https://example.com/", card).substringAfter('#')
        assertEquals(TokenCardCodec.encode(card).toList(), TokenCardCodec.encode(WebCardLink.decode("tokencoin://card#$payload").card).toList())
    }
    @Test fun rejectsInvalidUrls() {
        listOf("http://example.com", "https://example.com/#old", "https://user:pass@example.com", "not-a-url").forEach {
            assertTrue(runCatching { WebCardLink.create(it, card) }.isFailure)
        }
    }
    @Test fun rejectsInvalidPayload() {
        listOf("https://example.com/#card=!!!", "https://example.com/#other=abc",
            "javascript:alert(1)", "tokencoin://other#card=abc").forEach {
            assertTrue(runCatching { WebCardLink.decode(it) }.isFailure)
        }
    }
}
