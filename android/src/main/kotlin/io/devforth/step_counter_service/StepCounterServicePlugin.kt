package io.devforth.step_counter_service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
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

    init {
        serviceConnection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                try {
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

        context = flutterPluginBinding.applicationContext
        handler = Handler(context.mainLooper)

        channel =
            MethodChannel(flutterPluginBinding.binaryMessenger, "id.devforth/step_counter_service", JSONMethodCodec.INSTANCE)
        channel.setMethodCallHandler(this)
    }


    private fun startService() {
        val intent = Intent(context, StepCounterService::class.java)
        intent.putExtra("binder_id", serviceBinderId);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d("SCSPlugin", "onMethodCall")

        when (call.method) {
            "configure" -> {
                val arg = call.arguments as JSONObject

                val preferences = context.getSharedPreferences("id.devforth.step_counter_service", Context.MODE_PRIVATE)
                preferences.edit()
                    .putLong("on_start_handle", arg.getLong("on_start_handle"))
                    .putBoolean("start_on_boot", arg.getBoolean("start_on_boot"))
                    .putString("default_notification_title", arg.getString("default_notification_title"))
                    .putString("default_notification_content", arg.getString("default_notification_content"))
                    .apply()

                if (arg.getBoolean("start_on_boot")) {
                    startService()
                }
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
            "checkSensorsAvailability" -> {
                val hasStepCounterSensor: Boolean =
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)
                result.success(hasStepCounterSensor)

            }
            "isServiceRunning" -> {
                result.success(StepCounterService.isRunning.get())
            }
//            "invoke" -> {
//                if (serviceBinder != null) {
//                    serviceBinder!!.invoke(call.arguments.toString())
//                    result.success(true)
//                } else {
//                    result.success(false)
//                }
//            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d("SCSPlugin", "onDetachedFromEngine")

        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToService(binding: ServicePluginBinding) {
        Log.d("SCSPlugin", "onAttachedToService")
    }

    override fun onDetachedFromService() {
        Log.d("SCSPlugin", "onDetachedFromService")
    }
}
