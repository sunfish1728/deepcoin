package com.tokencoin.client.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AvatarRepoFormatTest {

    @Test
    fun githubRepoUrlMapsToRawIndex() {
        val r = AvatarRepoFormat.parseRef("https://github.com/Sunfish1728/DeepCoin-Avatars#whale")!!
        assertEquals("https://raw.githubusercontent.com/sunfish1728/deepcoin-avatars/HEAD/deepcoin-avatars.json", r.indexUrl)
        assertEquals("https://github.com/sunfish1728/deepcoin-avatars", r.pageUrl)
        assertEquals("whale", r.fragment)

        val t = AvatarRepoFormat.parseRef("https://github.com/a/b/tree/v2")!!
        assertEquals("https://raw.githubusercontent.com/a/b/v2/deepcoin-avatars.json", t.indexUrl)
        assertEquals("https://github.com/a/b/tree/v2", t.pageUrl)
    }

    @Test
    fun directFilesAreNotRepos() {
        assertNull(AvatarRepoFormat.parseRef("https://github.com/a/b/releases/download/v1/x.vrm"))
        assertNull(AvatarRepoFormat.parseRef("https://example.com/x.zip"))
        assertNull(AvatarRepoFormat.parseRef("ftp://example.com/deepcoin-avatars.json"))
        assertEquals("https://example.com/x.zip", AvatarRepoFormat.sourceKey(" https://example.com/x.zip ", "whale"))
    }

    @Test
    fun sourceKeyAddsCharId() {
        assertEquals("https://github.com/a/b#whale", AvatarRepoFormat.sourceKey("https://github.com/A/B", "whale"))
        assertEquals("https://github.com/a/b#cat", AvatarRepoFormat.sourceKey("https://github.com/a/b#cat", "whale"))
        assertEquals("http://10.0.2.2:1/r/deepcoin-avatars.json#w", AvatarRepoFormat.sourceKey("http://10.0.2.2:1/r/deepcoin-avatars.json", "w"))
    }

    private val ref = AvatarRepoFormat.parseRef("https://github.com/a/b")!!

    @Test
    fun parsesIndexAndResolvesRelativePaths() {
        val json = """
            {"format":"deepcoin-avatars/1","name":"測試庫","avatars":[
              {"char_id":"whale","name":"鯨魚娘","version":"1.0.0","file":"models/whale v1.zip","thumb":"thumbs/whale.png",
               "sha256":"${"A".repeat(64)}","size":123,"authors":["木子"],"license":"CC-BY-4.0"},
              {"char_id":"cat","name":"貓","file":"https://github.com/a/b/releases/download/cat-v1/cat.vrm"},
              {"char_id":"whale","name":"重複","file":"x.vrm"},
              {"char_id":"bad id","name":"壞","file":"x.vrm"},
              {"char_id":"esc","name":"跳出","file":"../x.vrm"}
            ]}
        """.trimIndent()
        val idx = AvatarRepoFormat.parseIndex(json, ref)
        assertEquals("測試庫", idx.name)
        assertEquals(listOf("whale", "cat"), idx.entries.map { it.charId })
        val w = idx.entries[0]
        assertEquals("https://raw.githubusercontent.com/a/b/HEAD/models/whale%20v1.zip", w.fileUrl)
        assertEquals("https://raw.githubusercontent.com/a/b/HEAD/thumbs/whale.png", w.thumbUrl)
        assertEquals("a".repeat(64), w.sha256)
        assertEquals(123L, w.size)
        assertEquals("https://github.com/a/b#whale", w.sourceKey)
        assertEquals("", idx.entries[1].sha256)
    }

    @Test
    fun rejectsWrongFormat() {
        val e = runCatching { AvatarRepoFormat.parseIndex("""{"format":"other","avatars":[]}""", ref) }.exceptionOrNull()
        assertTrue(e is IOException)
    }

    @Test
    fun pickOrder() {
        val idx = AvatarRepoFormat.parseIndex(
            """{"format":"deepcoin-avatars/1","avatars":[{"char_id":"a","name":"A","file":"a.vrm"},{"char_id":"b","name":"B","file":"b.vrm"}]}""",
            ref,
        )
        assertEquals("b", AvatarRepoFormat.pick(idx, "b", "a")?.charId)
        assertNull(AvatarRepoFormat.pick(idx, "zzz", "a"))
        assertEquals("b", AvatarRepoFormat.pick(idx, "", "b")?.charId)
        assertEquals("a", AvatarRepoFormat.pick(idx, "", "nope")?.charId)
    }
}
