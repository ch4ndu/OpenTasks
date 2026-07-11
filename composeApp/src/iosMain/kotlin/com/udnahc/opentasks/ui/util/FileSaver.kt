package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

@Composable
actual fun rememberFileExportLauncher(
    onResult: (FileExportResult) -> Unit,
): (FileExportRequest) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val launcher = remember {
        IosFileExportLauncher(scope) { currentOnResult.value(it) }
    }
    return remember(launcher) { { request -> launcher.launch(request) } }
}

private class IosFileExportLauncher(
    private val scope: CoroutineScope,
    private val onResult: (FileExportResult) -> Unit,
) {
    private var activeDelegate: IosDocumentExportDelegate? = null

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    fun launch(request: FileExportRequest) {
        scope.launch {
            val prepared = withContext(Dispatchers.Default) { prepareExport(request) }
            if (prepared == null) {
                onResult(FileExportResult.Error())
                return@launch
            }
            val presenter = activeViewController()
            if (presenter == null) {
                withContext(Dispatchers.Default) { cleanupExport(prepared.directory) }
                onResult(FileExportResult.Error())
                return@launch
            }

            val picker = UIDocumentPickerViewController(
                uRL = prepared.url,
                inMode = UIDocumentPickerMode.UIDocumentPickerModeExportToService,
            )
            val delegate = IosDocumentExportDelegate(
                exportDirectory = prepared.directory,
                scope = scope,
                onResult = onResult,
                onFinished = { activeDelegate = null },
            )
            activeDelegate = delegate
            picker.delegate = delegate
            try {
                presenter.presentViewController(picker, animated = true, completion = null)
            } catch (e: Exception) {
                activeDelegate = null
                withContext(Dispatchers.Default) { cleanupExport(prepared.directory) }
                onResult(FileExportResult.Error())
            }
        }
    }
}

private data class PreparedIosExport(
    val directory: String,
    val url: NSURL,
)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun prepareExport(request: FileExportRequest): PreparedIosExport? {
    val exportDirectory = "${NSTemporaryDirectory()}opentasks-exports/${NSUUID().UUIDString}/"
    val fileManager = platform.Foundation.NSFileManager.defaultManager
    val directoryCreated = fileManager.createDirectoryAtPath(
        exportDirectory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    if (!directoryCreated) return null

    val filePath = "$exportDirectory${request.fileName}"
    val written = NSString.create(string = request.content).writeToFile(
        filePath,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    if (!written) {
        cleanupExport(exportDirectory)
        return null
    }
    return PreparedIosExport(exportDirectory, NSURL.fileURLWithPath(filePath))
}

@OptIn(ExperimentalForeignApi::class)
private fun cleanupExport(exportDirectory: String) {
    platform.Foundation.NSFileManager.defaultManager.removeItemAtPath(exportDirectory, error = null)
}

@OptIn(ExperimentalForeignApi::class)
private class IosDocumentExportDelegate(
    private val exportDirectory: String,
    private val scope: CoroutineScope,
    private val onResult: (FileExportResult) -> Unit,
    private val onFinished: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        finish(FileExportResult.Completed)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(FileExportResult.Cancelled)
    }

    private fun finish(result: FileExportResult) {
        onFinished()
        scope.launch {
            withContext(Dispatchers.Default) { cleanupExport(exportDirectory) }
            onResult(result)
        }
    }
}
