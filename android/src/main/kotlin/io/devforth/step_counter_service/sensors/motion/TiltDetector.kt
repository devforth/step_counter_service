package io.devforth.step_counter_service.sensors.motion

import android.content.Context
import android.hardware.*
import io.devforth.step_counter_service.sensors.motion.MotionDetector


class TiltDetector(context: Context): MotionDetector(22, context) {
    override fun isMotionSignificant(event: SensorEvent): Boolean {
        return true;
    }
}