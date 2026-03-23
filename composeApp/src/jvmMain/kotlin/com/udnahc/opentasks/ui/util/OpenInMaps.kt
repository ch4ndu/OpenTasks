package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

@Composable
actual fun rememberOpenInMapsAction(): (String) -> Unit {
    return { location ->
        try {
            val encoded = URLEncoder.encode(location, "UTF-8")
            Desktop.getDesktop().browse(URI("https://www.google.com/maps/search/$encoded"))
        } catch (_: Exception) {
            // Silently fail if browser cannot open
        }
    }
}
