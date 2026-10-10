package me.rerere.rikkahub.ui.pages.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.fts.MessageAttachmentState
import me.rerere.rikkahub.data.db.fts.MessageSearchMode
import me.rerere.rikkahub.data.db.fts.MessageSearchRequest
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import kotlin.uuid.Uuid

private const val SORT_ORDER_PREF_KEY = "search_page_sort_order"
private const val SEARCH_MODE_PREF_KEY = "search_page_search_mode"
private const val PAGE_SIZE = 20

enum class SearchScope {
    ALL_ASSISTANTS,
    CURRENT_ASSISTANT,
}

/** 搜索页弹窗中编辑、确认后一起提交的搜索条件。 */
data class SearchConditions(
    val query: String = "",
    val mode: MessageSearchMode = MessageSearchMode.EXACT,
    val sort: MessageSearchSort = MessageSearchSort.NEWEST_FIRST,
    val scope: SearchScope = SearchScope.ALL_ASSISTANTS,
    val selectedModel: Model? = null,
    val selectedDeletedModelId: Uuid? = null,
    val manuallyEdited: Boolean = false,
    val attachmentState: MessageAttachmentState? = null,
) {
    val hasMessageFilter: Boolean
        get() = selectedModel != null ||
            selectedDeletedModelId != null ||
            manuallyEdited ||
            attachmentState != null

    val hasAnyCriteria: Boolean
        get() = hasMessageFilter || query.isNotBlank()
}

class SearchVM(
    private val context: Application,
    private val conversationRepo: ConversationRepository,
    settingsStore: SettingsStore,
) : ViewModel() {
    private var currentAssistantId: Uuid? = null
    private var existingModelIds: Set<Uuid> = emptySet()

    var conditions by mutableStateOf(
        SearchConditions(
            mode = runCatching {
                MessageSearchMode.valueOf(
                    context.readStringPreference(SEARCH_MODE_PREF_KEY, MessageSearchMode.EXACT.name)!!
                )
            }.getOrDefault(MessageSearchMode.EXACT),
            sort = runCatching {
                MessageSearchSort.valueOf(
                    context.readStringPreference(SORT_ORDER_PREF_KEY, MessageSearchSort.NEWEST_FIRST.name)!!
                )
            }.getOrDefault(MessageSearchSort.NEWEST_FIRST),
        )
    )
        private set
    private var allResults by mutableStateOf<List<MessageSearchResult>>(emptyList())
    var results by mutableStateOf<List<MessageSearchResult>>(emptyList())
        private set
    var resultCount by mutableStateOf(0)
        private set
    var currentPage by mutableStateOf(1)
        private set
    val totalPages: Int
        get() = ((resultCount + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    var deletedModelIds by mutableStateOf<List<Uuid>>(emptyList())
        private set

    /** 聊天记录中出现过的模型 ID（与“已删除模型”使用同一筛选范围）。 */
    var usedModelIds by mutableStateOf<Set<Uuid>>(emptySet())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRebuilding by mutableStateOf(false)
        private set

    init {
        if (conditions.mode != MessageSearchMode.FUZZY &&
            conditions.sort == MessageSearchSort.RELEVANCE
        ) {
            conditions = conditions.copy(sort = MessageSearchSort.NEWEST_FIRST)
            context.writeStringPreference(SORT_ORDER_PREF_KEY, MessageSearchSort.NEWEST_FIRST.name)
        }
        viewModelScope.launch {
            settingsStore.settingsFlow
                .map { it.getCurrentAssistant().id }
                .distinctUntilChanged()
                .collect { assistantId ->
                    currentAssistantId = assistantId
                    if (conditions.scope == SearchScope.CURRENT_ASSISTANT) {
                        reloadDeletedModelIds()
                        if (conditions.hasAnyCriteria) {
                            performSearch()
                        }
                    }
                }
        }
    }

    val hasSearchCriteria: Boolean
        get() = conditions.hasAnyCriteria

    val assistantFilter: Uuid?
        get() = if (conditions.scope == SearchScope.CURRENT_ASSISTANT) currentAssistantId else null

    fun applyConditions(newConditions: SearchConditions) {
        val normalized = if (newConditions.mode != MessageSearchMode.FUZZY &&
            newConditions.sort == MessageSearchSort.RELEVANCE
        ) {
            newConditions.copy(sort = MessageSearchSort.NEWEST_FIRST)
        } else {
            newConditions
        }
        conditions = normalized
        currentPage = 1
        context.writeStringPreference(SORT_ORDER_PREF_KEY, normalized.sort.name)
        context.writeStringPreference(SEARCH_MODE_PREF_KEY, normalized.mode.name)
        viewModelScope.launch {
            reloadDeletedModelIds()
            performSearch()
        }
    }

    fun loadDeletedModelIds(existingModelIds: Set<Uuid>) {
        this.existingModelIds = existingModelIds
        viewModelScope.launch {
            reloadDeletedModelIds()
        }
    }

    private suspend fun reloadDeletedModelIds() {
        val usedIds = conversationRepo.getUsedMessageModelIds(assistantFilter)
        usedModelIds = usedIds.toSet()
        deletedModelIds = usedIds
            .filterNot(existingModelIds::contains)
            .sortedBy { it.toString() }
    }

    fun goToPage(page: Int) {
        val target = page.coerceIn(1, totalPages)
        if (target == currentPage) return
        currentPage = target
        results = pageSlice()
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            isRebuilding = true
            try {
                conversationRepo.rebuildAllIndexes()
            } finally {
                isRebuilding = false
            }
        }
    }

    private fun pageSlice(): List<MessageSearchResult> {
        val offset = (currentPage - 1) * PAGE_SIZE
        return allResults.drop(offset).take(PAGE_SIZE)
    }

    private suspend fun performSearch() {
        val current = conditions
        if (!current.hasAnyCriteria) {
            allResults = emptyList()
            results = emptyList()
            resultCount = 0
            return
        }
        isLoading = true
        try {
            val request = MessageSearchRequest(
                keyword = current.query,
                mode = current.mode,
                sort = current.sort,
                modelId = current.selectedModel?.id ?: current.selectedDeletedModelId,
                manuallyEdited = current.manuallyEdited,
                attachmentState = current.attachmentState,
                assistantId = assistantFilter?.toString(),
            )
            allResults = conversationRepo.searchMessages(request)
            resultCount = allResults.size
            currentPage = currentPage.coerceIn(1, totalPages)
            results = pageSlice()
        } finally {
            isLoading = false
        }
    }
}
