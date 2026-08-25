package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.workspace.RootfsInstallProgress

class WorkspaceVM(
    private val repository: WorkspaceRepository,
    private val settingsStore: SettingsStore,
    private val terminalSessionManager: WorkspaceTerminalSessionManager,
) : ViewModel() {
    val workspaces = repository.listFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { repository.create(name) }
        }
    }

    fun checkIntegrity() {
        viewModelScope.launch {
            repository.checkIntegrity()
        }
    }

    fun reorderWorkspaces(workspaces: List<WorkspaceEntity>) {
        viewModelScope.launch {
            settingsStore.update { it.copy(workspaceOrder = workspaces.map(WorkspaceEntity::id)) }
        }
    }

    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, settingsStore.settingsFlow.value)

}
