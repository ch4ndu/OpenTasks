package com.udnahc.opentasks.data.sync

/** Escapes a PocketBase string literal without interpolating user data into a filter. */
object PocketBaseFilter {
    fun localIdEquals(localId: String): String =
        "localId='${localId.replace("\\", "\\\\").replace("'", "\\'")}'"
}
