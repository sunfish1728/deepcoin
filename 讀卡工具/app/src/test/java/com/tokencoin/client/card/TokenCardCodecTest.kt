package com.tokencoin.client.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenCardCodecTest {

    private val card = TokenCard(
        id = "a1b2c3d4", title = "DeepSeek娘幣", vendor = "deepseek", model = "deepseek-chat",
        models = listOf("deepseek-reasoner"), provider = "DeepSeek 官方", proto = "openai",
        base = "https://api.deepseek.com", key = "sk-test\"quote", apps = listOf("claude"),
        iat = 1760000000, balanceUsd = "5.00", char = "DeepSeek娘", charId = "ds-01",
    )

    @Test
    fun canonicalForm() {
        val json = TokenCardCodec.encode(card).toString(Charsets.UTF_8)
        // key 排序、無空白、空欄位省略、/ 不跳脫、中文原樣
        val body = """{"apps":["claude"],"balance_usd":"5.00","base":"https://api.deepseek.com","char":"DeepSeek娘","char_id":"ds-01","iat":1760000000,"id":"a1b2c3d4","key":"sk-test\"quote","model":"deepseek-chat","models":["deepseek-reasoner"],"proto":"openai","provider":"DeepSeek 官方","title":"DeepSeek娘幣","v":1,"vendor":"deepseek"}"""
        assertEquals(body, json)
        assertFalse(json.contains("\"sha\""))
    }

    @Test
    fun unsignedRoundTrip() {
        val d = TokenCardCodec.decode(TokenCardCodec.encode(card))
        assertEquals(card, d.card)
    }

    @Test
    fun legacyShaIsIgnoredAndRemoved() {
        val legacy = TokenCardCodec.canonicalJson(TokenCardCodec.fields(card) + ("sha" to "invalid-old-hash"))
        val decoded = TokenCardCodec.decode(legacy.toByteArray(Charsets.UTF_8))
        assertEquals(card, decoded.card)
        assertFalse(decoded.canonicalJson.contains("\"sha\""))
        assertEquals(TokenCardCodec.encode(card).toList(), TokenCardCodec.encode(decoded.card).toList())
    }

    @Test
    fun removes73BytesAndCannotReintroduceShaThroughExtras() {
        val fields = TokenCardCodec.fields(card)
        val legacy = TokenCardCodec.canonicalJson(fields + ("sha" to "a".repeat(64))).toByteArray(Charsets.UTF_8)
        assertEquals(73, legacy.size - TokenCardCodec.encode(card).size)
        assertEquals(TokenCardCodec.encode(card).toList(), TokenCardCodec.encode(card.copy(extra = mapOf("sha" to "a".repeat(64)))).toList())
    }

    @Test
    fun unknownFieldsArePreserved() {
        val withExtra = card.copy(extra = mapOf("x_future" to "hello"))
        val d = TokenCardCodec.decode(TokenCardCodec.encode(withExtra))
        assertEquals("hello", d.card.extra["x_future"])
        assertFalse(d.canonicalJson.isEmpty())
        assertTrue(d.canonicalJson.contains("\"x_future\":\"hello\""))
    }
}
