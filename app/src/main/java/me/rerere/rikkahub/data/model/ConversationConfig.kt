package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import kotlin.uuid.Uuid

/**
 * 会话开始后固定在会话上的配置。
 *
 * 新会话没有这份配置，聊天页里的切换直接改助手；会话落库时从助手拍一份快照，
 * 之后的切换只影响当前会话，别处的改动不会打乱它的上下文和缓存。
 * 模式注入和 Lorebook 同样随会话固定，沿用 [Conversation.modeInjectionIds] 与 [Conversation.lorebookIds]。
 *
 * fork 追加了 [followAssistant]：开启时该会话不使用本快照，而是实时跟随助手设置。
 * 该字段是 fork 私有的，上游不认识，但解码时 `ignoreUnknownKeys` 会忽略它，因此不影响上游读取本会话。
 */
@Serializable
data class ConversationConfig(
    val chatModelId: Uuid? = null,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val enableWebSearch: Boolean = false,
    val builtInSearch: Boolean = false, // fork 里对应 Assistant.useBuiltInSearch
    val mcpServers: Set<Uuid> = emptySet(),
    val workspaceId: Uuid? = null,
    val enabledSkills: Set<String> = emptySet(),
    // fork 私有：为 true 时该会话跟随助手的实时设置，本快照字段被忽略；默认 false（固定）
    val followAssistant: Boolean = false,
)

private fun Assistant.toConversationConfig() = ConversationConfig(
    chatModelId = chatModelId,
    reasoningLevel = reasoningLevel,
    enableWebSearch = enableWebSearch,
    builtInSearch = useBuiltInSearch,
    mcpServers = mcpServers,
    workspaceId = workspaceId,
    enabledSkills = enabledSkills,
)

private fun Assistant.withConversationConfig(config: ConversationConfig) = copy(
    chatModelId = config.chatModelId,
    reasoningLevel = config.reasoningLevel,
    enableWebSearch = config.enableWebSearch,
    useBuiltInSearch = config.builtInSearch,
    mcpServers = config.mcpServers,
    workspaceId = config.workspaceId,
    enabledSkills = config.enabledSkills,
)

/** 会话视角下的助手：会话开始后，会话持有的字段覆盖助手自身的值；开启跟随时忽略快照。 */
fun Assistant.withConversation(conversation: Conversation): Assistant {
    val config = conversation.config ?: return this
    if (config.followAssistant) return this
    return withConversationConfig(config).copy(
        modeInjectionIds = conversation.modeInjectionIds,
        lorebookIds = conversation.lorebookIds,
    )
}

/** 聊天页改动助手后留在会话上的部分，[updated] 是会话视角下改动后的助手。 */
fun Conversation.withAssistantUpdate(updated: Assistant, settings: Settings): Conversation {
    val previous = settings.getAssistantOf(this)
    val config = config
    val conversation = when {
        config == null -> this
        // 跟随助手时快照不参与，改动全部写回助手
        config.followAssistant -> this
        else -> copy(
            config = updated.toConversationConfig(),
            modeInjectionIds = updated.modeInjectionIds,
            lorebookIds = updated.lorebookIds,
        )
    }
    // 工作目录属于原来的工作区，换工作区后失效
    return if (updated.workspaceId != previous.workspaceId) conversation.copy(workspaceCwd = null) else conversation
}

/** 聊天页改动助手后写回助手设置的部分：会话持有的字段保持 [stored] 的原值。 */
fun Assistant.withoutConversationFields(conversation: Conversation, stored: Assistant): Assistant {
    val config = conversation.config
    // 未开始或跟随助手的会话，改动全部写回助手
    if (config == null || config.followAssistant) return this
    return withConversationConfig(stored.toConversationConfig()).copy(
        modeInjectionIds = stored.modeInjectionIds,
        lorebookIds = stored.lorebookIds,
    )
}

/** 会话所属助手自身的设置，不含会话上固定的配置；助手被删除后退回当前助手。 */
fun Settings.getStoredAssistantOf(conversation: Conversation): Assistant =
    getAssistantById(conversation.assistantId) ?: getCurrentAssistant()

/** 会话所属的助手（会话视角）。 */
fun Settings.getAssistantOf(conversation: Conversation): Assistant {
    val assistant = getStoredAssistantOf(conversation).withConversation(conversation)
    val config = conversation.config ?: return assistant
    // 跟随助手时直接使用助手自身的设置
    if (config.followAssistant) return assistant
    // 助手自身的失效引用在读取设置时已清理，会话上固定的这份在这里滤掉
    return assistant.copy(
        mcpServers = assistant.mcpServers.filterTo(mutableSetOf()) { id -> mcpServers.any { it.id == id } },
        modeInjectionIds = assistant.modeInjectionIds
            .filterTo(mutableSetOf()) { id -> modeInjections.any { it.id == id } },
        lorebookIds = assistant.lorebookIds.filterTo(mutableSetOf()) { id -> lorebooks.any { it.id == id } },
    )
}

/** 会话使用的聊天模型；跟随助手或配置缺失时以助手为准。 */
fun Settings.getChatModelOf(conversation: Conversation): Model? {
    val assistantModel = findModelById(getStoredAssistantOf(conversation).chatModelId ?: chatModelId)
    val config = conversation.config ?: return assistantModel
    if (config.followAssistant) return assistantModel
    // 固定的模型被删除后退回助手当前的模型
    return findModelById(config.chatModelId) ?: assistantModel
}

/**
 * 把助手当前的配置固定到会话上。已经固定过的会话原样返回，
 * 只有固定的模型被删除时才改为固定助手当前的模型，之后不再跟着助手变。
 */
fun Conversation.bindConfig(settings: Settings): Conversation {
    val model = settings.getChatModelOf(this)
    val config = config
    if (config != null) {
        if (model == null || model.id == config.chatModelId) return this
        return copy(config = config.copy(chatModelId = model.id))
    }
    val assistant = settings.getAssistantOf(this)
    return copy(
        config = assistant.toConversationConfig().copy(chatModelId = model?.id),
        // 旧版本允许会话单独绑定注入，这类会话保留自己的绑定
        modeInjectionIds = modeInjectionIds.ifEmpty { assistant.modeInjectionIds },
        lorebookIds = lorebookIds.ifEmpty { assistant.lorebookIds },
    )
}

/**
 * fork：切换会话是否跟随助手设置。
 *
 * 开启时仅把快照标记为跟随（快照字段被忽略）；关闭时用助手当前设置重新拍一份快照，
 * 之后该会话重新固定，不再随助手变化。
 */
fun Conversation.setFollowAssistant(settings: Settings, follow: Boolean): Conversation {
    if (follow) {
        val bound = bindConfig(settings)
        val config = bound.config ?: return bound
        if (config.followAssistant) return bound
        return bound.copy(config = config.copy(followAssistant = true))
    }
    val assistant = settings.getStoredAssistantOf(this)
    val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
    return copy(
        config = assistant.toConversationConfig().copy(followAssistant = false, chatModelId = model?.id),
        modeInjectionIds = assistant.modeInjectionIds,
        lorebookIds = assistant.lorebookIds,
    )
}
