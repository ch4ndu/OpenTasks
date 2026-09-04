package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
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
    val launcher = remember {
        IosFileExportLauncher { result -> currentOnResult.value(result) }
    }
    DisposableEffect(launcher) {
        onDispose { launcher.dispose() }
    }
    return remember(launcher) { { request -> launcher.launch(request) } }
}

private class IosFileExportLauncher(
    private val onResult: (FileExportResult) -> Unit,
) {
    private val preparationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var state: ExportState = ExportState.Idle

    @OptIn(ExperimentalForeignApi::class)
    fun launch(request: FileExportRequest) {
        if (state !is ExportState.Idle) return
        val lease = try {
            ExportLease(
                directory = "${NSTemporaryDirectory()}opentasks-exports/${NSUUID().UUIDString}/",
            )
        } catch (_: Exception) {
            onResult(FileExportResult.Error())
            return
        }
        val job = preparationScope.launch(start = CoroutineStart.LAZY) {
            prepareAndPresent(request, lease)
        }
        state = ExportState.Preparing(lease, job)
        job.start()
    }

    fun dispose() {
        when (val current = state) {
            ExportState.Idle -> {
                state = ExportState.Disposed
            }

            is ExportState.Preparing -> {
                state = ExportState.Disposed
                current.job.cancel()
                launchIndependentCleanup(current.lease, current.job)
            }

            is ExportState.Presented -> {
                state = ExportState.Disposed
                current.delegate.retire()
                current.picker.delegate = null
                try {
                    current.picker.dismissViewControllerAnimated(true, completion = null)
                } catch (_: Exception) {
                    // Cleanup remains owned by the export lease.
                }
                launchIndependentCleanup(current.lease)
            }

            is ExportState.Cleaning -> {
                state = ExportState.Disposed
            }

            ExportState.Disposed -> Unit
        }
        preparationScope.cancel()
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private suspend fun prepareAndPresent(request: FileExportRequest, lease: ExportLease) {
        try {
            val prepared = withContext(Dispatchers.Default) {
                prepareExport(request, lease)
            }
            currentCoroutineContext().ensureActive()
            val presenter = activeViewController() ?: throw IosExportFailure()
            val picker = UIDocumentPickerViewController(
                uRL = prepared.url,
                inMode = UIDocumentPickerMode.UIDocumentPickerModeExportToService,
            )
            val delegate = IosDocumentExportDelegate(this, lease)
            picker.delegate = delegate
            presenter.presentViewController(picker, animated = true, completion = null)

            val current = state
            if (current !is ExportState.Preparing || current.lease !== lease) {
                delegate.retire()
                picker.delegate = null
                throw CancellationException()
            }
            state = ExportState.Presented(lease, picker, delegate)
            delegate.activate()
        } catch (e: CancellationException) {
            cleanupCancelledPreparation(lease)
            throw e
        } catch (_: Exception) {
            cleanupFailedPreparation(lease)
        }
    }

    private suspend fun cleanupCancelledPreparation(lease: ExportLease) {
        if (!claimPreparingLease(lease)) return
        withContext(NonCancellable + Dispatchers.Default) {
            cleanupExport(lease.directory)
        }
        withContext(NonCancellable + Dispatchers.Main) {
            if (isCleaningLease(lease)) {
                state = ExportState.Idle
            }
        }
    }

    private suspend fun cleanupFailedPreparation(lease: ExportLease) {
        if (!claimPreparingLease(lease)) return
        withContext(NonCancellable + Dispatchers.Default) {
            cleanupExport(lease.directory)
        }
        withContext(NonCancellable + Dispatchers.Main) {
            if (isCleaningLease(lease)) {
                state = ExportState.Idle
                onResult(FileExportResult.Error())
            }
        }
    }

    private fun claimPreparingLease(lease: ExportLease): Boolean {
        val current = state
        if (current !is ExportState.Preparing || current.lease !== lease) return false
        state = ExportState.Cleaning(lease)
        return true
    }

    private fun isCleaningLease(lease: ExportLease): Boolean {
        val current = state
        return current is ExportState.Cleaning && current.lease === lease
    }

    fun finishPresentedExport(
        lease: ExportLease,
        delegate: IosDocumentExportDelegate,
        result: FileExportResult,
    ) {
        val current = state
        if (current !is ExportState.Presented ||
            current.lease !== lease ||
            current.delegate !== delegate
        ) {
            return
        }
        delegate.retire()
        current.picker.delegate = null
        state = ExportState.Cleaning(lease)
        launchIndependentCleanup(lease) {
            if (isCleaningLease(lease)) {
                state = ExportState.Idle
                onResult(result)
            }
        }
    }

    private fun launchIndependentCleanup(
        lease: ExportLease,
        waitFor: Job? = null,
        onCleaned: (() -> Unit)? = null,
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (waitFor != null) {
                waitFor.join()
            }
            cleanupExport(lease.directory)
            if (onCleaned != null) {
                withContext(Dispatchers.Main) { onCleaned() }
            }
        }
    }
}

private sealed interface ExportState {
    data object Idle : ExportState

    data class Preparing(
        val lease: ExportLease,
        val job: Job,
    ) : ExportState

    data class Presented(
        val lease: ExportLease,
        val picker: UIDocumentPickerViewController,
        val delegate: IosDocumentExportDelegate,
    ) : ExportState

    data class Cleaning(val lease: ExportLease) : ExportState

    data object Disposed : ExportState
}

private data class ExportLease(val directory: String)

private data class PreparedIosExport(val url: NSURL)

private class IosExportFailure : Exception()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun prepareExport(request: FileExportRequest, lease: ExportLease): PreparedIosExport {
    val fileManager = NSFileManager.defaultManager
    val directoryCreated = fileManager.createDirectoryAtPath(
        lease.directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    if (!directoryCreated) throw IosExportFailure()

    val filePath = "${lease.directory}${request.fileName}"
    val written = NSString.create(string = request.content).writeToFile(
        filePath,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    if (!written) throw IosExportFailure()
    return PreparedIosExport(NSURL.fileURLWithPath(filePath))
}

@OptIn(ExperimentalForeignApi::class)
private fun cleanupExport(exportDirectory: String) {
    NSFileManager.defaultManager.removeItemAtPath(exportDirectory, error = null)
}

@OptIn(ExperimentalForeignApi::class)
private class IosDocumentExportDelegate(
    private val launcher: IosFileExportLauncher,
    private val lease: ExportLease,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    private var active = false

    fun activate() {
        active = true
    }

    fun retire() {
        active = false
    }

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
        if (!active) return
        active = false
        launcher.finishPresentedExport(lease, this, result)
    }
}
