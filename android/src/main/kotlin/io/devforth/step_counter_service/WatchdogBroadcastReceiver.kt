package io.devforth.step_counter_service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat


class WatchdogBroadcastReceiver : BroadcastReceiver() {
    companion object {
        val QUEUE_REQUEST_ID = 0x12412
        val WATCHDOG_RESPAWN_ACTION: String = "id.devforth/step_counter_service.WATCHDOG_RESPAWN"

        fun enqueue(context: Context, millis: Int = 5000) {
            val intent = Intent(
                context,
                WatchdogBroadcastReceiver::class.java
            )
            intent.action = WATCHDOG_RESPAWN_ACTION

            val manager: AlarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

            var flags: Int = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val pIntent: PendingIntent =
                PendingIntent.getBroadcast(context, QUEUE_REQUEST_ID, intent, flags)

            AlarmManagerCompat.setAndAllowWhileIdle(
                manager,
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + millis,
                pIntent
            )
        }

        fun cancel(context: Context) {
            val intent = Intent(
                context,
                WatchdogBroadcastReceiver::class.java
            )
            intent.action = WATCHDOG_RESPAWN_ACTION

            val manager: AlarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

            var flags: Int = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val pIntent: PendingIntent =
                PendingIntent.getBroadcast(context, QUEUE_REQUEST_ID, intent, flags)

            manager.cancel(pIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("WatchdogBR", "onReceive")

        if (intent?.action === WATCHDOG_RESPAWN_ACTION) {
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
}
