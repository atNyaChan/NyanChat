package me.rerere.rikkahub.ui.pages.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageAttachmentState
import me.rerere.rikkahub.data.db.fts.MessageSearchMode
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import kotlin.uuid.Uuid

private const val SORT_ORDER_PREF_KEY = "search_page_sort_order"
private const val SEARCH_MODE_PREF_KEY = "search_page_search_mode"
private const val PAGE_SIZE = 20

class SearchVM(
    private val context: Application,
    private val conversationRepo: ConversationRepository,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")

    var searchQuery by mutableStateOf("")
        private set
    var sortOrder by mutableStateOf(
        runCatching {
            MessageSearchSort.valueOf(
                context.readStringPreference(SORT_ORDER_PREF_KEY, MessageSearchSort.NEWEST_FIRST.name)!!
            )
        }.getOrDefault(MessageSearchSort.NEWEST_FIRST)
    )
        private set
    var results by mutableStateOf<List<MessageSearchResult>>(emptyList())
        private set
    var resultCount by mutableStateOf(0)
        private set
    var currentPage by mutableStateOf(1)
        private set
    val totalPages: Int
        get() = ((resultCount + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    var searchMode by mutableStateOf(
        runCatching {
            MessageSearchMode.valueOf(
                context.readStringPreference(SEARCH_MODE_PREF_KEY, MessageSearchMode.EXACT.name)!!
            )
        }.getOrDefault(MessageSearchMode.EXACT)
    )
        private set
    var selectedModel by mutableStateOf<Model?>(null)
        private set
    var selectedDeletedModelId by mutableStateOf<Uuid?>(null)
        private set
    var searchManuallyEditedMessages by mutableStateOf(false)
        private set
    var attachmentState by mutableStateOf<MessageAttachmentState?>(null)
        private set
    var deletedModelIds by mutableStateOf<List<Uuid>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRebuilding by mutableStateOf(false)
        private set

    init {
        if (searchMode != MessageSearchMode.FUZZY && sortOrder == MessageSearchSort.RELEVANCE) {
            sortOrder = MessageSearchSort.NEWEST_FIRST
            context.writeStringPreference(SORT_ORDER_PREF_KEY, sortOrder.name)
        }
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .collectLatest { query -> performSearch(query) }
        }
    }

    fun onQueryChange(query: String) {
        currentPage = 1
        selectedModel = null
        selectedDeletedModelId = null
        searchManuallyEditedMessages = false
        attachmentState = null
        searchQuery = query
        _searchQuery.value = query
    }

    fun onSortChange(sort: MessageSearchSort) {
        if (sort == MessageSearchSort.RELEVANCE &&
            (searchMode != MessageSearchMode.FUZZY || isModelFilteredSearch)
        ) {
            return
        }
        if (sortOrder == sort) return
        sortOrder = sort
        currentPage = 1
        context.writeStringPreference(SORT_ORDER_PREF_KEY, sort.name)
        viewModelScope.launch {
            performSearch(searchQuery)
        }
    }

    fun onSearchModeChange(mode: MessageSearchMode) {
        if (searchMode == mode && !isModelFilteredSearch) return
        searchMode = mode
        currentPage = 1
        selectedModel = null
        selectedDeletedModelId = null
        searchManuallyEditedMessages = false
        attachmentState = null
        context.writeStringPreference(SEARCH_MODE_PREF_KEY, mode.name)
        ensureValidSortOrder(mode == MessageSearchMode.FUZZY)
        viewModelScope.launch {
            performSearch(searchQuery)
        }
    }

    fun onModelSearch(model: Model) {
        currentPage = 1
        ensureValidSortOrder(false)
        selectedModel = model
        selectedDeletedModelId = null
        searchManuallyEditedMessages = false
        attachmentState = null
        searchQuery = ""
        _searchQuery.value = ""
        viewModelScope.launch { performSearch("") }
    }

    fun onDeletedModelSearch(modelId: Uuid) {
        currentPage = 1
        ensureValidSortOrder(false)
        selectedModel = null
        selectedDeletedModelId = modelId
        searchManuallyEditedMessages = false
        attachmentState = null
        searchQuery = ""
        _searchQuery.value = ""
        viewModelScope.launch { performSearch("") }
    }

    fun onManuallyEditedMessagesSearch() {
        currentPage = 1
        ensureValidSortOrder(false)
        selectedModel = null
        selectedDeletedModelId = null
        searchManuallyEditedMessages = true
        attachmentState = null
        searchQuery = ""
        _searchQuery.value = ""
        viewModelScope.launch { performSearch("") }
    }

    fun onAttachmentSearch(state: MessageAttachmentState) {
        currentPage = 1
        ensureValidSortOrder(false)
        selectedModel = null
        selectedDeletedModelId = null
        searchManuallyEditedMessages = false
        attachmentState = state
        searchQuery = ""
        _searchQuery.value = ""
        viewModelScope.launch { performSearch("") }
    }

    fun loadDeletedModelIds(existingModelIds: Set<Uuid>) {
        viewModelScope.launch {
            deletedModelIds = conversationRepo.getUsedMessageModelIds()
                .filterNot(existingModelIds::contains)
                .sortedBy { it.toString() }
        }
    }

    val hasSearchCriteria: Boolean
        get() = selectedModel != null ||
            selectedDeletedModelId != null ||
            searchManuallyEditedMessages ||
            attachmentState != null ||
            searchQuery.isNotBlank()

    val isModelFilteredSearch: Boolean
        get() = selectedModel != null ||
            selectedDeletedModelId != null ||
            searchManuallyEditedMessages ||
            attachmentState != null

    fun search() {
        viewModelScope.launch {
            performSearch(searchQuery)
        }
    }

    fun goToPage(page: Int) {
        val target = page.coerceIn(1, totalPages)
        if (target == currentPage) return
        currentPage = target
        viewModelScope.launch { performSearch(searchQuery) }
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

    private fun ensureValidSortOrder(allowRelevance: Boolean) {
        if (!allowRelevance && sortOrder == MessageSearchSort.RELEVANCE) {
            sortOrder = MessageSearchSort.NEWEST_FIRST
            context.writeStringPreference(SORT_ORDER_PREF_KEY, sortOrder.name)
        }
    }

    private suspend fun performSearch(query: String) {
        val model = selectedModel
        val deletedModelId = selectedDeletedModelId
        val manuallyEdited = searchManuallyEditedMessages
        val selectedAttachmentState = attachmentState
        if (
            model == null &&
            deletedModelId == null &&
            !manuallyEdited &&
            selectedAttachmentState == null &&
            query.isBlank()
        ) {
            results = emptyList()
            resultCount = 0
            return
        }
        isLoading = true
        try {
            resultCount = when {
                model != null -> conversationRepo.countMessagesByModel(model.id)
                deletedModelId != null -> conversationRepo.countMessagesByModel(deletedModelId)
                manuallyEdited -> conversationRepo.countManuallyEditedMessages()
                selectedAttachmentState != null ->
                    conversationRepo.countMessagesByAttachmentState(selectedAttachmentState)
                else -> conversationRepo.countSearchMessages(query, searchMode)
            }
            currentPage = currentPage.coerceIn(1, totalPages)
            val offset = (currentPage - 1) * PAGE_SIZE
            results = when {
                model != null -> conversationRepo.searchMessagesByModel(model.id, sortOrder, PAGE_SIZE, offset)
                deletedModelId != null ->
                    conversationRepo.searchMessagesByModel(deletedModelId, sortOrder, PAGE_SIZE, offset)
                manuallyEdited -> conversationRepo.searchManuallyEditedMessages(sortOrder, PAGE_SIZE, offset)
                selectedAttachmentState != null ->
                    conversationRepo.searchMessagesByAttachmentState(
                        selectedAttachmentState,
                        sortOrder,
                        PAGE_SIZE,
                        offset,
                    )
                else -> conversationRepo.searchMessages(query, sortOrder, searchMode, PAGE_SIZE, offset)
            }
        } finally {
            isLoading = false
        }
    }
}
