package io.devforth.step_counter_service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.os.Build.VERSION.SDK_INT
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.NotificationCompat
import io.devforth.step_counter_service.sensors.*
import io.devforth.step_counter_service.sensors.motion.MotionDetector
import io.devforth.step_counter_service.sensors.motion.MotionDetectorLister
import io.devforth.step_counter_service.sensors.step.AccelerometerStepCounter
import io.devforth.step_counter_service.sensors.step.LinearAccelerationStepDetector
import io.devforth.step_counter_service.sensors.step.StepCounter
import io.devforth.step_counter_service.sensors.step.StepCountingSensorListener
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint
import io.flutter.plugin.common.JSONMethodCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean

private const val FOREGROUND_ID = 0x41241
private const val NOTIFICATION_CHANNEL_ID = "id.devforth/STEP_COUNTER_SERVICE_NOTIFICATION_ID"

class StepCounterService : Service(), StepCountingSensorListener, MotionDetectorLister,
    MethodChannel.MethodCallHandler {
    companion object {
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

        @Suppress("DEPRECATION")
        fun isServiceRunning(context: Context): Boolean {
            return (context.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == StepCounterService::class.java.name }
        }
    }

    class Config(private val context: Context) {
        private val sharedPreferences: SharedPreferences
            get() {
                return context.getSharedPreferences(
                    "id.devforth.step_counter_service",
                    MODE_PRIVATE
                )
            }

        var isManuallyStopped: Boolean
            get() {
                return sharedPreferences.getBoolean("is_manually_stopped", false)
            }
            set(value) {
                sharedPreferences.edit().putBoolean("is_manually_stopped", value).apply()
            }

        var isForeground: Boolean
            get() {
                return sharedPreferences.getBoolean("foreground", false)
            }
            set(value) {
                sharedPreferences.edit().putBoolean("foreground", value).apply()
            }
    }

    private val isRunning: AtomicBoolean = AtomicBoolean(false)

    private lateinit var handler: Handler

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private var stepCounter: StepCounter? = null
    private var motionDetector: MotionDetector? = null

    private var flutterEngine: FlutterEngine? = null
    private var methodChannel: MethodChannel? = null
    private var dartEntrypoint: DartEntrypoint? = null

    private var currentStepCount: Int? = null

    private var noMotionTimer: Timer? = null
    private var syncStepsTimer: Timer? = null
    private var pingTimer: Timer? = null

    private fun noMotionTimerTask(): TimerTask {
        return object : TimerTask() {
            override fun run() {
                Log.d("StepCounterService", "NoMotion TimerTask")
                handler.post {
                    stepCounter?.stop()
                    motionDetector?.start()

                    this@StepCounterService.syncStepsTimer?.cancel()

                    wakeLock?.release()
                    wakeLock = null
                }
            }
        }
    }

    private fun rescheduleNoMotionTimer() {
        // If motion detector is not present, we do not try to optimize use of the wake lock, since it's very unreliable or has no impact on battery usage to use something else
        if (this.motionDetector == null) return

        this.noMotionTimer?.cancel()
        this.noMotionTimer = Timer("NoMotionTimer", false)
        this.noMotionTimer?.schedule(noMotionTimerTask(), 5 * 60 * 1000)
    }

    private fun syncStepsTimerTask(): TimerTask {
        return object : TimerTask() {
            private var lastSentStepCount: Int? = null

            override fun run() {
                handler.post {
                    if (currentStepCount == null) return@post

                    Log.d("StepCounterService", "SyncSteps TimerTask: Pre Check $lastSentStepCount $currentStepCount")
                    if (lastSentStepCount != currentStepCount) {
                        Log.d("StepCounterService", "SyncSteps TimerTask: $currentStepCount")
                        lastSentStepCount = currentStepCount
                        rescheduleNoMotionTimer()

                        val data = updateStepsMessage(currentStepCount!!).toString()

                        this@StepCounterService.invoke(data)
                    }
                }
            }
        }
    }

    private fun rescheduleSyncStepsTimer() {
        this.syncStepsTimer?.cancel()
        this.syncStepsTimer = Timer("SyncStepsTimer", false)
        this.syncStepsTimer?.scheduleAtFixedRate(syncStepsTimerTask(), 1000, 10 * 1000)
    }

    private fun pingTimerTask(): TimerTask {
        return object : TimerTask() {
            override fun run() {
                handler.post {
                    Log.d("StepCounterService", "SyncSteps TimerTask: $currentStepCount")

                    val data = JSONObject(mapOf("method" to "ping", "args" to mapOf<String, String>())).toString()

                    this@StepCounterService.invoke(data)
                }
            }
        }
    }

    private fun reschedulePingTimer() {
        this.pingTimer?.cancel()
        this.pingTimer = Timer("PingTimer", false)
        this.pingTimer?.scheduleAtFixedRate(pingTimerTask(), 0, 5 * 1000)
    }


    override fun onCreate() {
        super.onCreate()
        Log.d("StepCounterService", "onCreate")

        handler = Handler(Looper.getMainLooper())

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        createNotification()

        stepCounter = StepCounter.getBest(this)
        motionDetector = MotionDetector.getBest(this)

        stepCounter?.registerListener(this)
        motionDetector?.registerListener(this)

        stepCounter?.start()

        rescheduleNoMotionTimer()
        rescheduleSyncStepsTimer()
        reschedulePingTimer()
    }

    @SuppressLint("WakelockTimeout")
    private fun startDartEntrypoint() {
        if (isRunning.get() || (flutterEngine != null && !flutterEngine!!.dartExecutor.isExecutingDart)) {
            return
        }

        if (wakeLock == null) {
            getLock(this).acquire()
        }

        val flutterLoader = FlutterInjector.instance().flutterLoader()
        if (!flutterLoader.initialized()) {
            flutterLoader.startInitialization(this)
        }

        flutterLoader.ensureInitializationComplete(this, null)
        isRunning.set(true)

        flutterEngine = FlutterEngine(this)
        flutterEngine!!.serviceControlSurface.attachToService(
            this@StepCounterService,
            null,
            Config(this).isForeground
        )

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
        Log.d("StepCounterService", "onStartCommand")

        val config = Config(this)
        config.isManuallyStopped = false

        // Step counting is not supported, no point in starting watchdog\flutter part of the service
        if (this.stepCounter == null) {
            if (config.isForeground) {
                startForeground(FOREGROUND_ID, notificationBuilder.setContentText("Failed to initialize step counting service").build())
            }

            Timer().schedule(object : TimerTask() {
                override fun run() {
                    handler.post {
                        this@StepCounterService.stopForeground(true)
                        this@StepCounterService.stopSelf()
                    }
                }
            }, 5000)

            return START_STICKY
        }

        WatchdogBroadcastReceiver.enqueue(this)

        if (config.isForeground) {
            startForeground(FOREGROUND_ID, notificationBuilder.build())
        }
        startDartEntrypoint()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("StepCounterService", "onDestroy")

        if (!Config(this).isManuallyStopped) {
            WatchdogBroadcastReceiver.enqueue(this, 1000)
        }

        this.noMotionTimer?.cancel()
        this.syncStepsTimer?.cancel()

        notificationManager.cancel(FOREGROUND_ID)
        stopForeground(true)

        stepCounter?.stop()
        motionDetector?.stop()

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

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isRunning.get()) {
            WatchdogBroadcastReceiver.enqueue(applicationContext, 1000)
        }
    }

    val listeners: HashMap<Int, IStepCounterService> = hashMapOf()
    private val binder: IStepCounterServiceBinder.Stub =
        object : IStepCounterServiceBinder.Stub() {
            override fun bind(id: Int, service: IStepCounterService?) {
                synchronized(listeners) {
                    listeners[id] = service!!
                }
                currentStepCount?.let { service!!.invoke(updateStepsMessage(it).toString()) }
                service!!.invoke(serviceStatusMessage().toString())
            }

            override fun unbind(id: Int) {
                synchronized(listeners) {
                    listeners.remove(id)
                }
            }

            override fun invoke(data: String) {
                if (methodChannel != null) {
                    try {
                        handler.post {
                            methodChannel?.invokeMethod(
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
                Log.d("Binder", "invokeInternal")
                this@StepCounterService.onMethodCall(
                    MethodCall(method, data?.let { JSONObject(it) }),
                    object : MethodChannel.Result {
                        override fun success(result: Any?) {}
                        override fun notImplemented() {}
                        override fun error(
                            errorCode: String,
                            errorMessage: String?,
                            errorDetails: Any?
                        ) {}
                    }
                )
            }
        }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        val binderId = intent.getIntExtra("binder_id", 0)
        if (binderId != 0) {
            synchronized(listeners) {
                listeners.remove(binderId)
            }
        }

        return super.onUnbind(intent)
    }


    private fun updateStepsMessage(steps: Int): JSONObject {
        return JSONObject(mapOf("method" to "updateSteps", "args" to JSONObject(mapOf("steps" to steps))))
    }

    private fun serviceStatusMessage(): JSONObject {
        return JSONObject(
            mapOf(
                "method" to "serviceStatus",
                "args" to JSONObject(mapOf(
                    "stepCounter" to if(this.stepCounter != null) this.stepCounter!!::class.java.simpleName else null,
                    "motionDetector" to if(this.motionDetector != null) this.motionDetector!!::class.java.simpleName else null,
                ))
            )
        )
    }

    override fun onStepCountChanged(stepCount: Int) {
        Log.d("StepCounterService", "onStepTaken $stepCount")
        currentStepCount = stepCount
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

        val contentIntent =
            PendingIntent.getActivity(this@StepCounterService, 11, intent, flags)

        val sharedPreferences =
            getSharedPreferences("id.devforth.step_counter_service", Context.MODE_PRIVATE)
        val notificationTitle =
            sharedPreferences.getString("default_notification_title", "Step Counter Service")
        val notificationContent =
            sharedPreferences.getString("default_notification_content", "Preparing...")

        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_bg_service_small)
            setContentTitle(notificationTitle)
            setContentText(notificationContent)
            setOngoing(true)
            setSilent(true)
            setContentIntent(contentIntent)
        }
    }

    private fun updateNotification(title: String, content: String) {
        notificationBuilder.apply {
            setContentTitle(title)
            setContentText(content)
        }

        if (Config(this).isForeground) {
//            notificationManager.notify(FOREGROUND_ID, notificationBuilder.build())
            startForeground(FOREGROUND_ID, notificationBuilder.build())
        }
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

                val currentValue = Config(this).isForeground
                Config(this).isForeground = value

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
                Config(this).isManuallyStopped = true
                WatchdogBroadcastReceiver.cancel(this)

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
            "rescheduleTimers" -> {
                try {
                    rescheduleNoMotionTimer()
                    rescheduleSyncStepsTimer()
                    reschedulePingTimer()

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

    @SuppressLint("WakelockTimeout")
    override fun onMotion() {
        Log.d("StepCounterService", "OnMotionDetected")
        if (wakeLock == null) {
            getLock(this).acquire()
        }

        // Restart timer since user is moving
        rescheduleNoMotionTimer()
        rescheduleSyncStepsTimer()
        stepCounter?.start()
    }
}