package io.devforth.step_counter_service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.os.Build.VERSION.SDK_INT
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint
import io.flutter.plugin.common.JSONMethodCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean


@OptIn(FlowPreview::class)
class StepCounterService : Service(), SensorEventListener, MethodChannel.MethodCallHandler {
    companion object {
        val isRunning: AtomicBoolean = AtomicBoolean(false)

        @Volatile
        var wakeLock: WakeLock? = null

        @Synchronized
        fun getLock(context: Context): WakeLock {
            if (wakeLock == null) {
                val powerManger = context.getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManger.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    (StepCounterService::class.java.name + ".Lock")
                )
                wakeLock!!.setReferenceCounted(true)
            }
            return wakeLock!!
        }

        private val WATCHDOG_REQUEST_CODE = 0x12412
        fun enqueueWatchdog(context: Context) {
            val intent = Intent(
                context,
                WatchdogBroadcastReceiver::class.java
            )
            val manager = context.getSystemService(ALARM_SERVICE) as AlarmManager

            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val pIntent = PendingIntent.getBroadcast(context, WATCHDOG_REQUEST_CODE, intent, flags)

            // Check is background service every 5 seconds
            AlarmManagerCompat.setAndAllowWhileIdle(
                manager,
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5000,
                pIntent
            )
        }

        fun isManuallyStopped(context: Context): Boolean {
            val sharedPreferences =
                context.getSharedPreferences("id.devforth.step_counter_service", MODE_PRIVATE)
            return sharedPreferences.getBoolean("is_manually_stopped", false)
        }

        fun setManuallyStopped(context: Context, value: Boolean) {
            val sharedPreferences =
                context.getSharedPreferences("id.devforth.step_counter_service", MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("is_manually_stopped", value).apply()
        }

        fun isForeground(context: Context): Boolean {
            val sharedPreferences =
                context.getSharedPreferences("id.devforth.step_counter_service", MODE_PRIVATE)
            return sharedPreferences.getBoolean("foreground", false)
        }

        fun setForeground(context: Context, value: Boolean) {
            val sharedPreferences =
                context.getSharedPreferences("id.devforth.step_counter_service", MODE_PRIVATE)
            return sharedPreferences.edit().putBoolean("foreground", value).apply()
        }

        @Suppress("DEPRECATION")
        fun isServiceRunning(context: Context): Boolean {
            return (context.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .getRunningServices(Integer.MAX_VALUE)
                .any { it -> it.service.className == StepCounterService::class.java.name }
        }
    }

    private lateinit var handler: Handler

    private val FOREGROUND_ID = 0x41241
    private val NOTIFICATION_CHANNEL_ID = "id.devforth/STEP_COUNTER_SERVICE_NOTIFICATION_ID"

    private val BATCHING_0S_LATENCY = 0
    private val BATCHING_5S_LATENCY = 5000000

    private lateinit var notificationManager: NotificationManager
    private lateinit var sensorManager: SensorManager

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var stepCounterSensor: Sensor

    private var flutterEngine: FlutterEngine? = null
    private var methodChannel: MethodChannel? = null
    private var dartEntrypoint: DartEntrypoint? = null

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onCreate() {
        super.onCreate();

        Log.d("StepCounterService", "onCreate")

        handler = Handler(Looper.getMainLooper())

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        createNotification()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        sensorManager.registerListener(
            this,
            stepCounterSensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            BATCHING_5S_LATENCY
        )

        startDartEntrypoint()
    }

    @SuppressLint("WakelockTimeout")
    fun startDartEntrypoint() {
        if (isRunning.get() && flutterEngine?.dartExecutor?.isExecutingDart == true) {
            return
        }

        getLock(applicationContext).acquire()

        val flutterLoader = FlutterInjector.instance().flutterLoader()
        if (!flutterLoader.initialized()) {
            flutterLoader.startInitialization(applicationContext)
        }

        flutterLoader.ensureInitializationComplete(applicationContext, null)
        isRunning.set(true);

        flutterEngine = FlutterEngine(this)
        flutterEngine!!.serviceControlSurface.attachToService(this@StepCounterService, null, true)

        methodChannel = MethodChannel(
            flutterEngine!!.dartExecutor.binaryMessenger,
            "id.devforth/step_counter_service_android_bg",
            JSONMethodCodec.INSTANCE
        )
        methodChannel!!.setMethodCallHandler(this)

        dartEntrypoint = DartEntrypoint(
            flutterLoader.findAppBundlePath(),
            "package:step_counter_service/step_counter_service_android.dart",
            "entrypoint"
        )
        flutterEngine!!.dartExecutor.executeDartEntrypoint(dartEntrypoint!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("StepCounterService", "onStartCommand")

        setManuallyStopped(this, false)
        enqueueWatchdog(this)
        startForeground(FOREGROUND_ID, notificationBuilder.build())

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("StepCounterService", "onDestroy")

        if (!isManuallyStopped(this)) {
            enqueueWatchdog(this)
        }

        scope.cancel()

        notificationManager.cancel(FOREGROUND_ID)
        sensorManager.unregisterListener(this)
        stopForeground(true)


        if (flutterEngine != null) {
            flutterEngine!!.serviceControlSurface.detachFromService()
            flutterEngine!!.destroy()
            flutterEngine = null
        }

        methodChannel = null
        dartEntrypoint = null

        isRunning.set(false)

        super.onDestroy()
    }

    val listeners: HashMap<Int, IStepCounterService> = hashMapOf()
    private val binder: IStepCounterServiceBinder.Stub = object : IStepCounterServiceBinder.Stub() {
        override fun bind(id: Int, service: IStepCounterService?) {
            synchronized(listeners) {
                listeners[id] = service!!
            }
            lastStepsValue?.let { service!!.invoke(updateStepsMessage(it).toString()) }
        }

        override fun unbind(id: Int) {
            synchronized(listeners) {
                if (listeners.containsKey(id)) {
                    listeners.remove(id)
                }
            }
        }

        override fun invoke(data: String) {
            if (methodChannel != null) {
                try {
                    handler.post {
                        methodChannel!!.invokeMethod(
                            "onMessage",
                            JSONObject(data)
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun invokeInternal(method: String, data: String?) {
            this@StepCounterService.onMethodCall(MethodCall(method, data?.let { JSONObject(it) }), object : MethodChannel.Result {
                override fun success(result: Any?) {}
                override fun notImplemented() {}
                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {}
            })
        }
    }

    override fun onBind(intent: Intent): IBinder {
        lastStepsValue?.let { binder.invoke(updateStepsMessage(it).toString()) }
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        val binderId = intent.getIntExtra("binder_id", 0);
        if (binderId != 0) {
            synchronized(listeners) {
                listeners.remove(binderId);
            }
        }

        return super.onUnbind(intent)
    }

    override fun onAccuracyChanged(s: Sensor?, p1: Int) {
        Log.i("StepCounterService", "onAccuracyChanged $p1")
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val updateStepsFlow = MutableStateFlow<Int?>(null)
    private var lastStepsValue: Int? = null;

    private fun updateStepsMessage(steps: Int): JSONObject {
        return JSONObject(mapOf("method" to "updateSteps", "args" to mapOf("steps" to steps)))
    }

    init {
        scope.launch {
            updateStepsFlow.debounce(1000).collect {
                if (it == null) return@collect

                Log.d("StepCounterService", "GOT IT $it")

                val data = updateStepsMessage(it).toString()

                lastStepsValue = it;

                this@StepCounterService.invoke(data)
                this@StepCounterService.binder.invoke(data)
            }
        }
    }

    override fun onSensorChanged(se: SensorEvent?) {
        if (se?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val steps: Int? = se.values?.get(0)?.toInt()
            if (steps != null) {
                Log.i("StepCounterService", "onSensorChanged $steps")

                updateStepsFlow.value = steps
            }
        }
    }

    private fun createNotificationChannel() {
        if (SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Step Counter Service"
            val description = "Counting steps in background"

            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
            channel.description = description

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)

        var flags = PendingIntent.FLAG_CANCEL_CURRENT
        if (SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }

        val contentIntent = PendingIntent.getActivity(this@StepCounterService, 11, intent, flags)

        val sharedPreferences =
            getSharedPreferences("id.devforth.step_counter_service", Context.MODE_PRIVATE)
        val notificationTitle =
            sharedPreferences.getString("default_notification_title", "Step Counter Service");
        val notificationContent =
            sharedPreferences.getString("default_notification_content", "Preparing...");

        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bg_service_small)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
    }

    private fun updateNotification(title: String, content: String) {
        notificationBuilder
            .setContentTitle(title)
            .setContentText(content)
        notificationManager.notify(FOREGROUND_ID, notificationBuilder.build())
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "updateNotification" -> {
                val arg = call.arguments as JSONObject
                val title = arg.getString("title")
                val content = arg.getString("content")
                updateNotification(title, content)
                result.success(true)
            }
            "getHandle" -> {
                val sharedPreferences =
                    getSharedPreferences("id.devforth.step_counter_service", MODE_PRIVATE)
                val handle: Long = sharedPreferences.getLong("on_start_handle", 0)
                result.success(handle)
            }
            "setForeground" -> {
                val arg = call.arguments as JSONObject
                val value = arg.getBoolean("value")

                val currentValue = isForeground(this)
                setForeground(this, value)

                if (currentValue != value) {
                    if (value) {
                        startForeground(FOREGROUND_ID, notificationBuilder.build())
                        flutterEngine?.serviceControlSurface?.onMoveToForeground()
                    } else {
                        stopForeground(true)
                        flutterEngine?.serviceControlSurface?.onMoveToBackground()
                    }
                }

            }
            "stop" -> {
                setManuallyStopped(this, true)
                val intent = Intent(this, WatchdogBroadcastReceiver::class.java)

                var flags = PendingIntent.FLAG_CANCEL_CURRENT
                if (SDK_INT >= Build.VERSION_CODES.S) {
                    flags = flags or PendingIntent.FLAG_MUTABLE
                }

                val pi =
                    PendingIntent.getBroadcast(applicationContext, WATCHDOG_REQUEST_CODE, intent, flags)
                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pi)

                try {
                    synchronized(listeners) {
                        for (key in listeners.keys) {
                            listeners[key]?.stop()
                        }
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }

                stopSelf()
                result.success(true)
            }
            "invoke" -> {
                try {
                    invoke(call.arguments.toString())
                    result.success(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun invoke(data: String) {
        synchronized(listeners) {
            listeners.values.forEach {
                it.invoke(data)
            }
        }
    }
}