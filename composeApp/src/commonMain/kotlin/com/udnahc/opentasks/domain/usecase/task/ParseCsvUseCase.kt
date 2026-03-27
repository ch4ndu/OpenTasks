package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.calendar.CsvParser
import com.udnahc.opentasks.data.calendar.CsvTask

class ParseCsvUseCase {
    operator fun invoke(csvContent: String): List<CsvTask> =
        CsvParser.parse(csvContent)
}
