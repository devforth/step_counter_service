package io.devforth.step_counter_service

import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.service.ServiceAware
import io.flutter.embedding.engine.plugins.service.ServicePluginBinding
import io.flutter.plugin.common.JSONMethodCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import org.json.JSONObject


class StepCounterServicePlugin : FlutterPlugin, MethodCallHandler, ServiceAware {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    private lateinit var handler: Handler

    private val serviceBinderId = (System.currentTimeMillis() / 1000).toInt()
    private var serviceBinder: IStepCounterServiceBinder? = null
    private lateinit var serviceConnection: ServiceConnection

    private var shouldUnbind: Boolean = false

    init {
        serviceConnection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                try {
                    shouldUnbind = false
                    serviceBinder!!.unbind(serviceBinderId)
                    serviceBinder = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceBinder = IStepCounterServiceBinder.Stub.asInterface(binder)

                try {
                    val listener: IStepCounterService = object : IStepCounterService.Stub() {
                        override fun invoke(data: String) {
                            try {
                                handler.post {
                                    channel.invokeMethod("onMessage", JSONObject(data))
                                }
                            } catch (e: java.lang.Exception) {
                                e.printStackTrace()
                            }
                        }

                        override fun stop() {
                            context.unbindService(serviceConnection)
                        }
                    }

                    serviceBinder!!.bind(serviceBinderId, listener)
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.d("SCSPlugin", "onAttachedToEngine")

        shouldUnbind = false;

        context = flutterPluginBinding.applicationContext
        handler = Handler(context.mainLooper)

        channel =
            MethodChannel(
                flutterPluginBinding.binaryMessenger,
                "id.devforth/step_counter_service",
                JSONMethodCodec.INSTANCE
            )
        channel.setMethodCallHandler(this)
    }


    private fun startService() {
        val intent = Intent(context, StepCounterService::class.java)
        intent.putExtra("binder_id", serviceBinderId);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && StepCounterService.Config(context).isForeground) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        shouldUnbind = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        WatchdogBroadcastReceiver.enqueue(context)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d("SCSPlugin", "onMethodCall ${call.method}")

        when (call.method) {
            "configure" -> {
                val arg = call.arguments as JSONObject

                val preferences = context.getSharedPreferences(
                    "id.devforth.step_counter_service",
                    Context.MODE_PRIVATE
                )
                preferences.edit()
                    .putLong("on_start_handle", arg.getLong("on_start_handle"))
                    .putBoolean("start_on_boot", arg.getBoolean("start_on_boot"))
                    .putBoolean("foreground", arg.getBoolean("foreground"))
                    .putString(
                        "default_notification_title",
                        arg.getString("default_notification_title")
                    )
                    .putString(
                        "default_notification_content",
                        arg.getString("default_notification_content")
                    )
                    .apply()

                result.success(null)
            }
            "startService" -> {
                try {
                    startService()
                    result.success(null)
                } catch (e: java.lang.Exception) {
                    result.error("start_service_error", e.message, null)
                }
            }
            "isServiceRunning" -> {
                result.success(StepCounterService.isServiceRunning(context))
            }
            "invoke" -> {
                if (serviceBinder != null) {
                    serviceBinder!!.invoke(call.arguments.toString())
                    result.success(true)
                } else {
                    result.success(false)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d("SCSPlugin", "onDetachedFromEngine")
        if (shouldUnbind && serviceBinder != null) {
            binding.applicationContext.unbindService(serviceConnection)
            shouldUnbind = false
        }
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToService(binding: ServicePluginBinding) {
        Log.d("SCSPlugin", "onAttachedToService")
    }

    override fun onDetachedFromService() {
        Log.d("SCSPlugin", "onDetachedFromService")
    }
}
