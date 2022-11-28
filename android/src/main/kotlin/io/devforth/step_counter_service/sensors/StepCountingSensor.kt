package io.devforth.step_counter_service.sensors

import android.content.Context
import android.hardware.SensorEvent
import android.hardware.SensorManager

interface StepCountingSensorListener {
    fun onStepCountChanged(stepCount: Int)
}

abstract class StepCountingSensor(
    sensorType: Int,
    context: Context,
) : ListenableSensor<StepCountingSensorListener>(context, sensorType) {
    private var previousStepCount: Int = 0
    abstract fun updateStepCount(event: SensorEvent): Int

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val stepCount = updateStepCount(event)
            if (stepCount != previousStepCount) {
                previousStepCount = stepCount
                this.listeners.forEach { it.onStepCountChanged(stepCount) }
            }
        }
    }
}