package me.rerere.rikkahub.ui.pages.assistant

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.ChatService
import kotlin.uuid.Uuid

class AssistantVM(
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val conversationRepo: ConversationRepository,
    private val chatService: ChatService,
    private val filesManager: FilesManager,
    private val appScope: AppScope,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    fun updateSettings(fn: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(fn)
        }
    }

    fun addAssistant(assistant: Assistant) {
        viewModelScope.launch {
            settingsStore.update { it.copy(assistants = it.assistants.plus(assistant)) }
        }
    }

    fun removeAssistant(
        assistant: Assistant,
        onRemoved: (fallbackChatId: Uuid?) -> Unit = {},
    ) {
        // 助手从列表移除后用户无法再次触发清理，放到应用级作用域里，离开页面也要跑完
        appScope.launch {
            var removed: Assistant? = null
            var fallbackAssistantId: Uuid? = null
            // 在同一次更新里按最新列表检查，连续删除时不会把最后一个助手也删掉
            // 先落设置：设置类使用记录清理后再删文件，同一内容被别处引用的才不会被误删
            settingsStore.update { settings ->
                val target = settings.assistants.find { it.id == assistant.id }
                if (target == null || settings.assistants.size <= 1) return@update settings
                removed = target
                val remainingAssistants = settings.assistants.filter { it.id != target.id }
                if (settings.assistantId == target.id) {
                    fallbackAssistantId = remainingAssistants.firstOrNull()?.id
                }
                settings.copy(
                    assistants = remainingAssistants,
                    assistantId = fallbackAssistantId ?: settings.assistantId,
                )
            }
            // 只有真的删掉了才清理关联数据
            val target = removed ?: return@launch
            cleanupAssistantFiles(target)
            memoryRepository.deleteFilesOfAssistant(target.id.toString())
            chatService.deleteConversationsOfAssistant(target.id)
            val fallbackChatId = fallbackAssistantId?.let { Uuid.random() }
            onRemoved(fallbackChatId)
        }
    }

    private suspend fun cleanupAssistantFiles(assistant: Assistant) {
        val uris = buildList {
            (assistant.avatar as? Avatar.Image)?.let { add(it.url.toUri()) }
            assistant.background?.let { add(it.toUri()) }
        }

        if (uris.isNotEmpty()) {
            filesManager.deleteChatFiles(uris)
        }
    }

    fun copyMemories(fromAssistantId: String, toAssistantId: String) {
        viewModelScope.launch {
            memoryRepository.copyFiles(fromAssistantId, toAssistantId)
        }
    }

    suspend fun hasMemories(assistantId: String): Boolean =
        memoryRepository.getFiles(assistantId).isNotEmpty()

    /** 助手是否仍有对话（含已归入文件夹的对话）。 */
    suspend fun hasConversations(assistantId: Uuid): Boolean =
        conversationRepo.countConversationsOfAssistant(assistantId) > 0
}
