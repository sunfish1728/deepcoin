package com.tokencoin.writer.net
import com.tokencoin.writer.card.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class HostedCardsTest {
 private val card=TokenCard(id="a1b2c3d4",vendor="deepseek",model="deepseek-chat",provider="DeepSeek 官方",proto="openai",base="https://api.deepseek.com",key="private-test-key",iat=1791676800,note="中文備註",extra=mapOf("future" to "保留"))
 private val ref=card.copy(v=2,ref="https://raw.githubusercontent.com/sunfish1728/deepcoin-web-API/main/cards/test.json")
 @Test fun publicSettingsExcludeKeyAndPreserveFields(){val json=HostedCards.publicJson(card);assertFalse(JSONObject(json).has("key"));assertFalse(json.contains(card.key));assertEquals("保留",JSONObject(json).getString("future"));assertEquals(card,HostedCards.merge(ref,json).card)}
 @Test fun referenceContainsOnlyFourFields(){val bytes=TokenCardCodec.encode(ref);val o=JSONObject(bytes.toString(Charsets.UTF_8));assertEquals(setOf("v","id","key","ref"),o.keys().asSequence().toSet());val decoded=TokenCardCodec.decode(bytes).card;assertEquals(ref.ref,decoded.ref);assertEquals(ref.key,decoded.key);assertEquals(ref.id,decoded.id);assertTrue(bytes.size < TokenCardCodec.encode(card.copy(note="備註".repeat(300))).size)}
 @Test fun defaultAndCustomWebLinksRoundTrip(){val link=WebCardLink.create("",ref);assertTrue(link.startsWith(WebCardLink.DEFAULT_WEB_URL));assertEquals(ref.ref,WebCardLink.decode(link).card.ref);assertTrue(WebCardLink.create("https://example.com/toy/",ref).startsWith("https://example.com/toy/"))}
 @Test fun rejectsMismatchedOrSecretPublicSettings(){for(field in listOf("id","key","ref","v")){val o=JSONObject(HostedCards.publicJson(card));o.put(field,if(field=="v") 2 else "wrong");try{HostedCards.merge(ref,o.toString());fail("Accepted $field")}catch(expected:IllegalArgumentException){}}}
 @Test(expected=IllegalArgumentException::class) fun rejectsOversize(){HostedCards.merge(ref," ".repeat(65537))}
}
