package com.udnahc.opentasks.ui.util

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ExternalLaunchResult
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import org.lighthousegames.logging.logging

private val log = logging("OpenInMaps")

@Composable
actual fun rememberOpenInMapsAction(): (String) -> ExternalLaunchResult {
    return { location ->
        try {
            if (!Desktop.isDesktopSupported()) {
                mapsLaunchFailure()
            } else {
                val desktop = Desktop.getDesktop()
                if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                    mapsLaunchFailure()
                } else {
                    val encoded = URLEncoder.encode(location, "UTF-8")
                    desktop.browse(URI("https://www.google.com/maps/search/$encoded"))
                    ExternalLaunchResult.SUCCESS
                }
            }
        } catch (_: Exception) {
            mapsLaunchFailure()
        }
    }
}

private fun mapsLaunchFailure(): ExternalLaunchResult {
    log.e { "Maps launch failed" }
    return ExternalLaunchResult.FAILURE
}
