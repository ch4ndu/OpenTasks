package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
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
                    val result = withContext(Dispatchers.Default) {
                        readImportedFile(url, type)
                    }
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

@OptIn(ExperimentalForeignApi::class)
private fun readImportedFile(url: NSURL, expectedType: ImportFileType): FileImportResult {
    val accessing = url.startAccessingSecurityScopedResource()
    return try {
        val fileName = url.lastPathComponent ?: "import.${expectedType.extension}"
        if (!expectedType.accepts(fileName)) {
            return FileImportResult.Error()
        }
        val data = NSData.dataWithContentsOfURL(url)
            ?: return FileImportResult.Error()
        val content = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            ?: return FileImportResult.Error()
        FileImportResult.Selected(
            ImportedFile(
                name = fileName,
                content = content,
            ),
        )
    } catch (e: Exception) {
        FileImportResult.Error()
    } finally {
        if (accessing) url.stopAccessingSecurityScopedResource()
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
