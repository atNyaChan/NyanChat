package me.rerere.rikkahub.data.db.fts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class MessageFtsManagerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var databaseName: String
    private lateinit var database: AppDatabase
    private lateinit var fts: MessageFtsManager
    private val assistantId = Uuid.random()

    @Before
    fun setUp() {
        directory = Files.createTempDirectory(context.cacheDir.toPath(), "message-fts-test-").toFile()
        databaseName = "message-fts-test-${Uuid.random()}"
        database = AppDatabaseFactory.create(context, databaseName)
        fts = MessageFtsManager(database, directory)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        directory.deleteRecursively()
    }

    @Test
    fun savingOnlyRewritesChangedMessages() = runBlocking {
        val apple = UIMessage.user("apple pie")
        val banana = UIMessage.assistant("banana split")
        val cherry = UIMessage.user("cherry tart")
        val conversation = conversation("first", apple, banana, cherry)
        save(conversation, insert = true)
        val before = indexedRowids()

        val durian = banana.copy(parts = UIMessage.assistant("durian cake").parts)
        val elderberry = UIMessage.assistant("elderberry jam")
        save(
            conversation.copy(
                title = "renamed",
                messageNodes = conversation.messageNodes.take(1) +
                    MessageNode.of(durian).copy(id = conversation.messageNodes[1].id) +
                    MessageNode.of(elderberry),
            ),
            insert = false,
        )
        val after = indexedRowids()

        // 未改动的消息不重写，改动过的消息换了一行，删掉的消息不再占行
        assertEquals(before[apple.id.toString()], after[apple.id.toString()])
        assertNotEquals(before[banana.id.toString()], after[banana.id.toString()])
        assertEquals(
            setOf(apple.id, banana.id, elderberry.id).map { it.toString() }.toSet(),
            after.keys,
        )
        assertEquals(emptyList<String>(), fts.search("banana").map { it.messageId })
        assertEquals(emptyList<String>(), fts.search("cherry").map { it.messageId })
        val result = fts.search("durian").single()
        assertEquals(banana.id.toString(), result.messageId)
        // 标题取自 conversationentity，改标题不需要改写索引
        assertEquals("renamed", result.title)
    }

    @Test
    fun renamingConversationUpdatesSearchResultsWithoutReindexing() = runBlocking {
        val conversation = conversation("first", UIMessage.user("apple pie"))
        save(conversation, insert = true)
        val before = indexedRowids()

        database.conversationDao().updateTitle(conversation.id.toString(), "renamed")

        assertEquals(before, indexedRowids())
        assertEquals("renamed", fts.search("apple").single().title)
    }

    @Test
    fun deletingConversationKeepsOthers() = runBlocking {
        val kept = conversation("kept", UIMessage.user("shared word kept"))
        val deleted = conversation("deleted", UIMessage.user("shared word deleted"))
        save(kept, insert = true)
        save(deleted, insert = true)

        fts.deleteConversation(deleted.id.toString())

        assertEquals(listOf(kept.id.toString()), fts.search("shared").map { it.conversationId })
    }

    private fun conversation(title: String, vararg messages: UIMessage) = Conversation(
        assistantId = assistantId,
        title = title,
        messageNodes = messages.map { MessageNode.of(it) },
    )

    private suspend fun save(conversation: Conversation, insert: Boolean) {
        val entity = ConversationEntity(
            id = conversation.id.toString(),
            assistantId = conversation.assistantId.toString(),
            title = conversation.title,
            nodes = "[]",
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            chatSuggestions = "[]",
            isPinned = false,
        )
        if (insert) database.conversationDao().insert(entity) else database.conversationDao().update(entity)
        fts.indexConversation(conversation)
    }

    private fun indexedRowids(): Map<String, Long> =
        database.openHelper.writableDatabase.query(
            "SELECT message_id, rowid FROM message_search_cache"
        ).use {
            buildMap { while (it.moveToNext()) put(it.getString(0), it.getLong(1)) }
        }
}
