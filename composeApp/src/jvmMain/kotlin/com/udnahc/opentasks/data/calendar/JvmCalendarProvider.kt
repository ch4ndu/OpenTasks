package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.model.CalendarEvent
import com.udnahc.opentasks.data.model.CalendarEventSourceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val IS_MAC = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

class JvmCalendarProvider internal constructor(
    private val processRunner: JvmProcessRunner = JvmProcessRunner(),
) : CalendarProvider {

    override fun isAvailable(): Boolean = IS_MAC

    override fun supportsExplicitImportWithoutPermissionRequest(): Boolean = IS_MAC

    override suspend fun checkPermission(): CalendarPermissionStatus =
        if (IS_MAC) CalendarPermissionStatus.NOT_DETERMINED else CalendarPermissionStatus.NOT_AVAILABLE

    override suspend fun requestPermission(): CalendarPermissionStatus = checkPermission()

    override suspend fun fetchEvents(
        startUtcMillis: Long,
        endUtcMillis: Long,
    ): List<CalendarEvent> {
        if (!IS_MAC) return emptyList()

        return withContext(Dispatchers.IO) {
            val result = try {
                processRunner.run(
                    command = listOf(
                        "osascript",
                        "-l",
                        "JavaScript",
                        "-e",
                        CALENDAR_JXA,
                        "--",
                        startUtcMillis.toString(),
                        endUtcMillis.toString(),
                    ),
                    timeoutMillis = FETCH_TIMEOUT_MILLIS,
                )
            } catch (error: CancellationException) {
                throw error
            }

            when (result) {
                is JvmProcessResult.Completed -> {
                    if (result.exitCode != 0) {
                        throw CalendarProviderException(CalendarProviderFailure.TRANSPORT)
                    }
                    parseJxaOutput(result.output)
                }

                JvmProcessResult.TimedOut,
                JvmProcessResult.OutputTooLarge,
                is JvmProcessResult.Failed ->
                    throw CalendarProviderException(CalendarProviderFailure.TRANSPORT)

                JvmProcessResult.InvalidUtf8 ->
                    throw CalendarProviderException(CalendarProviderFailure.INVALID_RESPONSE)
            }
        }
    }

    private suspend fun parseJxaOutput(output: String): List<CalendarEvent> {
        currentCoroutineContext().ensureActive()
        val root = try {
            Json.parseToJsonElement(output).jsonObject
        } catch (_: Exception) {
            throw CalendarProviderException(CalendarProviderFailure.INVALID_RESPONSE)
        }

        val version = root.requiredInt("version")
        if (version != RESPONSE_VERSION) invalidResponse()

        if (root.containsKey("error")) {
            root.requireExactKeys(setOf("version", "error"))
            when (root.requiredString("error")) {
                "denied" -> throw CalendarProviderException(CalendarProviderFailure.ACCESS_DENIED)
                "overflow" -> throw CalendarProviderException(CalendarProviderFailure.TOO_MANY_EVENTS)
                "transport" -> throw CalendarProviderException(CalendarProviderFailure.TRANSPORT)
                else -> invalidResponse()
            }
        }

        root.requireExactKeys(setOf("version", "events"))
        val rows = try {
            root.getValue("events").jsonArray
        } catch (_: Exception) {
            invalidResponse()
        }
        if (rows.size > MAX_CALENDAR_PROVIDER_EVENTS) {
            throw CalendarProviderException(CalendarProviderFailure.TOO_MANY_EVENTS)
        }

        currentCoroutineContext().ensureActive()
        val events = ArrayList<CalendarEvent>(rows.size)
        for (element in rows) {
            currentCoroutineContext().ensureActive()
            events.add(parseEvent(element))
        }
        val sorted = events.sortedWith(CALENDAR_PROVIDER_EVENT_ORDER)
        currentCoroutineContext().ensureActive()
        return sorted
    }

    private fun parseEvent(element: JsonElement): CalendarEvent {
        val row = try {
            element.jsonObject
        } catch (_: Exception) {
            invalidResponse()
        }
        row.requireExactKeys(EVENT_KEYS)
        val uid = row.requiredString("uid")
        val title = row.requiredString("title")
        if (title.isBlank()) invalidResponse()
        val startMillis = row.requiredLong("startMillis")
        val endMillis = row.nullableLong("endMillis")
        val isAllDay = row.requiredBoolean("isAllDay")
        val occurrenceToken = if (isAllDay) row.requiredLong("occurrenceDay") else startMillis

        return CalendarEvent(
            externalId = "mac_$uid",
            title = title,
            description = row.requiredString("description"),
            startTimeUtcMillis = startMillis,
            endTimeUtcMillis = endMillis,
            calendarName = row.requiredString("calendarName"),
            isAllDay = isAllDay,
            location = row.requiredString("location"),
            url = row.requiredString("url"),
            sourceKind = CalendarEventSourceKind.MACOS,
            rawUid = uid.ifBlank { null },
            occurrenceToken = occurrenceToken,
        )
    }

    private companion object {
        const val FETCH_TIMEOUT_MILLIS = 30_000L
        const val RESPONSE_VERSION = 1

        val EVENT_KEYS = setOf(
            "uid",
            "title",
            "description",
            "startMillis",
            "endMillis",
            "calendarName",
            "isAllDay",
            "occurrenceDay",
            "location",
            "url",
        )

        val CALENDAR_JXA = """
            function run(argv) {
                function fixedError(code) {
                    return JSON.stringify({version: 1, error: code});
                }
                function safeString(read) {
                    try {
                        var value = read();
                        return value == null ? "" : String(value);
                    } catch (_) {
                        return "";
                    }
                }
                function localEpochDay(date) {
                    var millisPerDay = 24 * 60 * 60 * 1000;
                    return Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / millisPerDay);
                }

                if (argv.length !== 2 || !/^-?\d+$/.test(argv[0]) || !/^-?\d+$/.test(argv[1])) {
                    return fixedError("transport");
                }
                var lower = Number(argv[0]);
                var upper = Number(argv[1]);
                if (!Number.isSafeInteger(lower) || !Number.isSafeInteger(upper) || lower > upper) {
                    return fixedError("transport");
                }

                try {
                    var calendarApplication = Application("Calendar");
                    var calendars = calendarApplication.calendars();
                    var rows = [];
                    for (var calendarIndex = 0; calendarIndex < calendars.length; calendarIndex++) {
                        var calendar = calendars[calendarIndex];
                        var calendarName = safeString(function () { return calendar.name(); });
                        var matchingEvents = calendar.events.whose({_and: [
                            {startDate: {_greaterThanEquals: new Date(lower)}},
                            {startDate: {_lessThanEquals: new Date(upper)}}
                        ]});
                        for (var eventIndex = 0; ; eventIndex++) {
                            var event = matchingEvents.at(eventIndex);
                            if (!event.exists()) {
                                break;
                            }
                            var startDate = event.startDate();
                            if (!(startDate instanceof Date)) {
                                return fixedError("transport");
                            }
                            var startMillis = startDate.getTime();
                            var title = safeString(function () { return event.summary(); });
                            if (title.trim().length === 0) {
                                continue;
                            }
                            if (rows.length === 10000) {
                                return fixedError("overflow");
                            }
                            var endDate = null;
                            try { endDate = event.endDate(); } catch (_) {}
                            var isAllDay = false;
                            try { isAllDay = Boolean(event.alldayEvent()); } catch (_) {}
                            rows.push({
                                uid: safeString(function () { return event.uid(); }),
                                title: title,
                                description: safeString(function () { return event.description(); }),
                                startMillis: startMillis,
                                endMillis: endDate instanceof Date ? endDate.getTime() : null,
                                calendarName: calendarName,
                                isAllDay: isAllDay,
                                occurrenceDay: localEpochDay(startDate),
                                location: safeString(function () { return event.location(); }),
                                url: safeString(function () { return event.url(); })
                            });
                        }
                    }
                    return JSON.stringify({version: 1, events: rows});
                } catch (error) {
                    var errorNumber = Number(error && error.number);
                    return fixedError(errorNumber === -1743 ? "denied" : "transport");
                }
            }
        """.trimIndent()
    }
}

private fun JsonObject.requireExactKeys(expected: Set<String>) {
    if (keys != expected) invalidResponse()
}

private fun JsonObject.requiredString(key: String): String {
    val primitive = this[key] as? JsonPrimitive ?: invalidResponse()
    if (!primitive.isString) invalidResponse()
    return primitive.content
}

private fun JsonObject.requiredInt(key: String): Int {
    val primitive = this[key] as? JsonPrimitive ?: invalidResponse()
    if (primitive.isString) invalidResponse()
    return primitive.intOrNull ?: invalidResponse()
}

private fun JsonObject.requiredLong(key: String): Long {
    val primitive = this[key] as? JsonPrimitive ?: invalidResponse()
    if (primitive.isString) invalidResponse()
    return primitive.longOrNull ?: invalidResponse()
}

private fun JsonObject.nullableLong(key: String): Long? {
    val element = this[key] ?: invalidResponse()
    if (element === JsonNull) return null
    val primitive = element as? JsonPrimitive ?: invalidResponse()
    if (primitive.isString) invalidResponse()
    return primitive.longOrNull ?: invalidResponse()
}

private fun JsonObject.requiredBoolean(key: String): Boolean {
    val primitive = this[key] as? JsonPrimitive ?: invalidResponse()
    if (primitive.isString) invalidResponse()
    return primitive.booleanOrNull ?: invalidResponse()
}

private fun invalidResponse(): Nothing =
    throw CalendarProviderException(CalendarProviderFailure.INVALID_RESPONSE)
