package com.example

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.util.Log

/**
 * Saves the stack trace of any crash and shows it on the next launch, so a crash can be
 * screenshotted without needing a computer or logcat.
 */
object CrashReporter {
    private const val PREFS = "crash_reporter"
    private const val KEY = "last_crash"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = "Thread: ${thread.name}\n" + Log.getStackTraceString(error)
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun showIfAny(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY, null) ?: return
        prefs.edit().remove(KEY).apply()
        activity.window.decorView.post {
            try {
                AlertDialog.Builder(activity)
                    .setTitle("Last crash (screenshot this)")
                    .setMessage(text.take(3500))
                    .setPositiveButton("OK", null)
                    .show()
            } catch (_: Throwable) {
            }
        }
    }
}