package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid

private val forkSuffixRegex = Regex("""\((\d+)\)$""")

/** 拆出末尾 ` (N)` 后缀：返回不含后缀的基础名与下一次可用的起始序号。 */
private fun forkSuffixParts(sourceName: String): Pair<String, Int> {
    // 源名称已带 (N) 后缀时递增序号，避免多次 fork 后叠加成 xxx (1) (1)
    val suffix = forkSuffixRegex.find(sourceName)
    val stem = suffix?.let { sourceName.removeRange(it.range).trimEnd() } ?: sourceName
    val start = suffix?.groupValues?.get(1)?.toIntOrNull()?.plus(1) ?: 1
    return stem to start
}

private fun nextForkName(start: Int, existingNames: Set<String>, build: (Int) -> String): String =
    generateSequence(start) { it + 1 }.map(build).first { it !in existingNames }

internal fun forkConversationTitle(sourceTitle: String, existingTitles: Set<String>): String {
    val (stem, start) = forkSuffixParts(sourceTitle)
    return nextForkName(start, existingTitles) { "$stem ($it)" }
}

/**
 * 从开头复制到 [messageId] 所在的节点（含），得到一个新会话；没有这条消息时返回 null。
 *
 * 继承助手、系统提示词、提示词注入、会话配置、工作目录和文件夹，标题在 [existingTitles] 中顺延编号。
 * 节点换新 id；本地附件经 [copyFile] 复制一份，两个会话之后各删各的文件，互不影响。
 *
 * fork：附件是内容寻址的，调用方传恒等映射（`{ it }`）复用同一实体，使用记录会随新会话一起写入。
 */
internal fun Conversation.fork(
    messageId: Uuid,
    existingTitles: Set<String>,
    copyFile: (url: String) -> String,
): Conversation? {
    val nodeIndex = messageNodes.indexOfFirst { node -> node.messages.any { it.id == messageId } }
    if (nodeIndex == -1) return null
    return Conversation(
        id = Uuid.random(),
        assistantId = assistantId,
        title = forkConversationTitle(title, existingTitles),
        messageNodes = messageNodes.take(nodeIndex + 1).map { node ->
            node.copy(
                id = Uuid.random(),
                messages = node.messages.map { message ->
                    message.copy(parts = message.parts.map { it.mapLocalFileUrls(copyFile) })
                },
            )
        },
        customSystemPrompt = customSystemPrompt,
        modeInjectionIds = modeInjectionIds,
        lorebookIds = lorebookIds,
        config = config,
        workspaceCwd = workspaceCwd,
        folderId = folderId,
    )
}
