package io.devforth.step_counter_service.sensors.step

import android.content.Context
import android.hardware.SensorEvent
import android.os.Build
import io.devforth.step_counter_service.sensors.ListenableSensor

private val SUPPORTED_SENSORS =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        listOf(SensorStepCounter::class.java, AccelerometerStepDetector::class.java)
    } else {
        listOf(AccelerometerStepDetector::class.java)
    }

interface StepCountingSensorListener {
    fun onStepCountChanged(stepCount: Int)
}

abstract class StepCounter(
    sensorType: Int,
    context: Context,
) : ListenableSensor<StepCountingSensorListener>(context, sensorType) {
    private var previousStepCount: Int = 0
    abstract fun updateStepCount(event: SensorEvent): Int

    override fun onSensorChanged(event: SensorEvent) {
        val stepCount = updateStepCount(event)
        if (stepCount != previousStepCount) {
            previousStepCount = stepCount
            this.listeners.forEach { it.onStepCountChanged(stepCount) }
        }
    }

    companion object {
        fun getBest(context: Context, desired: Class<out StepCounter>? = null): StepCounter? {
            if (desired != null) {
                return desired.constructors[0].newInstance(context) as StepCounter
            }

            for (sensor in SUPPORTED_SENSORS) {
                try {
                    return sensor.constructors[0].newInstance(context) as StepCounter
                } catch (_: Exception) {
                }
            }

            return null
//            throw Exception("Not supported sensor available on this device")
        }
    }
}