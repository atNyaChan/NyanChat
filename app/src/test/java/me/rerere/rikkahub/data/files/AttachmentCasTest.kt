package me.rerere.rikkahub.data.files

import java.io.File
import java.nio.file.Files
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
        return AttachmentCas.linkEntity(cacheDir, filesRoot, sha256, linkName, temporary) to sha256
    }

    private fun entity(sha256: String) = File(AttachmentCas.casDir(filesRoot), sha256)

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
    fun `missing index is rebuilt from disk and never drops a referenced entity`() {
        setUpRoots()

        val (linkA, shaA) = link("payload", "a.bin")
        val (linkB, _) = link("payload", "b.bin")
        val casEntity = entity(shaA)

        // 索引丢失后仍以磁盘上的软链接为准
        assertTrue(AttachmentCas.indexFile(cacheDir).delete())
        linkA.delete()
        AttachmentCas.removeLinks(cacheDir, filesRoot, listOf(AttachmentCas.LinkRef(shaA, "a.bin")))

        assertTrue("entity still referenced by b.bin", casEntity.exists())
        assertTrue(linkB.exists())
        assertTrue(AttachmentCas.indexFile(cacheDir).isFile)
    }

    @Test
    fun `removeLinks tolerates a stale index that misses the deleted link`() {
        setUpRoots()

        val (linkA, shaA) = link("payload", "a.bin")
        val (linkB, _) = link("payload", "b.bin")
        val casEntity = entity(shaA)

        // 索引里只留下 a.bin, b.bin 变成过期条目
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
}
