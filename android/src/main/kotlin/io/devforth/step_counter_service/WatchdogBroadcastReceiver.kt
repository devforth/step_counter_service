package io.devforth.step_counter_service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.BackgroundServiceStartNotAllowedException
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat

private const val QUEUE_REQUEST_ID = 0x12412
private const val WATCHDOG_RESPAWN_ACTION: String = "id.devforth/step_counter_service.WATCHDOG_RESPAWN"

class WatchdogBroadcastReceiver : BroadcastReceiver() {
    companion object {
        fun enqueue(context: Context, millis: Int = 600_000) {
            val intent = Intent(
                context,
                WatchdogBroadcastReceiver::class.java
            )
            Log.d("WatchdogBR", "enqueue $millis")
            intent.action = WATCHDOG_RESPAWN_ACTION

            val manager: AlarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

            var flags: Int = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val pIntent: PendingIntent =
                PendingIntent.getBroadcast(context, QUEUE_REQUEST_ID, intent, flags)
            AlarmManagerCompat.setExactAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + millis, pIntent)
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

    @SuppressLint("WakelockTimeout")
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("WatchdogBR", "onReceive")

        if (intent?.action === WATCHDOG_RESPAWN_ACTION) {
            if (StepCounterService.wakeLock == null) {
                StepCounterService.getLock(context).acquire()
            }

            val config = StepCounterService.Config(context)
            val serviceIntent = Intent(context, StepCounterService::class.java)
            if (!config.isManuallyStopped) {
                try {
                    if (config.isForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (e::class.java == ForegroundServiceStartNotAllowedException::class.java) {
                            Log.e("OnBootBR", "Got ForegroundServiceStartNotAllowedException")
                            Log.e("OnBootBR", e.stackTraceToString())
                        } else if (e::class.java == BackgroundServiceStartNotAllowedException::class.java) {
                            Log.e("OnBootBR", "Got BackgroundServiceStartNotAllowedException")
                            Log.e("OnBootBR", e.stackTraceToString())
                        }
                    } else if(e::class.java == java.lang.IllegalStateException::class.java) {
                        Log.e("OnBootBR", "Got IllegalStateException")
                        Log.e("OnBootBR", e.stackTraceToString())
                    } else {
                        throw e
                    }
                }
            }
        }
    }
}
