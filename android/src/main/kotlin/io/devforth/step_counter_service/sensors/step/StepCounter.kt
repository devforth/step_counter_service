package io.devforth.step_counter_service.sensors.step

import android.content.Context
import android.hardware.SensorEvent
import android.os.Build
import io.devforth.step_counter_service.BuildConfig
import io.devforth.step_counter_service.sensors.ListenableSensor

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
            if (BuildConfig.DEBUG && desired != null) {
                return desired.constructors[0].newInstance(context) as StepCounter
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try { return SensorStepCounter(context) } catch (_: Exception) {}
            }

            try { return AccelerometerStepDetector(context) } catch (_: Exception) {}

            return null
        }
    }
}