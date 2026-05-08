package com.udnahc.opentasks.ui.util

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.lighthousegames.logging.logging

private val log = logging("OpenInMaps")

@Composable
actual fun rememberOpenInMapsAction(): (String) -> Unit {
    val context = LocalContext.current
    return { location ->
        val encodedLocation = Uri.encode(location)
        val uri = Uri.parse("geo:0,0?q=$encodedLocation")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            log.e(e) { "Failed to open location in maps app" }
            val browserUri = Uri.parse("https://www.google.com/maps/search/$encodedLocation")
            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
