package com.livetv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sp = context.getSharedPreferences("livetv", Context.MODE_PRIVATE)
            if (sp.getBoolean("boot", false)) {
                val i = Intent(context, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(i)
                } catch (e: Exception) {
                }
            }
        }
    }
}
