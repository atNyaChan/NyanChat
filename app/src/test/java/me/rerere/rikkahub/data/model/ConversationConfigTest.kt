package me.rerere.rikkahub.data.model

import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationConfigTest {
    private val modelA = Model(modelId = "model-a", displayName = "Model A", tools = setOf(BuiltInTools.Search))
    private val modelB = Model(modelId = "model-b", displayName = "Model B")
    private val mcpServer = Uuid.random()
    private val workspace = Uuid.random()
    private val injection = Uuid.random()
    private val lorebook = Uuid.random()

    private val assistant = Assistant(
        chatModelId = modelA.id,
        reasoningLevel = ReasoningLevel.HIGH,
        enableWebSearch = true,
        useBuiltInSearch = true,
        mcpServers = setOf(mcpServer),
        workspaceId = workspace,
        enabledSkills = setOf("skill"),
        modeInjectionIds = setOf(injection),
        lorebookIds = setOf(lorebook),
    )

    private fun settings(assistant: Assistant = this.assistant) = Settings(
        assistantId = assistant.id,
        assistants = listOf(assistant),
        chatModelId = modelB.id,
        providers = listOf(ProviderSetting.OpenAI(models = listOf(modelA, modelB))),
        mcpServers = listOf(McpServerConfig.StreamableHTTPServer(id = mcpServer)),
        modeInjections = listOf(PromptInjection.ModeInjection(id = injection)),
        lorebooks = listOf(Lorebook(id = lorebook)),
    )

    private fun conversation(assistant: Assistant = this.assistant) = Conversation(
        assistantId = assistant.id,
        messageNodes = emptyList(),
    )

    @Test
    fun `unstarted conversation follows the assistant`() {
        val conversation = conversation()

        assertSame(assistant, settings().getAssistantOf(conversation))
        assertEquals(modelA, settings().getChatModelOf(conversation))
    }

    @Test
    fun `binding snapshots the assistant configuration`() {
        val bound = conversation().bindConfig(settings())

        assertEquals(
            ConversationConfig(
                chatModelId = modelA.id,
                reasoningLevel = ReasoningLevel.HIGH,
                enableWebSearch = true,
                builtInSearch = true,
                mcpServers = setOf(mcpServer),
                workspaceId = workspace,
                enabledSkills = setOf("skill"),
            ),
            bound.config,
        )
        assertEquals(setOf(injection), bound.modeInjectionIds)
        assertEquals(setOf(lorebook), bound.lorebookIds)
        assertSame(bound, bound.bindConfig(settings(assistant.copy(reasoningLevel = ReasoningLevel.OFF))))
    }

    @Test
    fun `binding resolves the global default model`() {
        val assistant = assistant.copy(chatModelId = null, useBuiltInSearch = false)

        val bound = conversation(assistant).bindConfig(settings(assistant))

        assertEquals(modelB.id, bound.config?.chatModelId)
        assertEquals(false, bound.config?.builtInSearch)
    }

    @Test
    fun `bound conversation ignores later assistant changes`() {
        val bound = conversation().bindConfig(settings())
        val changed = assistant.copy(
            chatModelId = modelB.id,
            reasoningLevel = ReasoningLevel.OFF,
            enableWebSearch = false,
            useBuiltInSearch = false,
            mcpServers = emptySet(),
            workspaceId = null,
            enabledSkills = emptySet(),
            modeInjectionIds = emptySet(),
            lorebookIds = emptySet(),
            systemPrompt = "changed",
        )

        val resolved = settings(changed).getAssistantOf(bound)

        assertEquals(assistant.copy(systemPrompt = "changed"), resolved)
        assertEquals(modelA, settings(changed).getChatModelOf(bound))
    }

    @Test
    fun `bound built-in search follows the conversation config`() {
        val bound = conversation().bindConfig(settings())
        val disabled = bound.copy(config = bound.config?.copy(builtInSearch = false))

        assertTrue(settings().getAssistantOf(bound).useBuiltInSearch)
        assertFalse(settings().getAssistantOf(disabled).useBuiltInSearch)
    }

    @Test
    fun `bound conversation falls back to the assistant model when its model is removed`() {
        val bound = conversation().bindConfig(settings())
        val withoutModelA = settings(assistant.copy(chatModelId = null)).copy(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(modelB))),
        )

        assertEquals(modelB.id, withoutModelA.getChatModelOf(bound)?.id)
    }

    @Test
    fun `binding again pins the assistant model once the pinned model is removed`() {
        val bound = conversation().bindConfig(settings())
        val withoutModelA = settings(assistant.copy(chatModelId = null)).copy(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(modelB))),
        )

        val rebound = bound.bindConfig(withoutModelA)

        assertEquals(bound.config?.copy(chatModelId = modelB.id), rebound.config)
        assertEquals(bound.modeInjectionIds, rebound.modeInjectionIds)
        assertSame(rebound, rebound.bindConfig(withoutModelA))
    }

    @Test
    fun `bound conversation drops references to removed items`() {
        val bound = conversation().bindConfig(settings())
        val emptied = settings().copy(mcpServers = emptyList(), modeInjections = emptyList(), lorebooks = emptyList())

        val resolved = emptied.getAssistantOf(bound)

        assertEquals(emptySet<Uuid>(), resolved.mcpServers)
        assertEquals(emptySet<Uuid>(), resolved.modeInjectionIds)
        assertEquals(emptySet<Uuid>(), resolved.lorebookIds)
    }

    @Test
    fun `chat page update goes to the assistant before the conversation starts`() {
        val conversation = conversation()
        val updated = assistant.copy(reasoningLevel = ReasoningLevel.LOW)

        assertSame(conversation, conversation.withAssistantUpdate(updated, settings()))
        assertSame(updated, updated.withoutConversationFields(conversation, assistant))
    }

    @Test
    fun `chat page update stays on the started conversation`() {
        val bound = conversation().bindConfig(settings())
        val newInjection = Uuid.random()
        val updated = settings().getAssistantOf(bound).copy(
            reasoningLevel = ReasoningLevel.LOW,
            modeInjectionIds = setOf(newInjection),
            quickMessageIds = setOf(Uuid.random()),
        )

        val conversation = bound.withAssistantUpdate(updated, settings())
        val stored = updated.withoutConversationFields(bound, assistant)

        assertEquals(ReasoningLevel.LOW, conversation.config?.reasoningLevel)
        assertEquals(setOf(newInjection), conversation.modeInjectionIds)
        // 不随会话固定的字段仍然写回助手
        assertEquals(assistant.copy(quickMessageIds = updated.quickMessageIds), stored)
    }

    @Test
    fun `switching workspace resets the working directory`() {
        val bound = conversation().bindConfig(settings()).copy(workspaceCwd = "/workspace/project")
        val assistantView = settings().getAssistantOf(bound)

        val sameWorkspace = bound.withAssistantUpdate(assistantView.copy(reasoningLevel = ReasoningLevel.LOW), settings())
        val otherWorkspace = bound.withAssistantUpdate(assistantView.copy(workspaceId = Uuid.random()), settings())

        assertEquals("/workspace/project", sameWorkspace.workspaceCwd)
        assertNull(otherWorkspace.workspaceCwd)
    }

    @Test
    fun `conversation bound before per-conversation config keeps its own injections`() {
        val conversationInjection = Uuid.random()
        val conversation = conversation().copy(modeInjectionIds = setOf(conversationInjection))

        val bound = conversation.bindConfig(settings())

        // 会话自己绑定过的保留，没绑定过的继承助手
        assertEquals(setOf(conversationInjection), bound.modeInjectionIds)
        assertEquals(setOf(lorebook), bound.lorebookIds)
    }

    // ---- fork：跟随助手设置 ----

    @Test
    fun `following assistant ignores the snapshot and reads live settings`() {
        val bound = conversation().bindConfig(settings()).setFollowAssistant(settings(), true)
        val changed = assistant.copy(chatModelId = modelB.id, reasoningLevel = ReasoningLevel.OFF)

        val resolved = settings(changed).getAssistantOf(bound)

        assertEquals(ReasoningLevel.OFF, resolved.reasoningLevel)
        assertEquals(modelB.id, settings(changed).getChatModelOf(bound)?.id)
    }

    @Test
    fun `turning off follow re-snapshots the assistant`() {
        val bound = conversation().bindConfig(settings()).setFollowAssistant(settings(), true)
        val changed = assistant.copy(reasoningLevel = ReasoningLevel.OFF, useBuiltInSearch = false)

        val frozen = bound.setFollowAssistant(settings(changed), false)

        assertEquals(false, frozen.config?.followAssistant)
        assertEquals(ReasoningLevel.OFF, frozen.config?.reasoningLevel)
        assertEquals(false, frozen.config?.builtInSearch)
        // 之后不再随助手变化
        assertSame(frozen, frozen.bindConfig(settings(assistant)))
    }
}
