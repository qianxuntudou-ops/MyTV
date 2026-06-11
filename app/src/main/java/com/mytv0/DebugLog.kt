package com.mytv0

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private const val FILE_NAME = "mytv-debug.log"
    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("I", tag, message, null)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
        write("W", tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        write("E", tag, message, error)
    }

    fun clear() {
        runCatching {
            File(MyTVApplication.getInstance().filesDir, FILE_NAME).writeText("")
        }
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        runCatching {
            val file = File(MyTVApplication.getInstance().filesDir, FILE_NAME)
            val line = buildString {
                append(formatter.format(Date()))
                append(' ')
                append(level)
                append('/')
                append(tag)
                append(": ")
                append(message)
                append('\n')
                if (error != null) {
                    append(Log.getStackTraceString(error))
                    append('\n')
                }
            }
            file.appendText(line)
        }
    }
}
