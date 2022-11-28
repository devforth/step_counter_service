package io.devforth.step_counter_service.sensors

import android.content.Context
import android.hardware.*
import android.util.Log
import androidx.core.content.ContextCompat

abstract class ListenableSensor<T>(
    context: Context,
    sensorType: Int,
    private val oneShot: Boolean = false
) : TriggerEventListener(), SensorEventListener {
    private val sensorManager: SensorManager = ContextCompat.getSystemService(
        context,
        SensorManager::class.java
    )!!
    private val sensor: Sensor = sensorManager.getDefaultSensor(sensorType)

    private val mutableListeners: MutableList<T> = mutableListOf()
    protected val listeners: List<T>
        get() {
            return mutableListeners.toList()
        }

    fun registerListener(listener: T) {
        mutableListeners.add(listener)
    }

    fun unregisterListener(listener: T) {
        mutableListeners.remove(listener)
    }

    open fun start() {
        if (oneShot) {
            sensorManager.requestTriggerSensor(this, sensor)
        } else {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        if (oneShot) {
            sensorManager.cancelTriggerSensor(this, sensor)
        } else {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onTrigger(event: TriggerEvent?) {
        TODO("Not yet implemented")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        TODO("Not yet implemented")
    }

    override fun onAccuracyChanged(sensor: Sensor?, acc: Int) {
        Log.d("ListenableSensor", "onAccuracyChanged ${sensor?.type} $acc")
    }

}