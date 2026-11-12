package com.livetv.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        val store = Store(this)
        if (store.getString("lang", "").isEmpty()) {
            store.putString("lang", "en")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
        Health.load(this)
    }
}

object CrashGuard {
    fun install(app: Application) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val text = sw.toString().take(6000)
                app.getSharedPreferences("livetv", Context.MODE_PRIVATE).edit()
                    .putString("crash_text", text)
                    .putBoolean("crashed", true)
                    .commit()
            } catch (x: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
    }
}
