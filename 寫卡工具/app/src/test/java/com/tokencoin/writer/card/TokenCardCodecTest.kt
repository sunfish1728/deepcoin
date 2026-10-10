package com.tokencoin.writer.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

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
        val sha = MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
        // sha 依字典序位於 provider 與 title 之間
        val expected = body.replace(""","title":""", ""","sha":"$sha","title":""")
        assertEquals(expected, json)
        println(json)
    }

    @Test
    fun roundTripAndVerify() {
        val d = TokenCardCodec.decode(TokenCardCodec.encode(card))
        assertEquals(ShaStatus.OK, d.shaStatus)
        assertEquals(card, d.card)
    }

    @Test
    fun detectsTampering() {
        val tampered = TokenCardCodec.encode(card).toString(Charsets.UTF_8).replace("deepseek-chat", "deepseek-xxxx")
        assertEquals(ShaStatus.MISMATCH, TokenCardCodec.decode(tampered.toByteArray()).shaStatus)
    }

    @Test
    fun unknownFieldsArePreservedAndHashed() {
        val withExtra = card.copy(extra = mapOf("x_future" to "hello"))
        val d = TokenCardCodec.decode(TokenCardCodec.encode(withExtra))
        assertEquals(ShaStatus.OK, d.shaStatus)
        assertEquals("hello", d.card.extra["x_future"])
        assertFalse(d.canonicalJson.isEmpty())
        assertTrue(d.canonicalJson.contains("\"x_future\":\"hello\""))
    }
}
