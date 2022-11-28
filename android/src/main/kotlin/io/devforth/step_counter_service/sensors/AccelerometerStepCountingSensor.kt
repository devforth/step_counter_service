package io.devforth.step_counter_service.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.os.SystemClock
import android.util.Log
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.properties.Delegates

private const val PEAK_THRESHOLD = 0.75

class AccelerometerStepCountingSensor(private val context: Context) :
    StepCountingSensor(Sensor.TYPE_LINEAR_ACCELERATION, context) {

    private val sharedPreferences = context.getSharedPreferences("id.devforth.step_counter_service.accelerometer", Context.MODE_PRIVATE)

    private var currentStepCount by Delegates.notNull<Int>()

    init {
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val expectedBootTime = sharedPreferences.getLong("boot_time", 0)
        // If difference between boot time and expected boot time is more than 100ms reset number of steps and boot time in storage
        if (bootTime - expectedBootTime > 100) {
            sharedPreferences.edit()
                .putLong("boot_time", bootTime)
                .putInt("count", 0)
                .apply()
        }
        currentStepCount = sharedPreferences.getInt("count", 0)
    }

    private val startVector: MutableList<Float> = mutableListOf()
    private var lastPeakTime: Long = 0L
    private var lastPeak: Float = 0f
    private var lastPeakDiff: Float = 0f

//    private var i = 0
//    private val accelerometerXAxis = mutableListOf<Float>()
//    private val accelerometerYAxis = mutableListOf<Float>()
//    private val accelerometerZAxis = mutableListOf<Float>()
//    private fun debugWrite(se: SensorEvent) {
//        i += 1
//        accelerometerXAxis.add(se.values[0])
//        accelerometerYAxis.add(se.values[1])
//        accelerometerZAxis.add(se.values[2])
//
//        if (i % 100 == 0) {
//            val outputStreamWriter =
//                OutputStreamWriter(context.openFileOutput("config.txt", Context.MODE_PRIVATE))
//            outputStreamWriter.write(accelerometerXAxis.joinToString(", ") + "\n")
//            outputStreamWriter.write(accelerometerYAxis.joinToString(", ") + "\n")
//            outputStreamWriter.write(accelerometerZAxis.joinToString(", ") + "\n")
//            outputStreamWriter.close()
//        }
//    }

    override fun start() {
        listeners.forEach { it.onStepCountChanged(currentStepCount) }
        return super.start()
    }

    override fun updateStepCount(event: SensorEvent): Int {
//        debugWrite(event)

        if (startVector.count() >= 3) {
            startVector.removeAt(0)
        }
        startVector.add(sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]))

        // Need 3 points in start vector to look for peaks
        if (startVector.count() != 3) {
            return currentStepCount
        }

        // Peak detection
        if (startVector[1] > startVector[0] && startVector[1] > startVector[2]) {
            if (startVector[1] < PEAK_THRESHOLD) {
//                Log.d("AccelerometerSCS", "Peak too small ${startVector[1]}")
                return currentStepCount
            }

            val peakDiff = abs(lastPeak - startVector[1])
            if (peakDiff <= lastPeakDiff * 0.5) {
                Log.d(
                    "AccelerometerSCS",
                    "Not as large as previous $peakDiff $lastPeakDiff ${lastPeakDiff * 0.5}"
                )
                lastPeakDiff = peakDiff
                return currentStepCount
            }
            if (lastPeakDiff <= peakDiff / 3) {
                Log.d("AccelerometerSCS", "Previous not large enough $peakDiff $lastPeakDiff ${peakDiff / 3}")
                lastPeakDiff = peakDiff
                return currentStepCount
            }

            lastPeakDiff = peakDiff

            if (lastPeakTime > 0) {
                // Discard peaks that are faster than 160bmp and slower than 20bpm
                val peakDelta = event.timestamp - lastPeakTime

                if (peakDelta < 60 * 1e9 / 160) {
                    Log.d("AccelerometerSCS", "Too fast ${peakDelta / 1e9}")
                } else if (peakDelta > 60 * 1e9 / 20) {
                    Log.d("AccelerometerSCS", "Too slow ${peakDelta / 1e9}")
                }

                if (peakDelta >= 60L * 1e9 / 180 && peakDelta <= 60L * 1e9 / 20) {
                    currentStepCount += 1
                    sharedPreferences.edit().putInt("count", currentStepCount).apply()

                    Log.d("AccelerometerSCS", "New step. Delta: $peakDelta")
                }
            }
            lastPeakTime = event.timestamp
        }
        return currentStepCount
    }
}