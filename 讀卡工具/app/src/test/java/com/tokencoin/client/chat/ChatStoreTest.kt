package com.tokencoin.client.chat

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChatStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun conversation(id: String = "test", updated: Long = 2) = Conversation(
        id, "測試標題", 1, updated, "card", "model", ChatSettings("系統提示", 0.5, 456),
        listOf(ChatMessage("user", Role.USER, "你好", createdAt = 1),
            ChatMessage("assistant", Role.ASSISTANT, "內容", "推理", "model", 2,
                MessageStatus.ERROR, "錯誤", TokenUsage(12, 34), 789)))

    @Test fun roundTripOverwriteSortCorruptSkipAndDelete() {
        val dir = temp.newFolder(); val store = ChatStore(dir)
        val first = conversation(); val latest = conversation("latest", 3)
        store.save(first); store.save(latest)
        File(dir, "broken.json").writeText("invalid JSON")
        File(dir, "incomplete.json").writeText("{}")
        File(dir, "orphan.tmp").writeText("invalid temp")
        assertEquals(listOf(latest, first), store.load())
        val modified = first.copy(title = "修改", updatedAt = 4)
        store.save(modified)
        assertEquals(modified, store.load().first())
        assertFalse(dir.listFiles()!!.any { it.name.startsWith("chat-") })
        store.delete("test"); store.delete("missing")
        assertEquals(listOf(latest), store.load())
    }

    @Test fun restoresStreamingAsStoppedAndNullableFields() {
        val store = ChatStore(temp.newFolder())
        val c = conversation().copy(settings = ChatSettings(), messages = listOf(
            ChatMessage("partial", Role.ASSISTANT, "部分回覆", createdAt = 1, status = MessageStatus.STREAMING)))
        store.save(c)
        assertEquals(c.copy(messages = listOf(c.messages.single().copy(status = MessageStatus.STOPPED))), store.load().single())
    }

    @Test fun createsDirectoryAndRejectsTraversal() {
        val store = ChatStore(File(temp.root, "new/chat"))
        assertTrue(store.load().isEmpty()); store.save(conversation())
        assertEquals(1, store.load().size)
        assertTrue(runCatching { store.save(conversation("../escape")) }.isFailure)
        assertTrue(runCatching { store.delete("../escape") }.isFailure)
    }
}
