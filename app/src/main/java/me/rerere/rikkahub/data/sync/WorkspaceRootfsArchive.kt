package me.rerere.rikkahub.data.sync

import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

internal object WorkspaceRootfsArchive {
    fun create(
        rootfsDir: File,
        workspaceDir: File,
        excludedRootfsPaths: Set<String>,
        outputStream: OutputStream,
    ) {
        ZstdOutputStream(
            outputStream.buffered(IO_BUFFER_SIZE),
            COMPRESSION_LEVEL,
        ).setWorkers(ZSTD_WORKERS).use { zstd ->
            TarArchiveOutputStream(zstd).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                val rootfsPath = rootfsDir.toPath()
                val excludedRoots = excludedRootfsPaths.mapTo(mutableSetOf()) { path ->
                    rootfsPath.resolve(path.trim('/'))
                }
                addTree(tar, rootfsPath, "", excludedRoots)
                addTree(tar, workspaceDir.toPath(), WORKSPACE_ENTRY)
                tar.finish()
            }
        }
    }

    private fun addTree(
        tar: TarArchiveOutputStream,
        root: Path,
        archivePrefix: String,
        excludedRoots: Set<Path> = emptySet(),
    ) {
        if (!Files.exists(root)) return
        if (archivePrefix.isNotEmpty()) putDirectory(tar, root, "$archivePrefix/")
        addDirectoryContents(tar, root, root, archivePrefix, excludedRoots)
    }

    private fun addDirectoryContents(
        tar: TarArchiveOutputStream,
        root: Path,
        directory: Path,
        archivePrefix: String,
        excludedRoots: Set<Path>,
    ) {
        Files.newDirectoryStream(directory).use { children ->
            children.forEach { path ->
                // PRoot 与内核挂载点只是占位目录，Android 侧可能没有读取权限，也不属于 Rootfs。
                // 必须在读取属性或打开目录之前跳过；Files.walk 会过早打开它并抛 AccessDeniedException。
                if (excludedRoots.any { path.startsWith(it) }) return@forEach
                val relative = root.relativize(path).toString().replace(File.separatorChar, '/')
                val name = if (archivePrefix.isEmpty()) relative else "$archivePrefix/$relative"
                when {
                    Files.isSymbolicLink(path) -> putSymlink(tar, path, name)
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> {
                        putDirectory(tar, path, "$name/")
                        addDirectoryContents(tar, root, path, archivePrefix, excludedRoots)
                    }
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> putFile(tar, path.toFile(), name)
                }
            }
        }
    }

    private fun putDirectory(tar: TarArchiveOutputStream, path: Path, name: String) {
        val entry = TarArchiveEntry(path.toFile(), name).apply {
            mode = readMode(path, DIRECTORY_MODE)
        }
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun putSymlink(tar: TarArchiveOutputStream, path: Path, name: String) {
        val entry = TarArchiveEntry(name, TarConstants.LF_SYMLINK).apply {
            linkName = Files.readSymbolicLink(path).toString()
            mode = readMode(path, SYMLINK_MODE)
            lastModifiedTime = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)
        }
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun putFile(tar: TarArchiveOutputStream, file: File, name: String) {
        val entry = TarArchiveEntry(file, name).apply {
            mode = readMode(file.toPath(), FILE_MODE)
        }
        tar.putArchiveEntry(entry)
        file.inputStream().buffered(IO_BUFFER_SIZE).use { it.copyTo(tar, IO_BUFFER_SIZE) }
        tar.closeArchiveEntry()
    }

    private fun readMode(path: Path, fallback: Int): Int = runCatching {
        Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).fold(0) { mode, permission ->
            mode or when (permission) {
                PosixFilePermission.OWNER_READ -> 0b100000000
                PosixFilePermission.OWNER_WRITE -> 0b010000000
                PosixFilePermission.OWNER_EXECUTE -> 0b001000000
                PosixFilePermission.GROUP_READ -> 0b000100000
                PosixFilePermission.GROUP_WRITE -> 0b000010000
                PosixFilePermission.GROUP_EXECUTE -> 0b000001000
                PosixFilePermission.OTHERS_READ -> 0b000000100
                PosixFilePermission.OTHERS_WRITE -> 0b000000010
                PosixFilePermission.OTHERS_EXECUTE -> 0b000000001
            }
        }
    }.getOrDefault(fallback)

    private const val COMPRESSION_LEVEL = 3
    private const val IO_BUFFER_SIZE = 128 * 1024
    private val ZSTD_WORKERS = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private const val WORKSPACE_ENTRY = "workspace"
    private const val FILE_MODE = 0b110100100 // 0644
    private const val DIRECTORY_MODE = 0b111101101 // 0755
    private const val SYMLINK_MODE = 0b111111111 // 0777
}
