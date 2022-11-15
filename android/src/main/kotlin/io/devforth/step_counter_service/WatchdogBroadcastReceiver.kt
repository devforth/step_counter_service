package io.devforth.step_counter_service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class WatchdogBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("WatchdogBR", "onReceive")

        val serviceIntent = Intent(context, StepCounterService::class.java)

        if (!StepCounterService.isManuallyStopped(context)) {
            if (StepCounterService.isForeground(context)) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
