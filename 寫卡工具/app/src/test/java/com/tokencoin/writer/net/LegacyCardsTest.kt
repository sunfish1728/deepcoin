package com.tokencoin.writer.net
import com.tokencoin.writer.card.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class LegacyCardsTest {
 private val data="""{"v":1,"id":"a1b2c3d4","vendor":"deepseek","provider":"測試","model":"deepseek-chat","proto":"openai","base":"https://api.deepseek.com","iat":1791676800,"char_id":"deepseek-whale"}"""
 private val ref=TokenCard(v=2,ref="https://example.com/retired",id="a1b2c3d4",key="card-only-key",vendor="",provider="",model="",proto="",base="",iat=0)
 @Test fun migratesToCompleteCard(){val full=LegacyCards.merge(ref,data).card;assertEquals(1,full.v);assertEquals("",full.ref);assertEquals(ref.key,full.key);assertEquals("deepseek-whale",full.charId);assertEquals("deepseek-chat",full.model)}
 @Test fun completeCardUsesDefaultAndCustomWebLinks(){val full=LegacyCards.merge(ref,data).card;assertTrue(WebCardLink.create("",full).startsWith(WebCardLink.DEFAULT_WEB_URL));assertEquals(full,WebCardLink.decode(WebCardLink.create("https://example.com/toy/",full)).card)}
 @Test fun rejectsWrongIdAndRemoteKeyOrNestedRef(){for(field in listOf("id","key","ref","v")){val o=JSONObject(data);o.put(field,if(field=="v") 2 else "wrong");try{LegacyCards.merge(ref,o.toString());fail("Accepted $field")}catch(expected:IllegalArgumentException){}}}
 @Test(expected=IllegalArgumentException::class) fun rejectsOversizedBackup(){LegacyCards.merge(ref," ".repeat(65537))}
}
