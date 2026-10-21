package me.rerere.rikkahub.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class DatabaseBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File

    @Before fun setUp() {
        directory = Files.createTempDirectory(context.cacheDir.toPath(), "database-backup-test-").toFile()
    }

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    private fun open(file: File): SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.path,
        null,
        SQLiteDatabase.CREATE_IF_NECESSARY,
    )

    private fun createWalDatabase(file: File): SQLiteDatabase = open(file).apply {
        enableWriteAheadLogging()
        query("PRAGMA wal_autocheckpoint=0").use { assertTrue(it.moveToFirst()) }
        execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY, text TEXT)")
        query("PRAGMA wal_checkpoint(TRUNCATE)").use { assertTrue(it.moveToFirst()) }
        execSQL("INSERT INTO messages(text) VALUES ('committed only in WAL')")
        assertTrue(File(file.path + "-wal").length() > 0)
    }

    @Test fun snapshotContainsWalCommitsAndFtsTablesAndNeedsNoSidecars() {
        val source = File(directory, "source")
        val snapshot = File(directory, "snapshot")
        createWalDatabase(source).use {
            it.execSQL("CREATE VIRTUAL TABLE search USING fts5(text, tokenize='unicode61')")
            it.execSQL("INSERT INTO search(text) VALUES ('hello backup')")
            // 旧存档里遗留的 `message_fts`（真实存档用已移除的 `simple` 分词器建表，这里用
            // 内置分词器模拟表结构）：备份校验前应被删掉。
            it.execSQL("CREATE VIRTUAL TABLE message_fts USING fts5(text, tokenize='unicode61')")
            it.execSQL("INSERT INTO message_fts(text) VALUES ('legacy index')")
            DatabaseBackup.createSnapshot(it, snapshot)
            // Subsequent writes must not appear in the already-created snapshot.
            it.execSQL("INSERT INTO messages(text) VALUES ('after snapshot')")
        }
        assertFalse(File(snapshot.path + "-wal").exists())
        assertFalse(File(snapshot.path + "-shm").exists())
        DatabaseBackup.normalize(snapshot)
        open(snapshot).use {
            assertEquals(1L, it.longForQuery("SELECT count(*) FROM messages", null))
            assertEquals(1L, it.longForQuery("SELECT count(*) FROM search WHERE search MATCH 'hello'", null))
            assertEquals(
                0L,
                it.longForQuery("SELECT count(*) FROM sqlite_master WHERE name = 'message_fts'", null),
            )
        }
    }

    @Test fun corruptDatabaseIsRejected() {
        val file = File(directory, "invalid")
        file.writeText("not a SQLite database")
        var failed = false
        try {
            DatabaseBackup.normalize(file)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Corrupt backup must not be published", failed)
    }
}
