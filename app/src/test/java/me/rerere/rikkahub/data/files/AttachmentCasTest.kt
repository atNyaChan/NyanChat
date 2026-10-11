package me.rerere.rikkahub.data.files

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentCasTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var filesRoot: File
    private lateinit var cacheDir: File

    private fun setUpRoots() {
        filesRoot = folder.newFolder("files")
        cacheDir = folder.newFolder("cache")
    }

    /** 把内容写入 cas 临时文件并建软链接, 返回 (软链接文件, sha256)。 */
    private fun link(content: String, linkName: String): Pair<File, String> {
        val cas = AttachmentCas.casDir(filesRoot).apply { mkdirs() }
        val (temporary, sha256) = AttachmentCas.writeTemporary(cas) { it.write(content.toByteArray()) }
        return AttachmentCas.linkEntity(filesRoot, sha256, linkName, temporary) to sha256
    }

    private fun entity(sha256: String) = File(AttachmentCas.casDir(filesRoot), sha256)

    private fun entry(
        fileName: String,
        conversationId: String = "conv",
        nodeId: String = "node",
        messageId: String = "message",
        folderId: String? = null,
        assistantId: String? = null,
    ) = ChatAttachmentEntry(
        fileName = fileName,
        conversationId = conversationId,
        nodeId = nodeId,
        messageId = messageId,
        folderId = folderId,
        assistantId = assistantId,
    )

    @Test
    fun `identical content shares one entity while every link resolves`() {
        setUpRoots()

        val (linkA, shaA) = link("shared payload", "a.bin")
        val (linkB, shaB) = link("shared payload", "b.bin")

        assertEquals(shaA, shaB)
        assertEquals(1, AttachmentCas.casDir(filesRoot).listFiles().orEmpty().count { it.isFile })
        assertTrue(AttachmentCas.isSymlink(linkA))
        assertTrue(AttachmentCas.isSymlink(linkB))
        // 软链接用相对路径指向 upload 同级的 upload-cas, 以便整体搬迁
        assertEquals("../${FileFolders.UPLOAD_CAS}/$shaA", Files.readSymbolicLink(linkA.toPath()).toString())
        assertEquals("shared payload", linkA.readText())
        assertEquals("shared payload", linkB.readText())
    }

    @Test
    fun `entity is deleted only after the last link is removed`() {
        setUpRoots()

        val (linkA, shaA) = link("payload", "a.bin")
        val (linkB, _) = link("payload", "b.bin")
        val casEntity = entity(shaA)

        linkA.delete()
        AttachmentCas.removeLinks(cacheDir, filesRoot, listOf(AttachmentCas.LinkRef(shaA, "a.bin")))
        assertTrue(casEntity.exists())
        assertTrue(linkB.exists())

        linkB.delete()
        AttachmentCas.removeLinks(cacheDir, filesRoot, listOf(AttachmentCas.LinkRef(shaA, "b.bin")))
        assertFalse(casEntity.exists())
    }

    @Test
    fun `removeLinks keeps an entity that is still referenced when the index is missing`() {
        setUpRoots()

        val (linkA, shaA) = link("payload", "a.bin")
        val (linkB, _) = link("payload", "b.bin")
        val casEntity = entity(shaA)

        // 索引不存在时仍以磁盘上的软链接为准
        assertFalse(AttachmentCas.indexFile(cacheDir).isFile)
        linkA.delete()
        AttachmentCas.removeLinks(cacheDir, filesRoot, listOf(AttachmentCas.LinkRef(shaA, "a.bin")))

        assertTrue("entity still referenced by b.bin", casEntity.exists())
        assertTrue(linkB.exists())
        // 索引缺失时不在删除路径里写出不完整的索引, 等下次访问时完整重建
        assertFalse(AttachmentCas.indexFile(cacheDir).isFile)
    }

    @Test
    fun `removeLinks deletes the entity only when no link references it on disk`() {
        setUpRoots()

        val (linkA, shaA) = link("payload", "a.bin")
        val (linkB, _) = link("payload", "b.bin")
        val casEntity = entity(shaA)

        // 只删掉其中一条软链接时实体必须保留, 删掉最后一条才删除实体
        linkB.delete()
        AttachmentCas.removeLinks(cacheDir, filesRoot, listOf(AttachmentCas.LinkRef(shaA, "b.bin")))
        assertTrue(casEntity.exists())

        linkA.delete()
        AttachmentCas.removeLinks(cacheDir, filesRoot, listOf(AttachmentCas.LinkRef(shaA, "a.bin")))
        assertFalse(casEntity.exists())
    }

    @Test
    fun `convertUploadFolderToCas dedups real files into entities and relative symlinks`() {
        setUpRoots()

        val upload = AttachmentCas.uploadDir(filesRoot).apply { mkdirs() }
        File(upload, "x.bin").writeText("same")
        File(upload, "y.bin").writeText("same")
        File(upload, "z.bin").writeText("other")

        AttachmentCas.convertUploadFolderToCas(filesRoot)

        val shaX = AttachmentCas.readSha(File(upload, "x.bin"))
        val shaY = AttachmentCas.readSha(File(upload, "y.bin"))
        val shaZ = AttachmentCas.readSha(File(upload, "z.bin"))
        assertEquals(shaX, shaY)
        assertNotEquals(shaX, shaZ)
        assertTrue(AttachmentCas.isSymlink(File(upload, "x.bin")))
        assertEquals(2, AttachmentCas.casDir(filesRoot).listFiles().orEmpty().count { it.isFile })
        assertEquals("same", File(upload, "x.bin").readText())
        assertEquals("other", File(upload, "z.bin").readText())
    }

    @Test
    fun `convertUploadFolderToCas is idempotent`() {
        setUpRoots()

        val upload = AttachmentCas.uploadDir(filesRoot).apply { mkdirs() }
        File(upload, "x.bin").writeText("same")
        AttachmentCas.convertUploadFolderToCas(filesRoot)
        val sha = AttachmentCas.readSha(File(upload, "x.bin"))

        AttachmentCas.convertUploadFolderToCas(filesRoot)

        assertEquals(sha, AttachmentCas.readSha(File(upload, "x.bin")))
        assertEquals(1, AttachmentCas.casDir(filesRoot).listFiles().orEmpty().count { it.isFile })
    }

    @Test
    fun `index stores every message reference and sorts by file name`() {
        setUpRoots()

        val (_, shaA) = link("payload", "a.bin")
        AttachmentCas.replaceIndex(
            cacheDir,
            entries = mapOf(
                shaA to listOf(
                    entry("a.bin", nodeId = "node2", messageId = "message2", folderId = "folder", assistantId = "assistant"),
                    entry("a.bin", nodeId = "node1", messageId = "message1", folderId = "folder", assistantId = "assistant"),
                )
            ),
        )

        val entries = AttachmentCas.readIndex(cacheDir)!!.getValue(shaA)
        assertEquals(2, entries.size)
        assertEquals(listOf("a.bin", "a.bin"), entries.map { it.fileName })
        assertEquals(listOf("message1", "message2"), entries.map { it.messageId })
        assertTrue(entries.all { it.conversationId == "conv" })
        assertTrue(entries.all { it.assistantId == "assistant" && it.folderId == "folder" })
    }

    @Test
    fun `index file only contains version and entries`() {
        setUpRoots()

        val (_, shaA) = link("payload", "a.bin")
        AttachmentCas.replaceIndex(cacheDir, entries = mapOf(shaA to listOf(entry("a.bin"))))

        val json = Json.parseToJsonElement(AttachmentCas.indexFile(cacheDir).readText()).jsonObject
        assertEquals(setOf("version", "entries"), json.keys)
        assertEquals(1, json.getValue("version").jsonPrimitive.int)
    }

    @Test
    fun `removeLinks drops message references for the deleted links`() {
        setUpRoots()

        val (linkA, shaA) = link("payload", "a.bin")
        val (linkB, _) = link("payload", "b.bin")
        AttachmentCas.replaceIndex(
            cacheDir,
            entries = mapOf(
                shaA to listOf(
                    entry("a.bin"),
                    entry("b.bin"),
                )
            ),
        )

        linkA.delete()
        AttachmentCas.removeLinks(cacheDir, filesRoot, listOf(AttachmentCas.LinkRef(shaA, "a.bin")))

        val entries = AttachmentCas.readIndex(cacheDir)!!.getValue(shaA)
        assertEquals(listOf("b.bin"), entries.map { it.fileName })
    }

    @Test
    fun `updateIndexIfPresent keeps other entries while changing the target`() {
        setUpRoots()

        val (_, shaA) = link("payload", "a.bin")
        val (_, shaB) = link("other payload", "b.bin")
        AttachmentCas.replaceIndex(
            cacheDir,
            entries = mapOf(
                shaA to listOf(entry("a.bin", conversationId = "conv")),
                shaB to listOf(entry("b.bin", conversationId = "other")),
            ),
        )

        val updated = AttachmentCas.updateIndexIfPresent(cacheDir) { entries ->
            entries.values.forEach { list -> list.removeAll { it.conversationId == "conv" } }
        }

        assertTrue(updated)
        val index = AttachmentCas.readIndex(cacheDir)!!
        assertFalse(index.containsKey(shaA))
        assertEquals(listOf("b.bin"), index.getValue(shaB).map { it.fileName })
    }

    @Test
    fun `updateIndexIfPresent leaves an unchanged index file untouched`() {
        setUpRoots()

        val (_, shaA) = link("payload", "a.bin")
        AttachmentCas.replaceIndex(
            cacheDir,
            entries = mapOf(shaA to listOf(entry("a.bin", conversationId = "conv"))),
        )
        val indexFile = AttachmentCas.indexFile(cacheDir)
        val stamp = 1_000_000L
        assertTrue(indexFile.setLastModified(stamp))

        val updated = AttachmentCas.updateIndexIfPresent(cacheDir) { /* 不修改任何条目 */ }

        assertTrue(updated)
        assertEquals(stamp, indexFile.lastModified())
    }

    @Test
    fun `updateIndexIfPresent also skips a rewrite when a transform only reorders entries`() {
        setUpRoots()

        val (_, shaA) = link("payload", "a.bin")
        AttachmentCas.replaceIndex(
            cacheDir,
            entries = mapOf(
                shaA to listOf(
                    entry("a.bin", conversationId = "conv1"),
                    entry("a.bin", conversationId = "conv2"),
                )
            ),
        )
        val indexFile = AttachmentCas.indexFile(cacheDir)
        val stamp = 1_000_000L
        assertTrue(indexFile.setLastModified(stamp))

        AttachmentCas.updateIndexIfPresent(cacheDir) { entries ->
            entries.values.forEach { list -> list.reverse() }
        }

        // 顺序变化不是内容变化, 不应重写文件
        assertEquals(stamp, indexFile.lastModified())
    }

    @Test
    fun `needsRebuild detects missing and present index`() {
        setUpRoots()
        assertTrue(AttachmentCas.needsRebuild(cacheDir))

        AttachmentCas.replaceIndex(cacheDir, entries = emptyMap())

        assertFalse(AttachmentCas.needsRebuild(cacheDir))
    }
}
