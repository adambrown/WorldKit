package com.grimfox.logging

import com.grimfox.logging.LoggingLevel.*
import kotlinx.coroutines.experimental.async
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

private class SynchronizedPrintStream(stream: OutputStream) : PrintStream(stream, true) {

    override fun print(b: Boolean) {
        synchronized(this) {
            super.print(b)
        }
    }

    override fun print(c: Char) {
        synchronized(this) {
            super.print(c)
        }
    }

    override fun print(i: Int) {
        synchronized(this) {
            super.print(i)
        }
    }

    override fun print(l: Long) {
        synchronized(this) {
            super.print(l)
        }
    }

    override fun print(f: Float) {
        synchronized(this) {
            super.print(f)
        }
    }

    override fun print(d: Double) {
        synchronized(this) {
            super.print(d)
        }
    }

    override fun print(s: CharArray?) {
        synchronized(this) {
            super.print(s)
        }
    }

    override fun print(s: String?) {
        synchronized(this) {
            super.print(s)
        }
    }

    override fun print(obj: Any?) {
        synchronized(this) {
            super.print(obj)
        }
    }

    override fun write(b: Int) {
        synchronized(this) {
            super.write(b)
        }
    }

    override fun write(buf: ByteArray?, off: Int, len: Int) {
        synchronized(this) {
            super.write(buf, off, len)
        }
    }

    override fun write(b: ByteArray?) {
        synchronized(this) {
            super.write(b)
        }
    }

    override fun println() {
        synchronized(this) {
            super.println()
        }
    }

    override fun println(x: Boolean) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun println(x: Char) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun println(x: Int) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun println(x: Long) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun println(x: Float) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun println(x: Double) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun println(x: CharArray?) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun println(x: String?) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun println(x: Any?) {
        synchronized(this) {
            super.println(x)
        }
    }

    override fun flush() {
        synchronized(this) {
            super.flush()
        }
    }

    override fun checkError(): Boolean {
        synchronized(this) {
            return super.checkError()
        }
    }

    override fun append(csq: CharSequence?): PrintStream {
        synchronized(this) {
            return super.append(csq)
        }
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): PrintStream {
        synchronized(this) {
            return super.append(csq, start, end)
        }
    }

    override fun append(c: Char): PrintStream {
        synchronized(this) {
            return super.append(c)
        }
    }

    override fun format(format: String?, vararg args: Any?): PrintStream {
        synchronized(this) {
            return super.format(format, *args)
        }
    }

    override fun format(l: Locale?, format: String?, vararg args: Any?): PrintStream {
        synchronized(this) {
            return super.format(l, format, *args)
        }
    }

    override fun clearError() {
        synchronized(this) {
            super.clearError()
        }
    }

    override fun printf(format: String?, vararg args: Any?): PrintStream {
        synchronized(this) {
            return super.printf(format, *args)
        }
    }

    override fun printf(l: Locale?, format: String?, vararg args: Any?): PrintStream {
        synchronized(this) {
            return super.printf(l, format, *args)
        }
    }

    override fun setError() {
        synchronized(this) {
            super.setError()
        }
    }

    override fun close() {
        synchronized(this) {
            super.close()
        }
    }
}

private fun loggingPrintStream(outputStream: OutputStream): PrintStream {
    val printStream = SynchronizedPrintStream(outputStream)
    Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    printStream.close()
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
    )
    return printStream
}

private enum class LoggingLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}

private val LOGGING_PATH = File(File(File(System.getProperty("wk.local.app.dir", System.getProperty("user.dir"))), "Logs"), "debug.log").canonicalPath

private val LOGGING_LEVEL = LoggingLevel.valueOf(System.getProperty("wk.log.level", "INFO").toUpperCase())

private val LOG_TO_SYS_OUT = System.getProperty("wk.log.to.sys.out", "false").toLowerCase().toBoolean()

private val LOGGING_PRINT_STREAM = if (LOG_TO_SYS_OUT) loggingPrintStream(System.out) else loggingPrintStream(File(LOGGING_PATH).outputStream().buffered())

private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss") }

private object InternalLogging {

    fun log(level: LoggingLevel, message: (() -> Any?)? = null, error: Throwable? = null) {
        if (level.ordinal >= LOGGING_LEVEL.ordinal) {
            if (message != null || error != null) {
                val logTime = System.currentTimeMillis()
                async {
                    val messageValue = message?.invoke()
                    when {
                        message != null && error != null -> {
                            val dateString = dateFormat.get().format(Date(logTime))
                            val levelString = level.name
                            val messageString = messageValue.toString()
                            val messageLine = "$dateString $levelString - $messageString"
                            synchronized(LOGGING_PRINT_STREAM) {
                                LOGGING_PRINT_STREAM.println(messageLine)
                                error.printStackTrace(LOGGING_PRINT_STREAM)
                            }
                        }
                        message != null && error == null -> {
                            LOGGING_PRINT_STREAM.println("${dateFormat.get().format(Date(logTime))} ${level.name} - $messageValue")
                        }
                        message == null && error != null -> {
                            val dateString = dateFormat.get().format(Date(logTime))
                            val levelString = level.name
                            val messageString = error.message
                            val messageLine = "$dateString $levelString - $messageString"
                            synchronized(LOGGING_PRINT_STREAM) {
                                LOGGING_PRINT_STREAM.println(messageLine)
                                error.printStackTrace(LOGGING_PRINT_STREAM)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun supplantSystemOut() {
    System.setOut(LOGGING_PRINT_STREAM)
}

fun supplantSystemErr() {
    System.setErr(LOGGING_PRINT_STREAM)
}

fun trace(message: () -> Any?) {
    InternalLogging.log(TRACE, message)
}

fun debug(message: () -> Any?) {
    InternalLogging.log(DEBUG, message)
}

fun info(message: () -> Any?) {
    InternalLogging.log(INFO, message)
}

fun warn(message: () -> Any?) {
    InternalLogging.log(WARN, message)
}

fun error(message: () -> Any?) {
    InternalLogging.log(ERROR, message)
}

fun fatal(message: () -> Any?) {
    InternalLogging.log(FATAL, message)
}

fun trace(message: () -> Any?, error: Throwable?) {
    InternalLogging.log(TRACE, message, error)
}

fun debug(message: () -> Any?, error: Throwable?) {
    InternalLogging.log(DEBUG, message, error)
}

fun info(message: () -> Any?, error: Throwable?) {
    InternalLogging.log(INFO, message, error)
}

fun warn(message: () -> Any?, error: Throwable?) {
    InternalLogging.log(WARN, message, error)
}

fun error(message: () -> Any?, error: Throwable?) {
    InternalLogging.log(ERROR, message, error)
}

fun fatal(message: () -> Any?, error: Throwable?) {
    InternalLogging.log(FATAL, message, error)
}

fun trace(error: Throwable) {
    InternalLogging.log(TRACE, error = error)
}

fun debug(error: Throwable) {
    InternalLogging.log(DEBUG, error = error)
}

fun info(error: Throwable) {
    InternalLogging.log(INFO, error = error)
}

fun warn(error: Throwable) {
    InternalLogging.log(WARN, error = error)
}

fun error(error: Throwable) {
    InternalLogging.log(ERROR, error = error)
}

fun fatal(error: Throwable) {
    InternalLogging.log(FATAL, error = error)
}

interface Log {

    fun trace(message: () -> Any?)

    fun debug(message: () -> Any?)

    fun info(message: () -> Any?)

    fun warn(message: () -> Any?)

    fun error(message: () -> Any?)

    fun fatal(message: () -> Any?)

    fun trace(message: () -> Any?, error: Throwable?)

    fun debug(message: () -> Any?, error: Throwable?)

    fun info(message: () -> Any?, error: Throwable?)

    fun warn(message: () -> Any?, error: Throwable?)

    fun error(message: () -> Any?, error: Throwable?)

    fun fatal(message: () -> Any?, error: Throwable?)

    fun trace(error: Throwable)

    fun debug(error: Throwable)

    fun info(error: Throwable)

    fun warn(error: Throwable)

    fun error(error: Throwable)

    fun fatal(error: Throwable)

    fun trace(message: String) {
        trace({ message })
    }

    fun debug(message: String) {
        debug({ message })
    }

    fun info(message: String) {
        info({ message })
    }

    fun warn(message: String) {
        warn({ message })
    }

    fun error(message: String) {
        error({ message })
    }

    fun fatal(message: String) {
        fatal({ message })
    }

    fun trace(message: String, error: Throwable?) {
        trace({ message }, error)
    }

    fun debug(message: String, error: Throwable?) {
        debug({ message }, error)
    }

    fun info(message: String, error: Throwable?) {
        info({ message }, error)
    }

    fun warn(message: String, error: Throwable?) {
        warn({ message }, error)
    }

    fun error(message: String, error: Throwable?) {
        error({ message }, error)
    }

    fun fatal(message: String, error: Throwable?) {
        fatal({ message }, error)
    }
}

val LOG = object : Log {

    override fun trace(message: () -> Any?) = com.grimfox.logging.trace(message)

    override fun debug(message: () -> Any?) = com.grimfox.logging.debug(message)

    override fun info(message: () -> Any?) = com.grimfox.logging.info(message)

    override fun warn(message: () -> Any?) = com.grimfox.logging.warn(message)

    override fun error(message: () -> Any?) = com.grimfox.logging.error(message)

    override fun fatal(message: () -> Any?) = com.grimfox.logging.fatal(message)

    override fun trace(message: () -> Any?, error: Throwable?) = com.grimfox.logging.trace(message, error)

    override fun debug(message: () -> Any?, error: Throwable?) = com.grimfox.logging.debug(message, error)

    override fun info(message: () -> Any?, error: Throwable?) = com.grimfox.logging.info(message, error)

    override fun warn(message: () -> Any?, error: Throwable?) = com.grimfox.logging.warn(message, error)

    override fun error(message: () -> Any?, error: Throwable?) = com.grimfox.logging.error(message, error)

    override fun fatal(message: () -> Any?, error: Throwable?) = com.grimfox.logging.fatal(message, error)

    override fun trace(error: Throwable) = com.grimfox.logging.trace(error)

    override fun debug(error: Throwable) = com.grimfox.logging.debug(error)

    override fun info(error: Throwable) = com.grimfox.logging.info(error)

    override fun warn(error: Throwable) = com.grimfox.logging.warn(error)

    override fun error(error: Throwable) = com.grimfox.logging.error(error)

    override fun fatal(error: Throwable) = com.grimfox.logging.fatal(error)
}