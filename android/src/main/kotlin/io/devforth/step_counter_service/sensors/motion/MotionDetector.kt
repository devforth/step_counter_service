package io.devforth.step_counter_service.sensors.motion

import android.content.Context
import android.hardware.SensorEvent
import android.hardware.TriggerEvent
import io.devforth.step_counter_service.BuildConfig
import io.devforth.step_counter_service.sensors.ListenableSensor

interface MotionDetectorLister {
    fun onMotion()
}

abstract class MotionDetector(
    sensorType: Int,
    context: Context,
) : ListenableSensor<MotionDetectorLister>(context, sensorType, oneShot = true) {
    abstract fun isMotionSignificant(event: SensorEvent): Boolean

    override fun onSensorChanged(event: SensorEvent) {
        if(isMotionSignificant(event)) {
            this.listeners.forEach { it.onMotion() }

            if (this.oneShot) {
                this.stop()
            }
        }
    }

    override fun onTrigger(event: TriggerEvent) {
        this.listeners.forEach { it.onMotion() }
    }

    companion object {
        fun getBest(context: Context, desired: Class<out MotionDetector>? = null): MotionDetector? {
            if (BuildConfig.DEBUG && desired != null) {
                return desired.constructors[0].newInstance(context) as MotionDetector
            }

            try { return SignificantMotionDetector(context) } catch (_: Exception) {}

            return null
        }
    }
}