package com.almoullim.background_location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.util.prefs.Preferences


class BootBroadcastReceiver : BroadcastReceiver() {
    private val TAG = BootBroadcastReceiver::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "BOOT detected: ${intent.action}")
        val serviceIntent = Intent(context, LocationUpdatesService::class.java)
        serviceIntent.action = LocationUpdatesService.ACTION_ON_BOOT
        val pref = context.getSharedPreferences("backgroundLocationPreferences", Context.MODE_PRIVATE)
        Log.i(TAG, "BOOT Post Execute: ${pref.getBoolean("locationActive", false)}")
        if (pref.getBoolean("locationActive", false)) {
            try {
                val plugin = BackgroundLocationPlugin()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (tr: Throwable) {
                Log.w(TAG, "Error starting service", tr)
            }
        }
    }
}