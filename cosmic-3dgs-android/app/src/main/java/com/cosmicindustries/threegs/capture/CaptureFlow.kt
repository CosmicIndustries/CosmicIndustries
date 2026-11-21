package com.cosmicindustries.threegs.capture

import androidx.camera.view.LifecycleCameraController
import com.cosmicindustries.threegs.data.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor

/** What happened to one shot, for the status line. */
data class ShotOutcome(val frameNumber: Int, val kept: Boolean, val message: String)

/**
 * One frame, start to finish: take the photo, score sharpness, move it aside if blurry,
 * log it in the manifest, and fill the coverage ring. Both the Capture button and the
 * timed shutter call this, so they behave the same.
 *
 * Replaces the body of build 002's capture-button lambda (the takePicture call plus
 * appendCaptureManifest in onImageSaved).
 */
suspend fun captureOneFrame(
    controller: LifecycleCameraController,
    mainExecutor: Executor,
    project: ProjectEntity,
    imagesDirectory: File,
    nextNumber: Int,
    pass: Pass,
    locked: Boolean,
    tracker: CoverageTracker,
    judge: BlurJudge,
): ShotOutcome {
    val pose = tracker.latestPose   // where the camera pointed when the shutter fired
    val file = File(imagesDirectory, "frame_" + nextNumber.toString().padStart(6, '0') + ".jpg")
    controller.takePictureTo(file, mainExecutor)

    return withContext(Dispatchers.IO) {
        val score = Sharpness.score(file)
        val blurry = judge.isBlurry(score)
        val stored = if (blurry) moveToRejected(imagesDirectory, file) else file
        val report = appendCaptureManifestV2(
            project, imagesDirectory, stored, nextNumber,
            FrameExtras(sharpness = score, rejected = blurry, pass = pass, pose = pose, locked = locked),
        )
        val message = when {
            blurry -> "Frame $nextNumber looked blurry, set aside. Hold still and take it again."
            report.sizeMismatch -> "Frame $nextNumber came out a different size than frame 1. Hold the phone the same way."
            report.exposureDrift -> "Frame $nextNumber: exposure changed. Unlock and lock again."
            !locked -> "Saved frame $nextNumber. Settings aren't locked yet."
            else -> "Saved frame $nextNumber."
        }
        if (!blurry) withContext(Dispatchers.Main) { tracker.recordFrame(pass, pose) }
        ShotOutcome(nextNumber, kept = !blurry, message = message)
    }
}
