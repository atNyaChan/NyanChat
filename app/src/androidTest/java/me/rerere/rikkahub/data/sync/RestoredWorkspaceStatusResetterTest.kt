package me.rerere.rikkahub.data.sync

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import me.rerere.workspace.WorkspaceShellStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoredWorkspaceStatusResetterTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var databaseFile: File

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = Files.createTempDirectory(app.cacheDir.toPath(), "workspace-resetter-test-").toFile()
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getDatabasePath(name: String): File =
                File(directory, "databases/$name").also { it.parentFile!!.mkdirs() }
        }
        databaseFile = context.getDatabasePath("rikka_hub")
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("CREATE TABLE workspaces (id TEXT PRIMARY KEY, root TEXT, shell_status TEXT)")
        }
    }

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    @Test fun keepsReadyWorkspaceWhoseLocalRootfsStillExists() {
        installLocalRootfs("ws-existing")
        insertWorkspace("ws-existing", "ws-existing", WorkspaceShellStatus.READY)

        RestoredWorkspaceStatusResetter.resetAfterRestore(context)

        assertEquals(WorkspaceShellStatus.READY.name, statusOf("ws-existing"))
    }

    @Test fun reEnablesExistingWorkspaceEvenIfArchiveDisabledIt() {
        installLocalRootfs("ws-existing")
        insertWorkspace("ws-existing", "ws-existing", WorkspaceShellStatus.DISABLED)

        RestoredWorkspaceStatusResetter.resetAfterRestore(context)

        assertEquals(WorkspaceShellStatus.READY.name, statusOf("ws-existing"))
    }

    @Test fun disablesWorkspaceWithoutLocalRootfs() {
        insertWorkspace("ws-missing", "ws-missing", WorkspaceShellStatus.READY)

        RestoredWorkspaceStatusResetter.resetAfterRestore(context)

        assertEquals(WorkspaceShellStatus.DISABLED.name, statusOf("ws-missing"))
    }

    private fun installLocalRootfs(root: String) {
        val linuxDir = File(File(context.filesDir, "workspaces"), "$root/linux/bin").apply { mkdirs() }
        File(linuxDir, "sh").writeText("#!/bin/sh\n")
    }

    private fun insertWorkspace(id: String, root: String, status: WorkspaceShellStatus) {
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.execSQL(
                "INSERT INTO workspaces (id, root, shell_status) VALUES (?, ?, ?)",
                arrayOf(id, root, status.name),
            )
        }
    }

    private fun statusOf(id: String): String =
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT shell_status FROM workspaces WHERE id = ?", arrayOf(id)).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        }
}
