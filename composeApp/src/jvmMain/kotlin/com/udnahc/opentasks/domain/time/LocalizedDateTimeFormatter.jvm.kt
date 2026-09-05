package com.udnahc.opentasks.domain.time

import java.time.Instant
import java.time.ZoneOffset
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

actual class LocalizedDateTimeFormatter actual constructor() : DateTimeTextFormatter {
    private val locale: Locale
        get() = Locale.getDefault()

    override val formattingContextKey: String
        get() = locale.toLanguageTag()

    override fun formatShortDate(localMillis: Long): String =
        formatDateWithoutYear(localMillis, FormatStyle.MEDIUM)

    override fun formatDateWithYear(localMillis: Long): String =
        formatDate(localMillis, FormatStyle.MEDIUM)

    override fun formatDateLabel(localMillis: Long): String =
        formatDateWithoutYear(localMillis, FormatStyle.FULL)

    override fun formatTime(localMillis: Long): String =
        localDateTime(localMillis).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    override fun formatHour(hour: Int): String {
        val normalized = ((hour % 24) + 24) % 24
        return localDateTime(normalized * 60L * 60L * 1000L)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }

    override fun formatMonthYear(localMillis: Long): String =
        localDateTime(localMillis).format(DateTimeFormatter.ofPattern("MMMM y", locale))

    override fun formatShortWeekday(localMillis: Long): String =
        localDateTime(localMillis).format(DateTimeFormatter.ofPattern("EEE", locale))

    override fun formatWeekRange(startLocalMillis: Long, endLocalMillis: Long): String =
        "${formatShortDate(startLocalMillis)} – ${formatShortDate(endLocalMillis)}"

    private fun formatDateWithoutYear(localMillis: Long, style: FormatStyle): String {
        val pattern = localizedDatePattern(style)
        return localDateTime(localMillis).format(
            DateTimeFormatter.ofPattern(pattern.withoutYearOrEraCluster(), locale),
        )
    }

    private fun formatDate(localMillis: Long, style: FormatStyle): String =
        localDateTime(localMillis).format(
            DateTimeFormatter.ofPattern(localizedDatePattern(style), locale),
        )

    private fun localizedDatePattern(style: FormatStyle): String =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            style,
            null,
            IsoChronology.INSTANCE,
            locale,
        )

    private fun localDateTime(localMillis: Long) =
        Instant.ofEpochMilli(localMillis).atZone(ZoneOffset.UTC).toLocalDateTime()
}

private data class DatePatternPart(
    val raw: String,
    val field: Char? = null,
)

/** Removes an outer localized year/era cluster without treating quoted literals as fields. */
private fun String.withoutYearOrEraCluster(): String {
    val parts = toDatePatternParts()
    val clusterFieldIndices = parts.indices.filter { index -> parts[index].field.isYearOrEraField() }
    if (clusterFieldIndices.isEmpty()) return this

    val clusterStart = clusterFieldIndices.first()
    val clusterEnd = clusterFieldIndices.last()
    val clusterIsContiguous = (clusterStart..clusterEnd).none { index ->
        val field = parts[index].field
        field != null && !field.isYearOrEraField()
    }
    if (!clusterIsContiguous) return parts.safeYearlessFieldPattern()

    val previousFieldIndex = (clusterStart - 1 downTo 0).firstOrNull { parts[it].field != null }
    val nextFieldIndex = (clusterEnd + 1 until parts.size).firstOrNull { parts[it].field != null }
    val retainedParts = when {
        previousFieldIndex == null && nextFieldIndex != null -> {
            val firstRetainedIndex = if (
                clusterEnd + 1 < parts.size && parts[clusterEnd + 1].field == null
            ) {
                clusterEnd + 2
            } else {
                clusterEnd + 1
            }
            parts.drop(firstRetainedIndex)
        }
        previousFieldIndex != null && nextFieldIndex == null -> {
            val firstRemovedIndex = if (
                clusterStart > 0 && parts[clusterStart - 1].field == null
            ) {
                clusterStart - 1
            } else {
                clusterStart
            }
            parts.take(firstRemovedIndex)
        }
        else -> return parts.safeYearlessFieldPattern()
    }
    return retainedParts
        .joinToString(separator = "", transform = DatePatternPart::raw)
        .takeIf { it.isNotBlank() }
        ?: parts.safeYearlessFieldPattern()
}

private fun List<DatePatternPart>.safeYearlessFieldPattern(): String =
    filter { part -> part.field != null && !part.field.isYearOrEraField() }
        .joinToString(separator = " ", transform = DatePatternPart::raw)
        .ifBlank { "MMM d" }

private fun String.toDatePatternParts(): List<DatePatternPart> {
    val parts = mutableListOf<DatePatternPart>()
    val literal = StringBuilder()
    fun flushLiteral() {
        if (literal.isNotEmpty()) {
            parts += DatePatternPart(literal.toString())
            literal.clear()
        }
    }

    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            character == '\'' -> {
                literal.append(character)
                index += 1
                while (index < length) {
                    val quoted = this[index]
                    literal.append(quoted)
                    index += 1
                    if (quoted != '\'') continue
                    if (index < length && this[index] == '\'') {
                        literal.append(this[index])
                        index += 1
                    } else {
                        break
                    }
                }
            }
            character.isAsciiLetter() -> {
                flushLiteral()
                val start = index
                while (index < length && this[index] == character) index += 1
                parts += DatePatternPart(substring(start, index), character)
            }
            else -> {
                literal.append(character)
                index += 1
            }
        }
    }
    flushLiteral()
    return parts
}

private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

private fun Char?.isYearOrEraField(): Boolean =
    this == 'y' || this == 'u' || this == 'Y' || this == 'G'
