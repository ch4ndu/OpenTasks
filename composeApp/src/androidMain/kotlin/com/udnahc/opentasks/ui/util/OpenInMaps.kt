package com.udnahc.opentasks.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.udnahc.opentasks.ExternalLaunchResult
import org.lighthousegames.logging.logging

private val log = logging("OpenInMaps")

@Composable
actual fun rememberOpenInMapsAction(): (String) -> ExternalLaunchResult {
    val context = LocalContext.current
    return { location ->
        val encodedLocation = Uri.encode(location)
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedLocation")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (tryLaunch(context, geoIntent)) {
            ExternalLaunchResult.SUCCESS
        } else {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/$encodedLocation"),
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (tryLaunch(context, browserIntent)) {
                ExternalLaunchResult.SUCCESS
            } else {
                log.e { "Maps launch failed" }
                ExternalLaunchResult.FAILURE
            }
        }
    }
}

private fun tryLaunch(context: Context, intent: Intent): Boolean = try {
    if (intent.resolveActivity(context.packageManager) == null) {
        false
    } else {
        context.startActivity(intent)
        true
    }
} catch (_: Exception) {
    false
}
