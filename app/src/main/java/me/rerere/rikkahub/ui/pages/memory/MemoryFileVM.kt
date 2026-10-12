package me.rerere.rikkahub.ui.pages.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.MemoryWriteResult

sealed interface MemoryFileState {
    data object Loading : MemoryFileState

    /** 文件不存在：路径不合法，或者已经被删掉 */
    data object Missing : MemoryFileState

    data class Loaded(val file: MemoryFile) : MemoryFileState
}

/**
 * 单个记忆文件的查看与编辑。
 *
 * [memoryId] 是记忆库的 id（助手 id 或全局记忆库），[path] 为 null 表示新建。
 */
class MemoryFileVM(
    private val memoryId: String,
    path: String?,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {
    // 当前文件的路径；编辑页允许改路径，保存成功后跟着变
    private val currentPath = MutableStateFlow(path?.let { MemoryFile.normalizePath(it) })

    val state: StateFlow<MemoryFileState> = currentPath
        .flatMapLatest { normalizedPath ->
            if (normalizedPath == null) {
                flowOf(MemoryFileState.Missing)
            } else {
                memoryRepository.getFilesFlow(memoryId).map { files ->
                    files.find { it.path == normalizedPath }?.let(MemoryFileState::Loaded)
                        ?: MemoryFileState.Missing
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MemoryFileState.Loading)

    /** 新建文件，路径已被占用时返回 [MemoryWriteResult.Conflict] */
    fun create(path: String, content: String, onResult: (MemoryWriteResult) -> Unit) {
        viewModelScope.launch {
            onResult(memoryRepository.writeFile(memoryId, path, content, MemoryRepository.NEW_FILE_VERSION))
        }
    }

    /** 保存已有文件；[newPath] 与当前路径不同时在同一个写入里重命名 */
    fun save(newPath: String, content: String, onResult: (MemoryWriteResult) -> Unit) {
        val fromPath = currentPath.value ?: return
        val toPath = MemoryFile.normalizePath(newPath) ?: return
        viewModelScope.launch {
            val result = memoryRepository.saveFile(memoryId, fromPath, toPath, content)
            if (result is MemoryWriteResult.Success) {
                currentPath.value = toPath
            }
            onResult(result)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val path = currentPath.value ?: return
        viewModelScope.launch {
            memoryRepository.deleteFile(memoryId, path)
            onDeleted()
        }
    }
}
