package io.devforth.step_counter_service.sensors

import android.content.Context
import android.hardware.*


interface SignificantMotionSensorListener {
    fun onSignificantMotion()
}

class SignificantMotionSensor constructor(
    context: Context,
): ListenableSensor<SignificantMotionSensorListener>(context, Sensor.TYPE_SIGNIFICANT_MOTION, true) {
    override fun onTrigger(event: TriggerEvent?) {
        this.listeners.forEach { it.onSignificantMotion() }
    }
}