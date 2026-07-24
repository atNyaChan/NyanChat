package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import kotlin.uuid.Uuid

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager,
    private val conversationRepository: ConversationRepository,
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun migrateMessageModelId(
        sourceModelId: Uuid,
        targetModelId: Uuid,
        onComplete: (Result<Int>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(
                runCatching {
                    conversationRepository.migrateMessageModelId(sourceModelId, targetModelId)
                }
            )
        }
    }
}
