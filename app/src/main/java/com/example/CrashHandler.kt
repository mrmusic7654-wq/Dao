package com.example

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val log = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val file = File(context.getExternalFilesDir(null), "crash_${timestamp}.log")
        file.writeText(log)
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
