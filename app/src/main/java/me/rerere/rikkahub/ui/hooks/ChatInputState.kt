package me.rerere.rikkahub.ui.hooks

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

class ChatInputState {
    val textContent = TextFieldState()
    private val messageContentState = mutableStateOf<List<UIMessagePart>>(emptyList())
    var messageContent: List<UIMessagePart>
        get() = messageContentState.value
        set(value) {
            messageContentState.value = value
        }
    var editingMessage by mutableStateOf<Uuid?>(null)

    /** 编辑中的消息允许改思考内容时不为 null，见 [setContents] */
    var editingReasoning by mutableStateOf<TextFieldState?>(null)
        private set

    /** 编辑中的思考带有签名或加密内容（metadata），确认编辑后要丢弃它 */
    var editingReasoningEncoded by mutableStateOf(false)
        private set
    private var dropReasoningMetadata by mutableStateOf(false)
    private var editingParts: List<UIMessagePart>? = null
    private var editingAttachmentUrls: Set<String> = emptySet()

    fun clearInput() {
        textContent.setTextAndPlaceCursorAtEnd("")
        messageContent = emptyList()
        editingMessage = null
        editingReasoning = null
        editingReasoningEncoded = false
        dropReasoningMetadata = false
        editingParts = null
        editingAttachmentUrls = emptySet()
    }

    fun isEditing() = editingMessage != null

    fun setMessageText(text: String) {
        textContent.setTextAndPlaceCursorAtEnd(text)
    }

    fun appendText(content: String) {
        textContent.setTextAndPlaceCursorAtEnd(textContent.text.toString() + content)
    }

    fun setContents(contents: List<UIMessagePart>) {
        val lastTextIndex = contents.indexOfLast { it is UIMessagePart.Text }
        val text = if (lastTextIndex >= 0) {
            (contents[lastTextIndex] as UIMessagePart.Text).text
        } else {
            ""
        }
        textContent.setTextAndPlaceCursorAtEnd(text)
        val reasoning = contents.editableReasoningOrNull()
        editingReasoning = reasoning?.let { TextFieldState(it.reasoning) }
        editingReasoningEncoded = reasoning != null && !reasoning.metadata.isNullOrEmpty()
        dropReasoningMetadata = false
        messageContent = contents.filter {
            it !is UIMessagePart.Text && (reasoning == null || it !is UIMessagePart.Reasoning)
        }
        editingParts = contents
        editingAttachmentUrls = contents.mapNotNull { it.attachmentUrlOrNull() }.toSet()
    }

    /** 确认编辑带签名或加密内容的思考：之后的编辑结果不再带原来的 metadata */
    fun dropEditingReasoningMetadata() {
        dropReasoningMetadata = true
        editingReasoningEncoded = false
    }

    fun getContents(): List<UIMessagePart> {
        val text = textContent.text.toString()
        if (isEditing()) {
            val originalParts = editingParts
            if (originalParts != null) {
                val editedTextIndex = originalParts.indexOfLast { it is UIMessagePart.Text }
                val remainingAttachments = messageContent.toMutableList()
                val merged = mutableListOf<UIMessagePart>()

                originalParts.forEachIndexed { index, part ->
                    when {
                        index == editedTextIndex -> {
                            if (text.isNotEmpty()) {
                                merged.add(UIMessagePart.Text(text))
                            }
                        }

                        part is UIMessagePart.Text -> {
                            if (part.text.isNotEmpty()) {
                                merged.add(part)
                            }
                        }

                        part is UIMessagePart.Reasoning && editingReasoning != null -> {
                            val reasoning = editingReasoning!!.text.toString()
                            if (reasoning.isNotBlank()) {
                                merged.add(
                                    part.copy(
                                        reasoning = reasoning,
                                        metadata = if (dropReasoningMetadata) null else part.metadata,
                                    )
                                )
                            }
                        }

                        else -> {
                            val currentIndex = remainingAttachments.indexOf(part)
                            if (currentIndex >= 0) {
                                merged.add(remainingAttachments.removeAt(currentIndex))
                            }
                        }
                    }
                }
                if (editedTextIndex < 0 && text.isNotEmpty()) {
                    merged.add(0, UIMessagePart.Text(text))
                }
                // Newly added attachments are appended in insertion order.
                merged.addAll(remainingAttachments)
                return merged
            }
            return if (text.isEmpty()) messageContent else listOf(UIMessagePart.Text(text)) + messageContent
        }
        return if (text.isEmpty()) messageContent else listOf(UIMessagePart.Text(text)) + messageContent
    }

    fun isEmpty(): Boolean {
        return textContent.text.isEmpty() && messageContent.isEmpty()
    }

    fun addImages(uris: List<Uri>) {
        val newMessage = messageContent.toMutableList()
        uris.forEach { uri ->
            newMessage.add(UIMessagePart.Image(uri.toString()))
        }
        messageContent = newMessage
    }

    fun addVideos(uris: List<Uri>) {
        val newMessage = messageContent.toMutableList()
        uris.forEach { uri ->
            newMessage.add(UIMessagePart.Video(uri.toString()))
        }
        messageContent = newMessage
    }

    fun addAudios(uris: List<Uri>) {
        val newMessage = messageContent.toMutableList()
        uris.forEach { uri ->
            newMessage.add(UIMessagePart.Audio(uri.toString()))
        }
        messageContent = newMessage
    }

    fun addFiles(uris: List<UIMessagePart.Document>) {
        val newMessage = messageContent.toMutableList()
        uris.forEach {
            newMessage.add(it)
        }
        messageContent = newMessage
    }

    /**
     * 仅删除当前输入组件临时新增的本地文件。
     * 编辑历史消息时，原有附件不在这里删除，由会话层统一做差异清理。
     */
    fun shouldDeleteFileOnRemove(part: UIMessagePart): Boolean {
        val url = part.attachmentUrlOrNull() ?: return false
        if (!url.startsWith("file://")) return false
        return !isEditing() || url !in editingAttachmentUrls
    }

    /** 输入框里当前附件（图片/视频/音频/文档）的 url，用于新增附件按内容去重。 */
    fun attachmentUrls(): List<String> = messageContent.mapNotNull { it.attachmentUrlOrNull() }

    /**
     * 只有一段思考加正文的消息才允许改思考内容。
     * 带 metadata 的思考（Claude/Gemini 签名、OpenAI 加密内容等）回传时要和原文对得上，
     * fork 允许确认后编辑，编辑结果会丢掉这些 metadata，见 [dropEditingReasoningMetadata]。
     */
    private fun List<UIMessagePart>.editableReasoningOrNull(): UIMessagePart.Reasoning? {
        if (!all { it is UIMessagePart.Text || it is UIMessagePart.Reasoning }) return null
        return filterIsInstance<UIMessagePart.Reasoning>().singleOrNull()
    }

    private fun UIMessagePart.attachmentUrlOrNull(): String? {
        return when (this) {
            is UIMessagePart.Image -> this.url
            is UIMessagePart.Video -> this.url
            is UIMessagePart.Audio -> this.url
            is UIMessagePart.Document -> this.url
            else -> null
        }
    }

}
