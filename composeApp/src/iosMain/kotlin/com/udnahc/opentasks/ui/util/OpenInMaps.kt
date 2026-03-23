package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.Foundation.NSString
import platform.Foundation.NSCharacterSet
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication

@Composable
actual fun rememberOpenInMapsAction(): (String) -> Unit {
    return { location ->
        val encoded = (location as NSString).stringByAddingPercentEncodingWithAllowedCharacters(
            NSCharacterSet.URLQueryAllowedCharacterSet
        ) ?: return@return
        val url = NSURL.URLWithString("maps://?q=$encoded") ?: return@return
        UIApplication.sharedApplication.openURL(url)
    }
}
