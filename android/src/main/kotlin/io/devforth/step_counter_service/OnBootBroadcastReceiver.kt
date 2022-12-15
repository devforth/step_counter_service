package io.devforth.step_counter_service

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class OnBootBroadcastReceiver : BroadcastReceiver() {
    @SuppressLint("WakelockTimeout")
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("OnBootBR", "onReceive")

        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED) || intent.action.equals("android.intent.action.QUICKBOOT_POWERON")) {
            val sharedPreferences = context.getSharedPreferences("id.devforth.step_counter_service", Context.MODE_PRIVATE)

            val startOnBoot = sharedPreferences.getBoolean("start_on_boot", true)

            if (startOnBoot) {
                if (StepCounterService.wakeLock == null) {
                    StepCounterService.getLock(context).acquire()
                }

                val serviceIntent = Intent(context, StepCounterService::class.java)

                val config = StepCounterService.Config(context)
                if (config.isForeground) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e::class.java == ForegroundServiceStartNotAllowedException::class.java) {
                            Log.e("OnBootBR", "Got ForegroundServiceStartNotAllowedException")
                            Log.e("OnBootBR", e.stackTraceToString())
                        } else {
                            throw e
                        }
                    }
                }
            }
        }
    }
}
