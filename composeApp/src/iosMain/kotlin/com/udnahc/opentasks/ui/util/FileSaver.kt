package com.udnahc.opentasks.ui.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFileSaver : FileSaver {

    override suspend fun save(fileName: String, content: String, mimeType: String): Boolean =
        suspendCancellableCoroutine { cont ->
            dispatch_async(dispatch_get_main_queue()) {
                try {
                    val tempDir = NSTemporaryDirectory()
                    val filePath = "$tempDir$fileName"
                    val nsString = NSString.create(string = content)
                    nsString.writeToFile(filePath, atomically = true, encoding = NSUTF8StringEncoding, error = null)

                    val fileUrl = NSURL.fileURLWithPath(filePath)
                    val activityVC = UIActivityViewController(
                        activityItems = listOf(fileUrl),
                        applicationActivities = null,
                    )
                    val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
                    rootVC?.presentViewController(activityVC, animated = true, completion = null)
                    cont.resume(true)
                } catch (e: Exception) {
                    cont.resume(false)
                }
            }
        }
}
