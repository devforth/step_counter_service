package io.devforth.step_counter_service.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.os.Build
import androidx.annotation.RequiresApi

class StepCounterSensor(context: Context): StepCountingSensor(Sensor.TYPE_STEP_COUNTER, context) {
    override fun updateStepCount(event: SensorEvent): Int {
        return event.values[0].toInt()
    }
}