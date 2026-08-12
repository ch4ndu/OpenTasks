package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.udnahc.opentasks.ExternalInputFailure
import com.udnahc.opentasks.ExternalInputPolicy
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject

@Composable
actual fun rememberFileImportLauncher(
    onResult: (FileImportResult) -> Unit,
): (ImportFileType) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val launcher = remember {
        IosFileImportLauncher(
            onResult = { currentOnResult.value(it) },
            readFile = { url, type ->
                scope.launch {
                    val result = try {
                        withContext(Dispatchers.Default) {
                            readImportedFile(url, type)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        FileImportResult.Error(ExternalInputFailure.UNREADABLE)
                    }
                    currentCoroutineContext().ensureActive()
                    currentOnResult.value(result)
                }
            },
        )
    }
    return remember(launcher) { { type -> launcher.launch(type) } }
}

private class IosFileImportLauncher(
    private val onResult: (FileImportResult) -> Unit,
    private val readFile: (NSURL, ImportFileType) -> Unit,
) {
    private var activeDelegate: IosDocumentImportDelegate? = null

    fun launch(type: ImportFileType) {
        val presenter = activeViewController()
        if (presenter == null) {
            onResult(FileImportResult.Error())
            return
        }
        val documentTypes = when (type) {
            ImportFileType.CSV -> listOf("public.comma-separated-values-text", "public.plain-text")
            ImportFileType.ICS -> listOf("com.apple.ical.ics", "public.calendar-event", "public.plain-text")
        }
        val picker = UIDocumentPickerViewController(
            documentTypes = documentTypes,
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
        )
        val delegate = IosDocumentImportDelegate(
            expectedType = type,
            onResult = onResult,
            readFile = readFile,
            onFinished = { activeDelegate = null },
        )
        activeDelegate = delegate
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

private class IosDocumentImportDelegate(
    private val expectedType: ImportFileType,
    private val onResult: (FileImportResult) -> Unit,
    private val readFile: (NSURL, ImportFileType) -> Unit,
    private val onFinished: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onResult(FileImportResult.Error())
        } else {
            readFile(url, expectedType)
        }
        onFinished()
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(FileImportResult.Cancelled)
        onFinished()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun readImportedFile(url: NSURL, expectedType: ImportFileType): FileImportResult {
    val accessing = url.startAccessingSecurityScopedResource()
    return try {
        val fileName = url.lastPathComponent ?: "import.${expectedType.extension}"
        if (!expectedType.accepts(fileName)) {
            return FileImportResult.Error(ExternalInputFailure.INVALID_FILE_TYPE)
        }
        val path = url.path ?: return FileImportResult.Error(ExternalInputFailure.UNREADABLE)
        when (val result = readBoundedFile(path, ExternalInputPolicy.MAX_IMPORT_BYTES)) {
            BoundedRead.TooLarge -> FileImportResult.Error(ExternalInputFailure.TOO_LARGE)
            BoundedRead.InvalidUtf8 -> FileImportResult.Error(ExternalInputFailure.INVALID_UTF8)
            BoundedRead.Unreadable -> FileImportResult.Error(ExternalInputFailure.UNREADABLE)
            is BoundedRead.Success -> FileImportResult.Selected(
                ImportedFile(name = fileName, content = result.content),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        FileImportResult.Error(ExternalInputFailure.UNREADABLE)
    } finally {
        if (accessing) url.stopAccessingSecurityScopedResource()
    }
}

private sealed interface BoundedRead {
    data class Success(val content: String) : BoundedRead
    data object TooLarge : BoundedRead
    data object InvalidUtf8 : BoundedRead
    data object Unreadable : BoundedRead
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun readBoundedFile(path: String, maxBytes: Int): BoundedRead {
    val file = platform.posix.fopen(path, "rb") ?: return BoundedRead.Unreadable
    return try {
        val contentBytes = ByteArray(maxBytes + 1)
        val buffer = ByteArray(minOf(maxBytes + 1, 8192))
        var total = 0
        while (total <= maxBytes) {
            currentCoroutineContext().ensureActive()
            val requested = minOf(buffer.size, maxBytes + 1 - total)
            val count = buffer.usePinned { pinned ->
                platform.posix.fread(
                    pinned.addressOf(0),
                    1.convert(),
                    requested.convert(),
                    file,
                ).toInt()
            }
            if (count < 0) return BoundedRead.Unreadable
            if (count == 0) {
                if (platform.posix.ferror(file) != 0) return BoundedRead.Unreadable
                break
            }
            buffer.copyInto(contentBytes, destinationOffset = total, endIndex = count)
            total += count
        }
        currentCoroutineContext().ensureActive()
        if (total > maxBytes) return BoundedRead.TooLarge
        val bytes = contentBytes.copyOf(total)
        if (!ExternalInputPolicy.isStrictUtf8(bytes)) return BoundedRead.InvalidUtf8
        if (bytes.isEmpty()) return BoundedRead.Success("")
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.convert())
        }
        val content = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            ?: return BoundedRead.InvalidUtf8
        BoundedRead.Success(content)
    } finally {
        platform.posix.fclose(file)
    }
}

internal fun activeViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val root = application.keyWindow?.rootViewController
        ?: application.windows.firstOrNull()?.let { it as? UIWindow }?.rootViewController
        ?: return null
    var active: UIViewController = root
    while (true) {
        val presented = active.presentedViewController ?: break
        active = presented
    }
    return active
}
