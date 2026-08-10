package com.udnahc.opentasks.data.sync

/** Escapes a PocketBase string literal without interpolating user data into a filter. */
object PocketBaseFilter {
    fun localIdEquals(localId: String): String =
        "localId='${localId.replace("\\", "\\\\").replace("'", "\\'")}'"

    fun accountEquals(accountId: String): String =
        "account='${escape(accountId)}'"

    fun ownerAndLocalIdEquals(accountId: String, localId: String): String =
        "(${accountEquals(accountId)} && ${localIdEquals(localId)})"

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")
}
