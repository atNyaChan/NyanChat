package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatServiceTest {
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
        assertEquals(ReasoningLevel.OFF, params.reasoningLevel)
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
}
