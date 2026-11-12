package com.livetv.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

// Checks the newest GitHub release and installs it in place (same signing key required).
object Updater {
    fun check(act: AppCompatActivity, manual: Boolean) {
        Thread {
            var number = 0
            var apk = ""
            var ok = false
            try {
                val txt = Store(act).download(
                    "https://api.github.com/repos/${Config.REPO_OWNER}/${Config.REPO_NAME}/releases/latest"
                )
                if (txt != null) {
                    val o = JSONObject(txt)
                    number = o.optString("tag_name").substringAfterLast('-').toIntOrNull() ?: 0
                    val assets = o.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name").endsWith(".apk")) {
                                apk = a.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    ok = true
                }
            } catch (e: Exception) {
            }
            act.runOnUiThread {
                if (act.isFinishing) return@runOnUiThread
                if (!ok) {
                    if (manual) toast(act, R.string.upd_failed)
                } else if (number > BuildConfig.VERSION_CODE && apk.isNotEmpty()) {
                    AlertDialog.Builder(act)
                        .setTitle(R.string.upd_title)
                        .setMessage(R.string.upd_msg)
                        .setPositiveButton(R.string.upd_download) { _, _ ->
                            toast(act, R.string.upd_downloading)
                            download(act.applicationContext, apk)
                        }
                        .setNegativeButton(R.string.upd_later, null)
                        .show()
                } else if (manual) {
                    toast(act, R.string.upd_latest)
                }
            }
        }.start()
    }

    private fun toast(ctx: Context, res: Int) {
        android.widget.Toast.makeText(ctx, res, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun download(app: Context, url: String) {
        try {
            val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val f = File(dir, "LiveTV-update.apk")
            if (f.exists()) f.delete()
            val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("Live TV")
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "LiveTV-update.apk")
            val id = dm.enqueue(req)
            val rcv = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == id) {
                        try {
                            app.unregisterReceiver(this)
                        } catch (e: Exception) {
                        }
                        install(app, f)
                    }
                }
            }
            ContextCompat.registerReceiver(
                app, rcv, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            toast(app, R.string.upd_failed)
        }
    }

    private fun install(ctx: Context, f: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            toast(ctx, R.string.upd_failed)
        }
    }
}
