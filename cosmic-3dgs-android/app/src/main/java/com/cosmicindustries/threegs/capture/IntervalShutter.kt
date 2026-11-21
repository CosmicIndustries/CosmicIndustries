package com.cosmicindustries.threegs.capture

import android.os.SystemClock
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** takePicture as a suspend call: returns once the JPEG is on disk. */
suspend fun LifecycleCameraController.takePictureTo(file: File, executor: Executor): File =
    suspendCancellableCoroutine { cont ->
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                if (cont.isActive) cont.resume(file)
            }
            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resumeWithException(exception)
            }
        })
    }

/**
 * Timed shutter: runs [shoot] every [intervalMs] until [stop]. A slow save never
 * overlaps the next shot, because the next one only starts after [shoot] returns.
 * 700 ms is a good start for a slow walk around a small aircraft (about 250 frames per 3 minutes).
 */
class IntervalShutter(
    private val scope: CoroutineScope,
    var intervalMs: Long = 700L,
    private val shoot: suspend () -> Unit,
) {
    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        job = scope.launch {
            while (isActive) {
                val started = SystemClock.elapsedRealtime()
                runCatching { shoot() }   // one failed frame shouldn't stop the run
                val wait = intervalMs - (SystemClock.elapsedRealtime() - started)
                if (wait > 0) delay(wait)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
