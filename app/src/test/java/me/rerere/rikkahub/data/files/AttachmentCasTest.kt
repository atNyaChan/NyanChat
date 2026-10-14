package me.rerere.rikkahub.data.files

import java.io.File
import me.rerere.rikkahub.data.sync.ManagedFilesArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentCasTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var filesRoot: File

    private fun setUpRoots() {
        filesRoot = folder.newFolder("files")
    }

    private fun uploadDir() = File(filesRoot, FileFolders.UPLOAD).apply { mkdirs() }

    /** 通过 [AttachmentCas] 落一份内容, 返回 (sha256, 实体文件)。 */
    private fun commit(content: String): Pair<String, File> {
        val dir = uploadDir()
        val (temporary, sha256) = AttachmentCas.writeTemporary(dir) { it.write(content.toByteArray()) }
        return sha256 to AttachmentCas.commitTemporary(dir, sha256, temporary)
    }

    @Test
    fun `identical content commits to one sha256 named entity`() {
        setUpRoots()

        val (shaA, fileA) = commit("shared payload")
        val (shaB, fileB) = commit("shared payload")

        assertEquals(shaA, shaB)
        assertEquals(fileA, fileB)
        assertEquals("shared payload", fileA.readText())
        assertEquals(
            1,
            uploadDir().listFiles().orEmpty().count { it.isFile && !AttachmentCas.isTemporary(it) },
        )
        // 相同内容再次提交时临时文件被丢弃
        assertTrue(uploadDir().listFiles().orEmpty().none { AttachmentCas.isTemporary(it) })
    }

    @Test
    fun `commitTemporary keeps distinct content in separate entities`() {
        setUpRoots()

        val (shaA, fileA) = commit("payload")
        val (shaB, fileB) = commit("other")

        assertTrue(shaA != shaB)
        assertEquals(fileA.name, shaA)
        assertEquals(fileB.name, shaB)
        assertEquals(2, uploadDir().listFiles().orEmpty().count { it.isFile })
    }

    @Test
    fun `deleteStaleTemporaryFiles removes only old temporary files`() {
        setUpRoots()

        val dir = uploadDir()
        val stale = File.createTempFile("upload-", ".tmp", dir).apply { writeText("stale") }
        stale.setLastModified(0L)
        val fresh = File.createTempFile("upload-", ".tmp", dir).apply { writeText("fresh") }
        val entity = File(dir, "a".repeat(64)).apply { writeText("entity") }

        AttachmentCas.deleteStaleTemporaryFiles(dir)

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        assertTrue(entity.exists())
        fresh.delete()
    }

    @Test
    fun `normalizeUploadDir renames legacy files to sha256 and dedups content`() {
        setUpRoots()

        val upload = uploadDir()
        File(upload, "x.bin").writeText("same")
        File(upload, "y.bin").writeText("same")
        File(upload, "z.bin").writeText("other")

        val renamed = ManagedFilesArchive.normalizeUploadDir(filesRoot)

        assertEquals(3, renamed.size)
        assertEquals(renamed.getValue("x.bin"), renamed.getValue("y.bin"))
        assertTrue(renamed.getValue("x.bin") != renamed.getValue("z.bin"))
        // 旧文件名不再存在，内容以 sha256 命名
        assertEquals(
            2,
            upload.listFiles().orEmpty().count { it.isFile && !AttachmentCas.isTemporary(it) },
        )
        assertTrue(renamed.values.all { upload.resolve(it).isFile })
        assertEquals("same", upload.resolve(renamed.getValue("x.bin")).readText())
        assertEquals("other", upload.resolve(renamed.getValue("z.bin")).readText())
    }

    @Test
    fun `normalizeUploadDir keeps already sha256 named files and reports identity mapping`() {
        setUpRoots()

        val upload = uploadDir()
        val (sha256, _) = commit("payload")

        val renamed = ManagedFilesArchive.normalizeUploadDir(filesRoot)

        assertEquals(mapOf(sha256 to sha256), renamed)
        assertTrue(upload.resolve(sha256).isFile)
    }

    @Test
    fun `normalizeUploadDir rehashes sha looking files whose content does not match`() {
        setUpRoots()

        val upload = uploadDir()
        val wrongName = "a".repeat(64)
        File(upload, wrongName).writeText("actual content")

        val renamed = ManagedFilesArchive.normalizeUploadDir(filesRoot)

        val expectedSha = renamed.getValue(wrongName)
        assertTrue(expectedSha != wrongName)
        assertTrue(upload.resolve(expectedSha).isFile)
        assertFalse(upload.resolve(wrongName).exists())
        assertEquals("actual content", upload.resolve(expectedSha).readText())
    }

    @Test
    fun `attachment sha is only read from local urls`() {
        val sha = "a".repeat(64)
        assertEquals(sha, attachmentShaFromUrl("upload/$sha"))
        assertEquals(sha, attachmentShaFromUrl("file:///data/user/0/app/files/upload/$sha"))
        assertNull(attachmentShaFromUrl("https://example.com/upload/$sha"))
        assertNull(attachmentShaFromUrl("content://media/upload/$sha"))
        assertNull(attachmentShaFromUrl(""))
    }

    @Test
    fun `temp file name is only read from local temp urls`() {
        assertEquals("x.jpg", tempAttachmentNameFromUrl("upload_tmp/x.jpg"))
        assertEquals("x.jpg", tempAttachmentNameFromUrl("file:///data/user/0/app/files/upload_tmp/x.jpg"))
        assertNull(tempAttachmentNameFromUrl("https://example.com/upload_tmp/x.jpg"))
        assertNull(tempAttachmentNameFromUrl("upload/abc"))
        assertNull(tempAttachmentNameFromUrl("file:///data/files/upload/x.jpg"))
    }

    @Test
    fun `isTemporary matches writeTemporary output`() {
        setUpRoots()

        val dir = uploadDir()
        val (temporary, _) = AttachmentCas.writeTemporary(dir) { it.write("x".toByteArray()) }
        assertTrue(AttachmentCas.isTemporary(temporary))
        temporary.delete()
    }
}
