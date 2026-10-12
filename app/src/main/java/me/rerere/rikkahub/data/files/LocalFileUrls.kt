package me.rerere.rikkahub.data.files

import android.net.Uri
import androidx.core.net.toFile
import java.io.File
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar

/**
 * 本地附件的「落盘形式」与「运行形式」之间的转换。
 *
 * - 落盘形式：相对 filesDir 的路径（例如 `upload/<sha256>`），与包名无关，备份可跨安装迁移。
 * - 运行形式：当前安装下的绝对 `file://` url。
 *
 * 内存里的 [UIMessage] / [Settings] 一律持有运行形式；数据库与 DataStore 只保存落盘形式。
 * 导入存档时由 [me.rerere.rikkahub.data.sync.ManagedFilesArchive] 把旧绝对路径改写成落盘形式。
 * 兼容旧版 ZIP 导出时用 [toUpstreamAbsolute] / [withUpstreamLocalFiles] 改回上游安装目录下的绝对 url。
 */
object LocalFileUrls {
    private const val UPLOAD_FOLDER = FileFolders.UPLOAD
    private const val FILES_MARKER = "/files/"

    /** 运行形式 -> 落盘形式。只处理 filesDir 下的 `file://`，其它（http/data/content/已是相对）原样返回。 */
    fun toStored(filesDir: File, url: String): String {
        if (url.isBlank()) return url
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        when (uri.scheme) {
            null -> return url
            "file" -> Unit
            else -> return url
        }
        val file = runCatching { uri.toFile() }.getOrNull()
        if (file != null) {
            relativeToFilesDir(filesDir, file)?.let { return it }
        }
        // 旧数据或跨安装的绝对路径：按文件名在当前安装的 upload 下找回相对路径
        val name = uri.lastPathSegment ?: return url
        if (name.isNotBlank() && File(filesDir, "$UPLOAD_FOLDER/$name").exists()) {
            return "$UPLOAD_FOLDER/$name"
        }
        return url
    }

    /**
     * 落盘形式 -> 运行形式。库里只保存相对路径，带 scheme 的值（http/data/content 及任何遗留绝对
     * `file://`）一律原样返回，不做兼容回退。
     */
    fun toRuntime(filesDir: File, url: String): String {
        if (url.isBlank()) return url
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        if (uri.scheme != null) return url
        return Uri.fromFile(File(filesDir, url)).toString()
    }

    /**
     * 兼容旧版 ZIP 导出：把落盘形式（`upload/<sha256>`）或旧绝对路径改写成上游安装目录下的绝对 url。
     * [filenameBySha] 非空时把 `upload/<sha256>` 映射成该内容在旧布局里的 `upload/<uuid.ext>` 文件名。
     */
    fun toUpstreamAbsolute(
        upstreamFilesDir: File,
        url: String,
        filenameBySha: Map<String, String>? = null,
    ): String {
        if (url.isBlank()) return url
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val relative = when (uri.scheme) {
            null -> url
            "file" -> relativeFromAbsolutePath(uri.path) ?: return url
            else -> return url
        }
        return Uri.fromFile(File(upstreamFilesDir, mapShaToLegacyName(relative, filenameBySha))).toString()
    }

    /** 把 `upload/<sha256>` 转成 `upload/<uuid.ext>`；非附件路径或映射缺失时原样返回。 */
    private fun mapShaToLegacyName(relative: String, filenameBySha: Map<String, String>?): String {
        if (filenameBySha == null || !relative.startsWith("$UPLOAD_FOLDER/")) return relative
        val filename = filenameBySha[relative.substringAfterLast('/')] ?: return relative
        return "$UPLOAD_FOLDER/$filename"
    }

    /**
     * 文件绝对路径相对 [filesDir] 的部分。用 [File.absolutePath] 而不是 `canonicalPath`，
     * 保留路径里原始的文件名。
     */
    fun relativeToFilesDir(filesDir: File, file: File): String? {
        val base = filesDir.absolutePath.trimEnd(File.separatorChar)
        val path = file.absolutePath
        if (!path.startsWith("$base${File.separator}")) return null
        return path.substring(base.length + 1).replace(File.separatorChar, '/')
    }

    private fun relativeFromAbsolutePath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val index = path.lastIndexOf(FILES_MARKER)
        if (index < 0) return null
        return path.substring(index + FILES_MARKER.length).takeIf { it.isNotBlank() }
    }
}

fun UIMessage.withStoredLocalUrls(filesDir: File): UIMessage =
    copy(parts = parts.map { it.mapLocalUrl { url -> LocalFileUrls.toStored(filesDir, url) } })

fun UIMessage.withRuntimeLocalUrls(filesDir: File): UIMessage =
    copy(parts = parts.map { it.mapLocalUrl { url -> LocalFileUrls.toRuntime(filesDir, url) } })

fun List<UIMessage>.withStoredLocalUrls(filesDir: File): List<UIMessage> =
    map { it.withStoredLocalUrls(filesDir) }

fun List<UIMessage>.withRuntimeLocalUrls(filesDir: File): List<UIMessage> =
    map { it.withRuntimeLocalUrls(filesDir) }

fun Avatar.withStoredLocalFiles(filesDir: File): Avatar =
    if (this is Avatar.Image) Avatar.Image(LocalFileUrls.toStored(filesDir, url)) else this

fun Avatar.withRuntimeLocalFiles(filesDir: File): Avatar =
    if (this is Avatar.Image) Avatar.Image(LocalFileUrls.toRuntime(filesDir, url)) else this

fun Avatar.withUpstreamLocalFiles(upstreamFilesDir: File, filenameBySha: Map<String, String>? = null): Avatar =
    if (this is Avatar.Image) Avatar.Image(LocalFileUrls.toUpstreamAbsolute(upstreamFilesDir, url, filenameBySha)) else this

fun Assistant.withStoredLocalFiles(filesDir: File): Assistant = copy(
    avatar = avatar.withStoredLocalFiles(filesDir),
    background = background?.let { LocalFileUrls.toStored(filesDir, it) },
)

fun Assistant.withRuntimeLocalFiles(filesDir: File): Assistant = copy(
    avatar = avatar.withRuntimeLocalFiles(filesDir),
    background = background?.let { LocalFileUrls.toRuntime(filesDir, it) },
)

fun Settings.withStoredLocalFiles(filesDir: File): Settings = copy(
    assistants = assistants.map { it.withStoredLocalFiles(filesDir) },
    displaySetting = displaySetting.copy(
        userAvatar = displaySetting.userAvatar.withStoredLocalFiles(filesDir),
    ),
)

fun Settings.withRuntimeLocalFiles(filesDir: File): Settings = copy(
    assistants = assistants.map { it.withRuntimeLocalFiles(filesDir) },
    displaySetting = displaySetting.copy(
        userAvatar = displaySetting.userAvatar.withRuntimeLocalFiles(filesDir),
    ),
)

/**
 * 设置里（助手头像、聊天背景、用户头像）引用到的附件 url，用于判断设置附件是否变化、
 * 是否需要重建 [FilesManager.updateSettingsAttachmentUsages]。
 */
fun Settings.attachmentUrls(): List<String> = buildList {
    assistants.forEach { assistant ->
        (assistant.avatar as? Avatar.Image)?.url?.let(::add)
        assistant.background?.let(::add)
    }
    (displaySetting.userAvatar as? Avatar.Image)?.url?.let(::add)
}

/** 兼容旧版 ZIP 导出：把助手头像、聊天背景与用户头像改写成上游安装目录下的绝对 url。 */
fun Settings.withUpstreamLocalFiles(
    upstreamFilesDir: File,
    filenameBySha: Map<String, String>? = null,
): Settings = copy(
    assistants = assistants.map { assistant ->
        assistant.copy(
            avatar = assistant.avatar.withUpstreamLocalFiles(upstreamFilesDir, filenameBySha),
            background = assistant.background?.let {
                LocalFileUrls.toUpstreamAbsolute(upstreamFilesDir, it, filenameBySha)
            },
        )
    },
    displaySetting = displaySetting.copy(
        userAvatar = displaySetting.userAvatar.withUpstreamLocalFiles(upstreamFilesDir, filenameBySha),
    ),
)

private fun UIMessagePart.mapLocalUrl(transform: (String) -> String): UIMessagePart = when (this) {
    is UIMessagePart.Image -> copy(url = transform(url))
    is UIMessagePart.Video -> copy(url = transform(url))
    is UIMessagePart.Audio -> copy(url = transform(url))
    is UIMessagePart.Document -> copy(url = transform(url))
    is UIMessagePart.Tool -> copy(output = output.map { it.mapLocalUrl(transform) })
    else -> this
}
