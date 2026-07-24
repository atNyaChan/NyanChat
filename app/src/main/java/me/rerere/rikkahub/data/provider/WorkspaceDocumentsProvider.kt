package me.rerere.rikkahub.data.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.workspace.WorkspaceManager
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * 通过 Storage Access Framework 暴露应用数据和工作区文件。
 *
 * ```
 * NyanChat
 * ├── data
 * └── workspaces
 *     └── {workspace name}
 * ```
 */
class WorkspaceDocumentsProvider : DocumentsProvider() {

    private fun manager(): WorkspaceManager = GlobalContext.get().get()

    private fun dao(): WorkspaceDAO = GlobalContext.get().get()

    private fun allWorkspaces(): List<WorkspaceEntity> = runBlocking { dao().getAll() }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val ctx = context ?: return cursor
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
            add(Root.COLUMN_TITLE, ctx.getString(R.string.app_name))
            add(
                Root.COLUMN_FLAGS,
                Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD,
            )
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(Root.COLUMN_MIME_TYPES, "*/*")
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        addTargetRow(cursor, parseDocId(documentId))
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = parseDocId(parentDocumentId)
        when (parent.kind) {
            TargetKind.ROOT -> {
                addTargetRow(cursor, Target(TargetKind.DATA))
                addTargetRow(cursor, Target(TargetKind.WORKSPACES))
            }

            TargetKind.WORKSPACES -> allWorkspaces().forEach { workspace ->
                manager().filesDir(workspace.root).mkdirs()
                addTargetRow(cursor, Target(TargetKind.WORKSPACE, workspace.root))
            }

            TargetKind.DATA, TargetKind.WORKSPACE -> {
                resolveFile(parent).takeIf(File::isDirectory)
                    ?.listFiles()
                    .orEmpty()
                    .filter { !it.name.startsWith(".l2s.") }
                    .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    .mapNotNull { child ->
                        runCatching { targetForFile(parent, child) }.getOrNull()
                    }
                    .forEach { child ->
                        addTargetRow(cursor, child)
                    }
            }
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val target = parseDocId(documentId)
        require(target.kind == TargetKind.DATA || target.kind == TargetKind.WORKSPACE)
        return ParcelFileDescriptor.open(resolveFile(target), ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = parseDocId(parentDocumentId)
        require(parent.kind == TargetKind.DATA || parent.kind == TargetKind.WORKSPACE)
        val parentDir = resolveFile(parent)
        require(parentDir.isDirectory)
        val targetFile = uniqueChild(parentDir, displayName)
        if (mimeType == Document.MIME_TYPE_DIR) {
            require(targetFile.mkdir())
        } else {
            require(targetFile.createNewFile())
        }
        notifyChange(parentDocumentId)
        return buildDocId(targetForFile(parent, targetFile))
    }

    override fun deleteDocument(documentId: String) {
        val target = parseDocId(documentId)
        require(!target.isProtected)
        val file = resolveFile(target)
        require(if (file.isDirectory) file.deleteRecursively() else file.delete())
        notifyChange(buildDocId(target.parent()))
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val target = parseDocId(documentId)
        require(!target.isProtected)
        val file = resolveFile(target)
        val destination = File(file.parentFile, sanitizeName(displayName))
        require(!destination.exists())
        require(file.renameTo(destination))
        notifyChange(buildDocId(target.parent()))
        return buildDocId(targetForFile(target.parent(), destination))
    }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        val source = parseDocId(sourceDocumentId)
        val targetParent = parseDocId(targetParentDocumentId)
        require(!source.isProtected)
        require(targetParent.kind == TargetKind.DATA || targetParent.kind == TargetKind.WORKSPACE)
        val sourceFile = resolveFile(source)
        val targetDir = resolveFile(targetParent)
        require(targetDir.isDirectory)
        val destination = uniqueChild(targetDir, sourceFile.name)
        require(!destination.canonicalPath.startsWith(sourceFile.canonicalPath + File.separator))
        require(sourceFile.copyRecursively(destination))
        notifyChange(targetParentDocumentId)
        return buildDocId(targetForFile(targetParent, destination))
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String?,
        targetParentDocumentId: String,
    ): String {
        val source = parseDocId(sourceDocumentId)
        val targetParent = parseDocId(targetParentDocumentId)
        require(!source.isProtected)
        require(targetParent.kind == TargetKind.DATA || targetParent.kind == TargetKind.WORKSPACE)
        val sourceFile = resolveFile(source)
        val targetDir = resolveFile(targetParent)
        require(targetDir.isDirectory)
        val destination = uniqueChild(targetDir, sourceFile.name)
        require(!destination.canonicalPath.startsWith(sourceFile.canonicalPath + File.separator))
        if (!sourceFile.renameTo(destination)) {
            require(sourceFile.copyRecursively(destination))
            require(if (sourceFile.isDirectory) sourceFile.deleteRecursively() else sourceFile.delete())
        }
        notifyChange(sourceParentDocumentId ?: buildDocId(source.parent()))
        notifyChange(targetParentDocumentId)
        return buildDocId(targetForFile(targetParent, destination))
    }

    override fun getDocumentType(documentId: String): String {
        val target = parseDocId(documentId)
        return if (target.kind == TargetKind.ROOT || target.kind == TargetKind.WORKSPACES) {
            Document.MIME_TYPE_DIR
        } else {
            mimeOf(resolveFile(target))
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = parseDocId(parentDocumentId)
        val child = parseDocId(documentId)
        if (parent.kind == TargetKind.ROOT) return child.kind != TargetKind.ROOT
        if (parent.kind == TargetKind.WORKSPACES) return child.kind == TargetKind.WORKSPACE
        if (parent.kind != child.kind || parent.workspaceRoot != child.workspaceRoot) return false
        if (parent.relativePath.isEmpty()) return child.relativePath.isNotEmpty()
        return child.relativePath.startsWith(parent.relativePath + "/")
    }

    private fun addTargetRow(cursor: MatrixCursor, target: Target) {
        val virtualDirectory = target.kind == TargetKind.ROOT || target.kind == TargetKind.WORKSPACES
        val file = if (virtualDirectory) null else resolveFile(target)
        val isDirectory = virtualDirectory || file?.isDirectory == true
        val flags = when {
            target.kind == TargetKind.ROOT || target.kind == TargetKind.WORKSPACES -> 0
            target.isProtected -> Document.FLAG_DIR_SUPPORTS_CREATE
            isDirectory -> Document.FLAG_DIR_SUPPORTS_CREATE or
                Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
                Document.FLAG_SUPPORTS_COPY or Document.FLAG_SUPPORTS_MOVE

            else -> Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME or Document.FLAG_SUPPORTS_COPY or
                Document.FLAG_SUPPORTS_MOVE
        }
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, buildDocId(target))
            add(Document.COLUMN_DISPLAY_NAME, displayName(target, file))
            add(Document.COLUMN_MIME_TYPE, if (isDirectory) Document.MIME_TYPE_DIR else mimeOf(file!!))
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_SIZE, if (isDirectory) null else file?.length())
            add(Document.COLUMN_LAST_MODIFIED, file?.lastModified())
        }
    }

    private fun displayName(target: Target, file: File?): String = when (target.kind) {
        TargetKind.ROOT -> context?.getString(R.string.app_name).orEmpty()
        TargetKind.DATA -> if (target.relativePath.isEmpty()) "data" else file?.name.orEmpty()
        TargetKind.WORKSPACES -> "workspaces"
        TargetKind.WORKSPACE -> if (target.relativePath.isEmpty()) {
            allWorkspaces().firstOrNull { it.root == target.workspaceRoot }?.name ?: target.workspaceRoot
        } else {
            file?.name.orEmpty()
        }
    }

    private fun resolveFile(target: Target): File {
        val base = when (target.kind) {
            TargetKind.DATA -> requireNotNull(context).applicationInfo.dataDir.let(::File)
            TargetKind.WORKSPACE -> manager().filesDir(target.workspaceRoot)
            else -> error("Virtual document has no backing file")
        }.canonicalFile
        base.mkdirs()
        if (target.relativePath.isEmpty()) return base
        val resolved = File(base, target.relativePath).canonicalFile
        require(resolved.path == base.path || resolved.path.startsWith(base.path + File.separator))
        return resolved
    }

    private fun targetForFile(parent: Target, file: File): Target {
        val base = resolveFile(parent.copy(relativePath = "")).canonicalFile
        val relativePath = file.canonicalFile.relativeTo(base).path.replace(File.separatorChar, '/')
        return parent.copy(relativePath = relativePath)
    }

    private fun parseDocId(documentId: String): Target = when {
        documentId == ROOT_DOC_ID -> Target(TargetKind.ROOT)
        documentId == DATA_DOC_ID -> Target(TargetKind.DATA)
        documentId.startsWith("$DATA_DOC_ID/") ->
            Target(TargetKind.DATA, relativePath = documentId.removePrefix("$DATA_DOC_ID/"))

        documentId == WORKSPACES_DOC_ID -> Target(TargetKind.WORKSPACES)
        documentId.startsWith(WORKSPACE_PREFIX) -> {
            val path = documentId.removePrefix(WORKSPACE_PREFIX)
            val root = path.substringBefore('/')
            require(root.isNotBlank())
            Target(
                kind = TargetKind.WORKSPACE,
                workspaceRoot = root,
                relativePath = path.substringAfter('/', ""),
            )
        }

        else -> error("Invalid documentId: $documentId")
    }

    private fun buildDocId(target: Target): String = when (target.kind) {
        TargetKind.ROOT -> ROOT_DOC_ID
        TargetKind.DATA -> if (target.relativePath.isEmpty()) DATA_DOC_ID else "$DATA_DOC_ID/${target.relativePath}"
        TargetKind.WORKSPACES -> WORKSPACES_DOC_ID
        TargetKind.WORKSPACE -> buildString {
            append(WORKSPACE_PREFIX)
            append(target.workspaceRoot)
            if (target.relativePath.isNotEmpty()) {
                append('/')
                append(target.relativePath)
            }
        }
    }

    private fun mimeOf(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        return file.extension.lowercase().takeIf(String::isNotEmpty)
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: "application/octet-stream"
    }

    private fun uniqueChild(parent: File, displayName: String): File {
        val safeName = sanitizeName(displayName)
        var candidate = File(parent, safeName)
        if (!candidate.exists()) return candidate
        val stem = candidate.nameWithoutExtension
        val extension = candidate.extension.let { if (it.isEmpty()) "" else ".$it" }
        var index = 1
        do {
            candidate = File(parent, "$stem ($index)$extension")
            index++
        } while (candidate.exists())
        return candidate
    }

    private fun sanitizeName(name: String): String = name.replace('/', '_').ifBlank { "untitled" }

    private fun notifyChange(parentDocumentId: String) {
        val ctx = context ?: return
        ctx.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(
                ctx.packageName + ".documents",
                parentDocumentId,
            ),
            null,
        )
    }

    private enum class TargetKind {
        ROOT,
        DATA,
        WORKSPACES,
        WORKSPACE,
    }

    private data class Target(
        val kind: TargetKind,
        val workspaceRoot: String = "",
        val relativePath: String = "",
    ) {
        val isProtected: Boolean
            get() = kind == TargetKind.ROOT ||
                kind == TargetKind.WORKSPACES ||
                relativePath.isEmpty()

        fun parent(): Target = when {
            kind == TargetKind.DATA && relativePath.isEmpty() -> Target(TargetKind.ROOT)
            kind == TargetKind.WORKSPACE && relativePath.isEmpty() -> Target(TargetKind.WORKSPACES)
            else -> copy(relativePath = relativePath.substringBeforeLast('/', ""))
        }
    }

    companion object {
        private const val ROOT_ID = "nyanchat_files"
        private const val ROOT_DOC_ID = "root"
        private const val DATA_DOC_ID = "data"
        private const val WORKSPACES_DOC_ID = "workspaces"
        private const val WORKSPACE_PREFIX = "workspace/"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
