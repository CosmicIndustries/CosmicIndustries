package com.cosmicindustries.threegs.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import kotlin.math.asin
import kotlin.math.atan2

/** The four walk-around passes from the plan. */
enum class Pass(val label: String) { LOW("Low"), MID("Mid"), HIGH("High"), UNDER("Under") }

/** Where the back camera is pointing: elevation (+ up, - down) and heading, in degrees. */
data class CameraPose(val elevationDeg: Float, val headingDeg: Float)

/**
 * Coverage tracker. You pick the pass you're on (low / mid / high / underside);
 * each saved frame fills the 30-degree heading sector the camera was pointing at.
 * 12 of 12 sectors = a full orbit for that pass.
 *
 * Uses the game rotation vector (gyro + accelerometer, no compass) so the metal
 * of the aircraft or a hangar can't throw the heading off. It drifts a few degrees
 * over minutes, which is fine for "did I go all the way around". It is guidance only;
 * COLMAP still computes the real camera poses.
 */
class CoverageTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotation = FloatArray(9)

    /** Latest camera pose. Compose state, updated on the main thread by the sensor callback. */
    var latestPose: CameraPose? by mutableStateOf<CameraPose?>(null)
        private set

    /** Filled sectors per pass. Compose state, so the UI redraws as frames land. */
    val filled = mutableStateMapOf<Pass, Set<Int>>().apply { Pass.entries.forEach { put(it, emptySet()) } }

    val hasSensor: Boolean get() = sensor != null

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        // The back camera looks along the device's -Z axis. In world coords (x east, y north, z up):
        val lx = -rotation[2]
        val ly = -rotation[5]
        val lz = -rotation[8]
        val elevation = Math.toDegrees(asin(lz.coerceIn(-1f, 1f).toDouble())).toFloat()
        val heading = ((Math.toDegrees(atan2(lx.toDouble(), ly.toDouble())) + 360.0) % 360.0).toFloat()
        latestPose = CameraPose(elevation, heading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Call after a frame is kept. Returns the sector it filled (0-11), or null without a sensor reading. */
    fun recordFrame(pass: Pass, pose: CameraPose?): Int? {
        pose ?: return null
        val sector = sectorOf(pose.headingDeg)
        filled[pass] = (filled[pass] ?: emptySet()) + sector
        return sector
    }

    fun count(pass: Pass): Int = filled[pass]?.size ?: 0

    /** Rebuilds coverage from an existing capture-manifest.json (reopening a project). */
    fun restoreFrom(manifest: File) {
        Pass.entries.forEach { filled[it] = emptySet() }
        if (!manifest.isFile) return
        val frames = runCatching { JSONObject(manifest.readText()).optJSONArray("frames") }.getOrNull() ?: return
        for (i in 0 until frames.length()) {
            val f = frames.optJSONObject(i) ?: continue
            if (f.optBoolean("rejected", false)) continue
            val pass = runCatching { Pass.valueOf(f.optString("pass")) }.getOrNull() ?: continue
            if (!f.has("headingDeg")) continue
            val sector = sectorOf(f.getDouble("headingDeg").toFloat())
            filled[pass] = (filled[pass] ?: emptySet()) + sector
        }
    }

    companion object {
        const val SECTORS = 12
        fun sectorOf(headingDeg: Float): Int = ((headingDeg % 360f) / (360f / SECTORS)).toInt().coerceIn(0, SECTORS - 1)
    }
}
