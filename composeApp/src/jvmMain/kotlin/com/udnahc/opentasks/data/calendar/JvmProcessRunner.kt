package com.udnahc.opentasks.data.calendar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

internal sealed interface JvmProcessResult {
    data class Completed(
        val exitCode: Int,
        val output: String,
    ) : JvmProcessResult

    data object TimedOut : JvmProcessResult
    data object OutputTooLarge : JvmProcessResult
    data object InvalidUtf8 : JvmProcessResult
    data class Failed(val cause: Exception) : JvmProcessResult
}

/** Runs one bounded child process without allowing its output or lifetime to escape. */
internal class JvmProcessRunner(
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) {
    init {
        require(maxOutputBytes > 0) { "Process output limit must be positive" }
        require(pollIntervalMillis > 0) { "Process poll interval must be positive" }
    }

    suspend fun run(command: List<String>, timeoutMillis: Long): JvmProcessResult {
        require(command.isNotEmpty()) { "Process command must not be empty" }
        require(timeoutMillis > 0) { "Process timeout must be positive" }

        return supervisorScope {
            var process: Process? = null
            var outputReader: Deferred<ByteArray>? = null
            var terminateProcess = false

            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start()
                        .also { process = it }
                }
                val startedProcess = process
                    ?: error("Process did not start")
                val reader = async(Dispatchers.IO) {
                    readOutput(startedProcess)
                }
                outputReader = reader

                val completion = withTimeoutOrNull(timeoutMillis) {
                    val exitCode = awaitExit(startedProcess, reader)
                    exitCode to reader.await()
                }
                if (completion == null) {
                    terminateProcess = true
                    JvmProcessResult.TimedOut
                } else {
                    val (exitCode, output) = completion
                    JvmProcessResult.Completed(
                        exitCode = exitCode,
                        output = decodeUtf8Strict(output),
                    )
                }
            } catch (error: OutputTooLargeException) {
                terminateProcess = true
                JvmProcessResult.OutputTooLarge
            } catch (_: InvalidUtf8Exception) {
                JvmProcessResult.InvalidUtf8
            } catch (error: CancellationException) {
                terminateProcess = true
                throw error
            } catch (error: Exception) {
                terminateProcess = true
                JvmProcessResult.Failed(error)
            } finally {
                process?.let { startedProcess ->
                    if (terminateProcess) {
                        terminate(startedProcess, outputReader)
                    } else {
                        closeStreams(startedProcess)
                    }
                }
            }
        }
    }

    private suspend fun awaitExit(
        process: Process,
        outputReader: Deferred<ByteArray>,
    ): Int {
        while (process.isAlive) {
            if (outputReader.isCompleted) {
                outputReader.await()
            }
            delay(pollIntervalMillis)
        }
        return process.exitValue()
    }

    private suspend fun readOutput(process: Process): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxOutputBytes, READ_BUFFER_SIZE))
        val buffer = ByteArray(READ_BUFFER_SIZE)
        process.inputStream.use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > maxOutputBytes - output.size()) {
                    throw OutputTooLargeException()
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private suspend fun terminate(
        process: Process,
        outputReader: Job?,
    ) {
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                process.destroy()
            } catch (_: Exception) {
                // Cleanup continues through stream closure and forcible destroy.
            }
            closeStreams(process)
            outputReader?.cancelAndJoin()

            if (!waitForExit(process) && process.isAlive) {
                try {
                    process.destroyForcibly()
                } catch (_: Exception) {
                    // The bounded wait below still gives the child a chance to exit.
                }
                waitForExit(process)
            }
            closeStreams(process)
        }
    }

    private fun waitForExit(process: Process): Boolean = try {
        process.waitFor(CLEANUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun closeStreams(process: Process) {
        closeQuietly(process.inputStream)
        closeQuietly(process.errorStream)
        closeQuietly(process.outputStream)
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (_: Exception) {
            // Process teardown is best effort after the child has been stopped.
        }
    }

    private class OutputTooLargeException : IllegalStateException()
    private class InvalidUtf8Exception : IllegalArgumentException()

    private fun decodeUtf8Strict(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        throw InvalidUtf8Exception()
    }

    private companion object {
        const val DEFAULT_MAX_OUTPUT_BYTES = 16 * 1024 * 1024
        const val DEFAULT_POLL_INTERVAL_MILLIS = 10L
        const val CLEANUP_TIMEOUT_MILLIS = 250L
        const val READ_BUFFER_SIZE = 8 * 1024
    }
}
