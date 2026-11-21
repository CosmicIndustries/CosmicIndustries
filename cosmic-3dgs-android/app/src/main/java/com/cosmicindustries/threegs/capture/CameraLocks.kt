package com.cosmicindustries.threegs.capture

import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.LifecycleCameraController
import kotlinx.coroutines.delay
import java.util.concurrent.Executor

/**
 * Build 003: keeps every frame in a capture sequence on the same camera settings,
 * which is what COLMAP + SuGaR assume (one camera, fixed intrinsics, stable color).
 *
 * Call [configureForPhotogrammetry] right after the controller is created.
 * Call [lock] once the preview is running and the aircraft fills the frame,
 * and [unlock] when the sequence is done (or when leaving the screen).
 */
object CameraLocks {

    data class LockResult(val focusSucceeded: Boolean)

    /** Static settings: max quality, no flash, no pinch zoom, no tap-to-focus. */
    fun configureForPhotogrammetry(controller: LifecycleCameraController) {
        controller.setImageCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        controller.setImageCaptureFlashMode(ImageCapture.FLASH_MODE_OFF)
        controller.setPinchToZoomEnabled(false)   // zooming changes focal length and can switch lenses
        controller.setTapToFocusEnabled(false)    // a stray tap would refocus mid-sequence
    }

    /**
     * Meters and focuses once on the center of the frame, then holds focus, exposure
     * and white balance until [unlock]. Needs the controller to be bound (camera open).
     * Throws IllegalStateException if the camera isn't ready yet.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun lock(controller: LifecycleCameraController, executor: Executor): LockResult {
        val control = controller.cameraControl
            ?: throw IllegalStateException("Camera isn't ready yet. Wait for the preview, then lock.")

        // Main lens, no zoom. 1.0 keeps multi-camera phones on the wide (main) lens.
        controller.setZoomRatio(1.0f).awaitResult(executor)

        // Drop any earlier lock so metering starts fresh.
        val c2 = Camera2CameraControl.from(control)
        c2.clearCaptureRequestOptions().awaitResult(executor)

        // Focus + meter on the middle 30% of the frame, and keep that focus (no auto-cancel).
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f, 0.3f)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        ).disableAutoCancel().build()
        val result = control.startFocusAndMetering(action).awaitResult(executor)

        // Give auto-exposure and white balance a moment to settle on the new region.
        delay(SETTLE_MS)

        // Freeze exposure and white balance where they settled.
        c2.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                .build()
        ).awaitResult(executor)

        return LockResult(focusSucceeded = result.isFocusSuccessful)
    }

    /** Back to normal auto camera behavior. Safe to call when nothing is locked. */
    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun unlock(controller: LifecycleCameraController, executor: Executor) {
        val control = controller.cameraControl ?: return
        runCatching { Camera2CameraControl.from(control).clearCaptureRequestOptions().awaitResult(executor) }
        runCatching { control.cancelFocusAndMetering().awaitResult(executor) }
    }

    private const val SETTLE_MS = 700L
}
