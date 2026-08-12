package com.udnahc.opentasks.data.calendar

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class JvmProcessRunnerTest {
    @Test
    fun completesAndReturnsMergedOutput(): Unit = runBlocking {
        val result = JvmProcessRunner().run(
            command = listOf("sh", "-c", "printf success"),
            timeoutMillis = 1_000L,
        )

        val completed = assertIs<JvmProcessResult.Completed>(result)
        assertEquals(0, completed.exitCode)
        assertEquals("success", completed.output)
    }

    @Test
    fun returnsNonZeroExitCodeAndStderrOutput(): Unit = runBlocking {
        val result = JvmProcessRunner().run(
            command = listOf("sh", "-c", "printf failure >&2; exit 7"),
            timeoutMillis = 1_000L,
        )

        val completed = assertIs<JvmProcessResult.Completed>(result)
        assertEquals(7, completed.exitCode)
        assertEquals("failure", completed.output)
    }

    @Test
    fun terminatesAProcessThatExceedsTheTimeout(): Unit = runBlocking {
        val result = JvmProcessRunner().run(
            command = listOf("sh", "-c", "while :; do :; done"),
            timeoutMillis = 100L,
        )

        assertIs<JvmProcessResult.TimedOut>(result)
    }

    @Test
    fun cancellationTerminatesStartedChildProcess(): Unit = runBlocking {
        val pidFile = File.createTempFile("jvm-process-runner", ".pid")
        var runnerJob: Job? = null
        try {
            runnerJob = launch {
                JvmProcessRunner().run(
                    command = listOf(
                        "sh",
                        "-c",
                        "echo ${'$'}${'$'} > '${pidFile.absolutePath}'; while :; do :; done",
                    ),
                    timeoutMillis = 10_000L,
                )
            }

            var childPid: Long? = null
            withTimeout(2_000L) {
                while (childPid == null) {
                    childPid = pidFile.takeIf { it.isFile }
                        ?.readText()
                        ?.trim()
                        ?.toLongOrNull()
                    if (childPid == null) delay(10L)
                }
            }
            val child = ProcessHandle.of(childPid ?: error("The child PID was not captured")).orElse(null)
                ?: error("The child process handle was not available")

            runnerJob.cancelAndJoin()

            withTimeout(2_000L) {
                while (child.isAlive) delay(10L)
            }
            assertFalse(child.isAlive)
        } finally {
            runnerJob?.cancelAndJoin()
            pidFile.delete()
        }
    }

    @Test
    fun rejectsOutputBeyondTheConfiguredLimit(): Unit = runBlocking {
        val result = JvmProcessRunner(maxOutputBytes = 64).run(
            command = listOf("sh", "-c", "printf '%s' '${"x".repeat(128)}'"),
            timeoutMillis = 1_000L,
        )

        assertIs<JvmProcessResult.OutputTooLarge>(result)
    }
}
