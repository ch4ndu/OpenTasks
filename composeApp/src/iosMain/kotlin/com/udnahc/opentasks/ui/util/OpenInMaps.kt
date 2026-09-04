package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ExternalLaunchResult
import platform.Foundation.NSURL
import platform.Foundation.NSString
import platform.Foundation.NSCharacterSet
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication
import org.lighthousegames.logging.logging

private val log = logging("OpenInMaps")

@Composable
actual fun rememberOpenInMapsAction(): (String) -> ExternalLaunchResult {
    return { location ->
        @Suppress("CAST_NEVER_SUCCEEDS")
        val encoded = (location as NSString).stringByAddingPercentEncodingWithAllowedCharacters(
            NSCharacterSet.URLQueryAllowedCharacterSet
        )
        val url = encoded?.let { NSURL.URLWithString("maps://?q=$it") }
        if (url == null) {
            mapsLaunchFailure()
        } else {
            @Suppress("DEPRECATION")
            if (UIApplication.sharedApplication.openURL(url)) {
                ExternalLaunchResult.SUCCESS
            } else {
                mapsLaunchFailure()
            }
        }
    }
}

private fun mapsLaunchFailure(): ExternalLaunchResult {
    log.e { "Maps launch failed" }
    return ExternalLaunchResult.FAILURE
}
