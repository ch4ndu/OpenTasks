package com.udnahc.opentasks

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.udnahc.opentasks.di.initKoin
import java.io.Closeable
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.ic_launcher
import org.jetbrains.compose.resources.painterResource

fun main() {
    val instanceLease = acquireDesktopInstanceLease() ?: return
    instanceLease.use {
        initKoin()
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "OpenTasks",
                icon = painterResource(Res.drawable.ic_launcher),
            ) {
                App()
            }
        }
    }
}

private fun acquireDesktopInstanceLease(): DesktopInstanceLease? {
    val userHome = try {
        System.getProperty("user.home")
    } catch (_: SecurityException) {
        null
    }
    if (userHome.isNullOrBlank()) return desktopInstanceLeaseFailure()

    var channel: FileChannel? = null
    return try {
        val applicationDirectory = Path.of(userHome, ".opentasks")
        Files.createDirectories(applicationDirectory)
        val directoryAttributes = Files.readAttributes(
            applicationDirectory,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        if (!directoryAttributes.isDirectory || directoryAttributes.isSymbolicLink) {
            return desktopInstanceLeaseFailure()
        }
        val openedChannel = FileChannel.open(
            applicationDirectory.resolve("instance.lock"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        )
        channel = openedChannel
        val lock = openedChannel.tryLock() ?: run {
            openedChannel.closeQuietly()
            return desktopInstanceLeaseFailure()
        }
        DesktopInstanceLease(openedChannel, lock)
    } catch (_: OverlappingFileLockException) {
        channel.closeQuietly()
        desktopInstanceLeaseFailure()
    } catch (_: InvalidPathException) {
        channel.closeQuietly()
        desktopInstanceLeaseFailure()
    } catch (_: IOException) {
        channel.closeQuietly()
        desktopInstanceLeaseFailure()
    } catch (_: SecurityException) {
        channel.closeQuietly()
        desktopInstanceLeaseFailure()
    } catch (_: UnsupportedOperationException) {
        channel.closeQuietly()
        desktopInstanceLeaseFailure()
    }
}

private fun desktopInstanceLeaseFailure(): DesktopInstanceLease? {
    System.err.println("OpenTasks could not acquire the application instance lock.")
    return null
}

private fun FileChannel?.closeQuietly() {
    try {
        this?.close()
    } catch (_: IOException) {
        // The fixed acquisition failure above is the only startup diagnostic.
    }
}

private class DesktopInstanceLease(
    private val channel: FileChannel,
    private val lock: FileLock,
) : Closeable {
    override fun close() {
        try {
            lock.release()
        } catch (_: IOException) {
            // Process exit releases the operating-system lock as a final fallback.
        }
        channel.closeQuietly()
    }
}
