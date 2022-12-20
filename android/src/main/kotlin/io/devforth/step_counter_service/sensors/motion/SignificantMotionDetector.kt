package io.devforth.step_counter_service.sensors.motion

import android.content.Context
import android.hardware.*
import io.devforth.step_counter_service.sensors.motion.MotionDetector


class SignificantMotionDetector(context: Context): MotionDetector(Sensor.TYPE_SIGNIFICANT_MOTION, context) {
    override fun isMotionSignificant(event: SensorEvent): Boolean {
        return true;
    }
}