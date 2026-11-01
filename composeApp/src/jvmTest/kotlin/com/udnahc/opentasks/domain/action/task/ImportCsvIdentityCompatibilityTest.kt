package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.calendar.CsvGenerator
import com.udnahc.opentasks.data.calendar.CsvParser
import com.udnahc.opentasks.data.calendar.CsvTask
import com.udnahc.opentasks.data.extensions.computeDeadlineUtcMillis
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportCsvIdentityCompatibilityTest {
    @Test
    fun parserImprovementsRetainHistoricalIdentityForEachRecurrenceSpelling() = runTest {
        val cases = listOf(
            Triple("freq=daily", RecurrenceType.DAILY, RecurrenceType.NONE),
            Triple("FREQ =DAILY", RecurrenceType.DAILY, RecurrenceType.NONE),
            Triple("FREQ= DAILY", RecurrenceType.DAILY, RecurrenceType.NONE),
            Triple("freq=daily;byday=mo,tu,we,th,fr", RecurrenceType.EVERY_WEEKDAY, RecurrenceType.NONE),
            Triple("FREQ = DAILY;BYDAY=MO,TU,WE,TH,FR", RecurrenceType.EVERY_WEEKDAY, RecurrenceType.NONE),
            Triple("FREQ=daily", RecurrenceType.DAILY, RecurrenceType.DAILY),
            Triple("RRULE:FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR", RecurrenceType.EVERY_WEEKDAY, RecurrenceType.DAILY),
            Triple("FREQ=WEEKLY", RecurrenceType.WEEKLY, RecurrenceType.WEEKLY),
            Triple("FREQ=DAILY;FREQ=WEEKLY", RecurrenceType.WEEKLY, RecurrenceType.DAILY),
            Triple("X-FREQ=MONTHLY", RecurrenceType.NONE, RecurrenceType.MONTHLY),
        )
        for ((rule, interpreted, historical) in cases) {
            val row = rowForRule(rule)
            val previousRow = row.copy(recurrenceType = historical, identityRecurrenceType = historical)
            val previousId = ImportedIdentityBatch().nextCsv(previousRow).canonicalId
            val existing = testTask(id = "prior-import", recurrenceType = historical, sourceExternalId = previousId)
            val repository = FakeTaskRepository(listOf(existing))

            assertEquals(interpreted, row.recurrenceType, rule)
            assertEquals(historical, row.identityRecurrenceType, rule)
            assertEquals(previousId, ImportedIdentityBatch().nextCsv(row).canonicalId, rule)
            assertEquals(0, action(repository)(listOf(row)), rule)
            assertTrue(repository.inserted.isEmpty(), rule)
            assertEquals(existing, repository.tasks.single(), rule)
        }
    }

    @Test
    fun newlyRecognizedRulesKeepHistoricalNoneOrdinalsSeparateFromDailyControls() = runTest {
        val rows = listOf(
            "",
            "freq=daily",
            "FREQ = DAILY;BYDAY=MO,TU,WE,TH,FR",
            "FREQ=DAILY",
            "freq=weekly",
            "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR",
        ).map(::rowForRule)
        val historicalRecurrences = listOf(
            RecurrenceType.NONE, RecurrenceType.NONE, RecurrenceType.NONE,
            RecurrenceType.DAILY, RecurrenceType.NONE, RecurrenceType.DAILY,
        )
        val previousBatch = ImportedIdentityBatch()
        val previousIds = rows.zip(historicalRecurrences).map { (row, recurrence) ->
            previousBatch.nextCsv(row.copy(recurrenceType = recurrence, identityRecurrenceType = recurrence)).canonicalId
        }
        val currentBatch = ImportedIdentityBatch()
        assertEquals(previousIds, rows.map { currentBatch.nextCsv(it).canonicalId })
        assertEquals(rows.size, previousIds.toSet().size)
        val repository = FakeTaskRepository(listOf(0, 1, 3).map { index ->
            testTask(id = "prior-$index", recurrenceType = historicalRecurrences[index], sourceExternalId = previousIds[index])
        })
        val import = action(repository)

        assertEquals(3, import(rows))
        assertEquals(listOf(2, 4, 5).map(previousIds::get), repository.inserted.map { it.sourceExternalId })
        assertEquals(
            listOf(RecurrenceType.EVERY_WEEKDAY, RecurrenceType.WEEKLY, RecurrenceType.EVERY_WEEKDAY),
            repository.inserted.map { it.recurrenceType },
        )
        assertEquals(0, import(rows))
        assertEquals(rows.size, repository.tasks.size)
    }

    @Test
    fun previouslyImportedWeekdayRowKeepsItsCanonicalIdentity() = runTest {
        val row = rows(RecurrenceType.EVERY_WEEKDAY).single()
        val previousRow = row.copy(recurrenceType = RecurrenceType.DAILY, identityRecurrenceType = RecurrenceType.DAILY)
        val previousId = ImportedIdentityBatch().nextCsv(previousRow).canonicalId
        val existing = testTask(id = "prior-import", recurrenceType = RecurrenceType.DAILY, sourceExternalId = previousId)
        val repository = FakeTaskRepository(listOf(existing))

        assertEquals(RecurrenceType.EVERY_WEEKDAY, row.recurrenceType)
        assertEquals(previousId, ImportedIdentityBatch().nextCsv(row).canonicalId)
        assertEquals(0, action(repository)(listOf(row)))
        assertTrue(repository.inserted.isEmpty())
        assertEquals(existing, repository.tasks.single())
    }

    @Test
    fun freshMixedWeekdayAndDailyRowsRetainDistinctOrdinalsAndCorrectRecurrence() = runTest {
        val rows = rows(RecurrenceType.EVERY_WEEKDAY, RecurrenceType.DAILY, RecurrenceType.EVERY_WEEKDAY)
        val previousBatch = ImportedIdentityBatch()
        val previousIds = rows.map { row ->
            previousBatch.nextCsv(row.copy(recurrenceType = RecurrenceType.DAILY, identityRecurrenceType = RecurrenceType.DAILY)).canonicalId
        }
        val currentBatch = ImportedIdentityBatch()
        assertEquals(previousIds, rows.map { currentBatch.nextCsv(it).canonicalId })
        assertEquals(3, previousIds.toSet().size)

        val repository = FakeTaskRepository()
        val import = action(repository)
        assertEquals(3, import(rows))
        assertEquals(rows.map(CsvTask::recurrenceType), repository.inserted.map { it.recurrenceType })
        assertEquals(previousIds, repository.inserted.map { it.sourceExternalId })
        assertEquals(0, import(rows))
    }

    @Test
    fun partiallyImportedMixedBatchConsumesEachHistoricalOrdinalOnce() = runTest {
        val rows = rows(RecurrenceType.DAILY, RecurrenceType.EVERY_WEEKDAY, RecurrenceType.EVERY_WEEKDAY)
        val previousBatch = ImportedIdentityBatch()
        val previousIds = rows.map { row ->
            previousBatch.nextCsv(row.copy(recurrenceType = RecurrenceType.DAILY, identityRecurrenceType = RecurrenceType.DAILY)).canonicalId
        }
        val repository = FakeTaskRepository(previousIds.take(2).mapIndexed { index, id ->
            testTask(id = "prior-$index", recurrenceType = RecurrenceType.DAILY, sourceExternalId = id)
        })
        val import = action(repository)

        assertEquals(1, import(rows))
        assertEquals(previousIds.last(), repository.inserted.single().sourceExternalId)
        assertEquals(RecurrenceType.EVERY_WEEKDAY, repository.inserted.single().recurrenceType)
        assertEquals(0, import(rows))
        assertEquals(3, repository.tasks.size)
    }

    private fun rowForRule(rule: String): CsvTask = CsvParser.parse(
        CsvGenerator.generate(
            listOf(testTask(title = "Same task", recurrenceType = RecurrenceType.DAILY)),
            emptyList(),
        ).replace("RRULE:FREQ=DAILY", rule),
    ).single()

    private fun rows(vararg recurrences: RecurrenceType): List<CsvTask> = CsvParser.parse(
        CsvGenerator.generate(recurrences.mapIndexed { index, recurrence ->
            testTask(
                id = "source-$index",
                title = "Same task",
                content = "Same content",
                deadline = computeDeadlineUtcMillis(2026, 5, 8, 9, 0),
                recurrenceType = recurrence,
            )
        }, emptyList()),
    )

    private fun action(repository: FakeTaskRepository) = ImportCsvTasksAction(
        repository,
        FakeCategoryRepository(),
        ScheduleTaskRemindersAction(NotificationScheduler(), repository),
    )
}
