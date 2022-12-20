package io.devforth.step_counter_service.sensors.motion

import android.content.Context
import android.hardware.SensorEvent
import android.hardware.TriggerEvent
import io.devforth.step_counter_service.sensors.ListenableSensor
import io.devforth.step_counter_service.sensors.step.StepCounter

private val SUPPORTED_SENSORS = listOf(SignificantMotionDetector::class.java)

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
            if (desired != null) {
                return desired.constructors[0].newInstance(context) as MotionDetector
            }

            for (sensor in SUPPORTED_SENSORS) {
                try {
                    return sensor.constructors[0].newInstance(context) as MotionDetector
                } catch (_: Exception) {
                }
            }

            return null
//            throw Exception("Not supported sensor available on this device")
        }
    }
}