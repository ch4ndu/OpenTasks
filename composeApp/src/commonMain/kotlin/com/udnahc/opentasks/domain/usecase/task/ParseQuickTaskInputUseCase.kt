package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.computeLocalMillis
import com.udnahc.opentasks.data.model.RecurrenceType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

class ParseQuickTaskInputUseCase {
    operator fun invoke(
        rawInput: String,
        reference: LocalDateTime,
        context: QuickTaskCreationContext,
        suppressedTokenSignatures: Set<String> = emptySet(),
    ): QuickTaskParseResult {
        val suffixCandidates = suffixCandidates(rawInput)
        val selected = QuickTaskTokenKind.entries.mapNotNull { kind ->
            suffixCandidates.lastOrNull { it.kind == kind }
        }
        val timeCandidate = selected.firstOrNull { it.kind == QuickTaskTokenKind.TIME }
        val dateCandidate = selected.firstOrNull { it.kind == QuickTaskTokenKind.DATE }
        val recurrenceCandidate = selected.firstOrNull { it.kind == QuickTaskTokenKind.RECURRENCE }
        val signatures = selected.associateWith { it.signature(rawInput) }
        val activeTime = timeCandidate
            ?.takeIf { signatures.getValue(it) !in suppressedTokenSignatures }
            ?.time
        val resolvedDate = dateCandidate?.resolveDate(reference, activeTime)
        val recurrenceDate = recurrenceCandidate?.resolveRecurrenceDate(reference, activeTime)

        val tokens = selected.sortedBy { it.range.first }.map { candidate ->
            val signature = signatures.getValue(candidate)
            QuickTaskToken(
                kind = candidate.kind,
                sourceRange = candidate.range,
                sourceText = rawInput.substring(candidate.range),
                signature = signature,
                resolvedDate = when (candidate.kind) {
                    QuickTaskTokenKind.DATE -> resolvedDate
                    QuickTaskTokenKind.RECURRENCE -> recurrenceDate
                    QuickTaskTokenKind.TIME -> null
                },
                resolvedTime = candidate.time,
                recurrenceType = candidate.recurrenceType,
                isActive = signature !in suppressedTokenSignatures,
            )
        }

        val activeDate = tokens.firstOrNull { it.kind == QuickTaskTokenKind.DATE && it.isActive }
            ?.resolvedDate
        val activeRecurrence = tokens.firstOrNull {
            it.kind == QuickTaskTokenKind.RECURRENCE && it.isActive
        }
        val deadlineDate = activeDate
            ?: activeRecurrence?.resolvedDate
            ?: context.fallbackDate
            ?: activeTime?.let { time -> implicitTimeDate(reference, time) }
        val deadlineTime = activeTime ?: DEFAULT_TIME
        val deadline = deadlineDate?.let { date ->
            computeLocalMillis(
                year = date.year,
                month = date.month.ordinal + 1,
                day = date.day,
                hour = deadlineTime.hour,
                minute = deadlineTime.minute,
            )
        }
        val recurrenceType = activeRecurrence?.recurrenceType ?: RecurrenceType.NONE
        val cleanedTitle = cleanTitle(rawInput, tokens.filter { it.isActive }.map { it.sourceRange })

        return QuickTaskParseResult(
            rawInput = rawInput,
            cleanedTitle = cleanedTitle,
            deadline = deadline,
            isAllDay = deadline != null && activeTime == null,
            recurrenceType = recurrenceType,
            recognizedTokens = tokens,
        )
    }

    private fun suffixCandidates(input: String): List<Candidate> {
        val candidates = buildCandidates(input)
        val suffix = mutableListOf<Candidate>()
        var suffixStart = input.length
        while (true) {
            val candidate = candidates
                .asSequence()
                .filter { it.range.last < suffixStart }
                .filter { onlySeparators(input, it.range.last + 1, suffixStart) }
                .maxWithOrNull(compareBy<Candidate> { it.range.last }.thenByDescending { it.range.first })
                ?: break
            suffix += candidate
            suffixStart = candidate.range.first
        }
        return suffix.sortedBy { it.range.first }
    }

    private fun buildCandidates(input: String): List<Candidate> = buildList {
        RECURRENCE_REGEX.findAll(input).forEach { match ->
            val normalized = match.value.lowercase().trim()
            val weekday = weekdayFromName(match.groups[1]?.value)
            val recurrence = when (normalized) {
                "every day", "daily" -> RecurrenceType.DAILY
                "every week", "weekly" -> RecurrenceType.WEEKLY
                "every month", "monthly" -> RecurrenceType.MONTHLY
                "every year", "yearly" -> RecurrenceType.YEARLY
                "every weekday" -> RecurrenceType.EVERY_WEEKDAY
                else -> RecurrenceType.WEEKLY
            }
            add(
                Candidate(
                    kind = QuickTaskTokenKind.RECURRENCE,
                    range = match.range,
                    recurrenceType = recurrence,
                    weekday = weekday,
                )
            )
        }
        DATE_REGEX.findAll(input).forEach { match ->
            val normalized = match.value.lowercase().trim()
            val amount = match.groups[1]?.value?.toIntOrNull()
            val unit = match.groups[2]?.value?.lowercase()
            val weekday = weekdayFromName(match.groups[3]?.value ?: normalized)
            val valid = when (unit) {
                "day", "days" -> amount in 1..365
                "week", "weeks" -> amount in 1..52
                else -> normalized == "today" || normalized == "tomorrow" || weekday != null
            }
            if (valid) {
                add(
                    Candidate(
                        kind = QuickTaskTokenKind.DATE,
                        range = match.range,
                        relativeAmount = amount,
                        relativeUnit = unit,
                        weekday = weekday,
                        isToday = normalized == "today",
                        isTomorrow = normalized == "tomorrow",
                    )
                )
            }
        }
        TIME_REGEX.findAll(input).forEach { match ->
            val hour12 = match.groups[1]?.value?.toIntOrNull()
            val minute12 = match.groups[2]?.value?.toIntOrNull() ?: 0
            val meridiem = match.groups[3]?.value?.lowercase()
            val hour24 = match.groups[4]?.value?.toIntOrNull()
            val minute24 = match.groups[5]?.value?.toIntOrNull()
            val time = when {
                hour12 != null && hour12 in 1..12 && minute12 in 0..59 && meridiem != null -> {
                    val normalizedHour = when {
                        meridiem == "am" && hour12 == 12 -> 0
                        meridiem == "pm" && hour12 != 12 -> hour12 + 12
                        else -> hour12
                    }
                    LocalTime(normalizedHour, minute12)
                }
                hour24 != null && hour24 in 0..23 && minute24 != null && minute24 in 0..59 ->
                    LocalTime(hour24, minute24)
                else -> null
            }
            if (time != null) {
                add(Candidate(QuickTaskTokenKind.TIME, match.range, time = time))
            }
        }
    }

    private fun Candidate.resolveDate(reference: LocalDateTime, time: LocalTime?): LocalDate = when {
        isToday -> reference.date
        isTomorrow -> reference.date.plus(1, DateTimeUnit.DAY)
        relativeUnit == "day" || relativeUnit == "days" ->
            reference.date.plus(relativeAmount ?: 0, DateTimeUnit.DAY)
        relativeUnit == "week" || relativeUnit == "weeks" ->
            reference.date.plus((relativeAmount ?: 0) * 7, DateTimeUnit.DAY)
        weekday != null -> nextWeekday(reference, weekday, time)
        else -> reference.date
    }

    private fun Candidate.resolveRecurrenceDate(
        reference: LocalDateTime,
        time: LocalTime?,
    ): LocalDate {
        val occurrenceTime = time ?: DEFAULT_TIME
        weekday?.let { return nextStrictOccurrence(reference, it, occurrenceTime) }
        if (recurrenceType == RecurrenceType.EVERY_WEEKDAY) {
            var date = reference.date
            while (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY ||
                LocalDateTime(date, occurrenceTime) <= reference
            ) {
                date = date.plus(1, DateTimeUnit.DAY)
            }
            return date
        }
        val todayOccurrence = LocalDateTime(reference.date, occurrenceTime)
        if (todayOccurrence > reference) return reference.date
        return when (recurrenceType) {
            RecurrenceType.DAILY -> reference.date.plus(1, DateTimeUnit.DAY)
            RecurrenceType.WEEKLY -> reference.date.plus(7, DateTimeUnit.DAY)
            RecurrenceType.MONTHLY -> reference.date.plus(1, DateTimeUnit.MONTH)
            RecurrenceType.YEARLY -> reference.date.plus(1, DateTimeUnit.YEAR)
            RecurrenceType.EVERY_WEEKDAY, RecurrenceType.NONE -> reference.date.plus(1, DateTimeUnit.DAY)
        }
    }

    private fun nextWeekday(
        reference: LocalDateTime,
        weekday: DayOfWeek,
        time: LocalTime?,
    ): LocalDate {
        var date = reference.date
        while (date.dayOfWeek != weekday) date = date.plus(1, DateTimeUnit.DAY)
        if (date == reference.date && (time == null || LocalDateTime(date, time) <= reference)) {
            date = date.plus(7, DateTimeUnit.DAY)
        }
        return date
    }

    private fun nextStrictOccurrence(
        reference: LocalDateTime,
        weekday: DayOfWeek,
        time: LocalTime,
    ): LocalDate {
        var date = reference.date
        while (date.dayOfWeek != weekday || LocalDateTime(date, time) <= reference) {
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return date
    }

    private fun implicitTimeDate(reference: LocalDateTime, time: LocalTime): LocalDate =
        if (LocalDateTime(reference.date, time) > reference) {
            reference.date
        } else {
            reference.date.plus(1, DateTimeUnit.DAY)
        }

    private fun Candidate.signature(input: String): String =
        "${kind.name}:${input.substring(range).lowercase().replace(WHITESPACE_REGEX, " ").trim()}"

    private fun cleanTitle(input: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return normalizeTitle(input)
        val removed = buildString(input.length) {
            input.forEachIndexed { index, character ->
                append(if (ranges.any { index in it }) ' ' else character)
            }
        }
        return normalizeTitle(removed)
    }

    private fun normalizeTitle(value: String): String = value
        .replace(WHITESPACE_REGEX, " ")
        .replace(SPACE_BEFORE_PUNCTUATION_REGEX, "$1")
        .trim()
        .trim(',', ';', ':', '-', '\u2013', '\u2014')
        .trim()

    private fun onlySeparators(input: String, start: Int, endExclusive: Int): Boolean {
        if (start >= endExclusive) return true
        return input.substring(start, endExclusive).all {
            it.isWhitespace() || it == ',' || it == ';' || it == ':' || it == '-' || it == '\u2013' || it == '\u2014'
        }
    }

    private fun weekdayFromName(value: String?): DayOfWeek? = when (value?.lowercase()) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        "sunday" -> DayOfWeek.SUNDAY
        else -> null
    }

    private data class Candidate(
        val kind: QuickTaskTokenKind,
        val range: IntRange,
        val time: LocalTime? = null,
        val recurrenceType: RecurrenceType = RecurrenceType.NONE,
        val relativeAmount: Int? = null,
        val relativeUnit: String? = null,
        val weekday: DayOfWeek? = null,
        val isToday: Boolean = false,
        val isTomorrow: Boolean = false,
    )

    private companion object {
        val DEFAULT_TIME = LocalTime(8, 0)
        val WEEKDAY_PATTERN = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
        val RECURRENCE_REGEX = Regex(
            "\\b(?:every\\s+(?:day|week|month|year|weekday|($WEEKDAY_PATTERN))|daily|weekly|monthly|yearly)\\b",
            RegexOption.IGNORE_CASE,
        )
        val DATE_REGEX = Regex(
            "\\b(?:today|tomorrow|in\\s+(\\d{1,3})\\s+(day|days|week|weeks)|($WEEKDAY_PATTERN))\\b",
            RegexOption.IGNORE_CASE,
        )
        val TIME_REGEX = Regex(
            "\\bat\\s+(?:(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)|(\\d{2}):(\\d{2}))\\b",
            RegexOption.IGNORE_CASE,
        )
        val WHITESPACE_REGEX = Regex("\\s+")
        val SPACE_BEFORE_PUNCTUATION_REGEX = Regex("\\s+([,.!?])")
    }
}
