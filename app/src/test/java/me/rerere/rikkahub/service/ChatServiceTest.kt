package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatServiceTest {
    @Test
    fun `fork conversation inherits folder and workspace context`() {
        val source = Conversation(
            assistantId = Uuid.random(),
            title = "Source conversation",
            messageNodes = emptyList(),
            workspaceCwd = "/workspace/project",
            folderId = Uuid.random(),
        )

        val fork = createForkConversation(source, emptyList())

        assertNotEquals(source.id, fork.id)
        assertEquals(source.assistantId, fork.assistantId)
        assertEquals(source.workspaceCwd, fork.workspaceCwd)
        assertEquals(source.folderId, fork.folderId)
        assertEquals("", fork.title)
        assertFalse(fork.isPinned)
    }

    @Test
    fun `background generation params include model custom request configuration`() {
        val headers = listOf(CustomHeader(name = "X-Gateway-Token", value = "test-token"))
        val bodies = listOf(CustomBody(key = "gateway_mode", value = JsonPrimitive("strict")))
        val model = Model(
            modelId = "custom-chat-model",
            customHeaders = headers,
            customBodies = bodies,
        )

        val params = backgroundTextGenerationParams(model)

        assertEquals(model, params.model)
        assertEquals(ReasoningLevel.AUTO, params.reasoningLevel)
        assertEquals(headers, params.customHeaders)
        assertEquals(bodies, params.customBody)
    }

    @Test
    fun `tool continuation keeps unique assistant message identities`() {
        val user = UIMessage.user("Run tools")
        val firstAssistant = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("First tool step")),
        )
        val continuation = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("Finished")),
        )
        val pendingResponse = UIMessage.assistant("")

        val messages = listOf(user, firstAssistant, continuation)
            .prepareGeneratedMessages(
                responseMessageIndex = 1,
                pendingResponse = pendingResponse,
            )

        assertEquals(pendingResponse.id, messages[1].id)
        assertEquals(continuation.id, messages[2].id)
        assertNotEquals(messages[1].id, messages[2].id)
    }

    @Test
    fun `empty tool continuation is hidden while initial placeholder remains`() {
        val user = UIMessage.user("Run tools")
        val initialPlaceholder = UIMessage.assistant("")
        val emptyContinuation = UIMessage.assistant("")
        val pendingResponse = UIMessage.assistant("")

        val messages = listOf(user, initialPlaceholder, emptyContinuation)
            .prepareGeneratedMessages(
                responseMessageIndex = 1,
                pendingResponse = pendingResponse,
            )

        assertEquals(2, messages.size)
        assertEquals(pendingResponse.id, messages[1].id)
    }

    @Test
    fun `external web search is disabled when assistant preference is disabled`() {
        val assistant = Assistant(enableWebSearch = false)
        val model = Model()

        assertFalse(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `external web search is enabled when assistant preference is enabled`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model()

        assertTrue(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `built-in search suppresses enabled external web search`() {
        val assistant = Assistant(enableWebSearch = true, useBuiltInSearch = true)
        val model = Model(tools = setOf(BuiltInTools.Search))

        assertFalse(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `built-in search falls back to external when model lacks the tool`() {
        val assistant = Assistant(enableWebSearch = true, useBuiltInSearch = true)
        val model = Model(tools = emptySet())

        assertTrue(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `built-in search remains exclusive when external web search is disabled`() {
        val assistant = Assistant(enableWebSearch = false, useBuiltInSearch = true)
        val model = Model(tools = setOf(BuiltInTools.Search))

        assertFalse(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `unrelated built-in tools do not suppress external web search`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(tools = setOf(BuiltInTools.UrlContext))

        assertTrue(shouldUseExternalWebSearch(assistant, model))
    }
}
