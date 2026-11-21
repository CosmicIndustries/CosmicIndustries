package com.cosmicindustries.threegs.capture

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.cosmicindustries.threegs.data.ProjectEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** What build 003 knows about a frame beyond build 002's fields. */
data class FrameExtras(
    val sharpness: Double,
    val rejected: Boolean,
    val pass: Pass,
    val pose: CameraPose?,
    val locked: Boolean,
)

/** Result of writing one frame, so the screen can warn right away. */
data class FrameReport(
    val widthPx: Int,
    val heightPx: Int,
    val sizeMismatch: Boolean,     // not the same pixel size as frame 1
    val exposureDrift: Boolean,    // exposure or ISO moved more than 10% from frame 1
)

/**
 * Replaces build 002's appendCaptureManifest. Same file (capture-manifest.json in the
 * images folder) and the same fields, plus: sharpness, rejected, pass, elevation/heading,
 * lock state, exposure time, ISO, white balance, and the original EXIF orientation.
 *
 * It also resets the JPEG's EXIF orientation tag to "normal" (pixels untouched), so
 * every tool reads the frames in the camera sensor's own layout and they all share one
 * size. The original tag is kept in the manifest. Gallery apps will show the
 * photos sideways; that is expected.
 */
fun appendCaptureManifestV2(
    project: ProjectEntity,
    imagesDirectory: File,
    imageFile: File,
    frameNumber: Int,
    extras: FrameExtras,
): FrameReport {
    val manifestFile = File(imagesDirectory, MANIFEST_NAME)
    val root = if (manifestFile.isFile) JSONObject(manifestFile.readText()) else JSONObject()
    val frames = root.optJSONArray("frames") ?: JSONArray()

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(imageFile.absolutePath, bounds)

    val exif = ExifInterface(imageFile.absolutePath)
    val originalOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    val exposureS = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, Double.NaN)
    val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1)
    val whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)
    val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)

    if (!extras.rejected && originalOrientation != ExifInterface.ORIENTATION_NORMAL) {
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
    }

    // Compare with the first kept frame.
    var first: JSONObject? = null
    for (i in 0 until frames.length()) {
        val f = frames.optJSONObject(i) ?: continue
        if (!f.optBoolean("rejected", false)) { first = f; break }
    }
    val sizeMismatch = first != null && !extras.rejected &&
        (first.optInt("widthPx") != bounds.outWidth || first.optInt("heightPx") != bounds.outHeight)
    val firstExposure = first?.optDouble("exposureTimeS", Double.NaN) ?: Double.NaN
    val firstIso = first?.optInt("iso", -1) ?: -1
    val exposureDrift = !extras.rejected && extras.locked && first != null &&
        ((!firstExposure.isNaN() && !exposureS.isNaN() && abs(exposureS - firstExposure) > 0.10 * firstExposure) ||
            (firstIso > 0 && iso > 0 && abs(iso - firstIso) > 0.10 * firstIso))

    val frame = JSONObject()
        .put("index", frameNumber)
        .put("file", imageFile.name)
        .put("capturedAtEpochMs", System.currentTimeMillis())
        .put("widthPx", bounds.outWidth)
        .put("heightPx", bounds.outHeight)
        .put("cameraFacing", "back")
        .put("orientation", ExifInterface.ORIENTATION_NORMAL)
        .put("originalExifOrientation", originalOrientation)
        .put("sharpness", extras.sharpness)
        .put("rejected", extras.rejected)
        .put("pass", extras.pass.name)
        .put("locked", extras.locked)
        .put("sizeMismatch", sizeMismatch)
        .put("exposureDrift", exposureDrift)
    if (focalLength != null) frame.put("focalLength", focalLength)
    if (!exposureS.isNaN()) frame.put("exposureTimeS", exposureS)
    if (iso > 0) frame.put("iso", iso)
    if (whiteBalance != null) frame.put("whiteBalance", whiteBalance)
    extras.pose?.let {
        frame.put("elevationDeg", it.elevationDeg.toDouble())
        frame.put("headingDeg", it.headingDeg.toDouble())
    }
    frames.put(frame)

    root.put("schemaVersion", 2)
        .put("appBuild", "003")
        .put("projectId", project.id)
        .put("projectName", project.name)
        .put("captureMode", "multi_view_photogrammetry")
        .put("orientationNormalized", true)
        .put("updatedAtEpochMs", System.currentTimeMillis())
        .put("locationDataStored", false)
        .put("deviceIdentifiersStored", false)
        .put("frames", frames)
    manifestFile.writeText(root.toString(2))

    return FrameReport(bounds.outWidth, bounds.outHeight, sizeMismatch, exposureDrift)
}

/** Moves a blurry frame out of the export set into images/rejected/. Returns its new location. */
fun moveToRejected(imagesDirectory: File, imageFile: File): File {
    val dir = File(imagesDirectory, REJECTED_DIR).apply { mkdirs() }
    val target = File(dir, imageFile.name)
    if (!imageFile.renameTo(target)) {
        imageFile.copyTo(target, overwrite = true)
        imageFile.delete()
    }
    return target
}

/**
 * Replaces build 002's export. Writes the COLMAP/SuGaR layout:
 *   images/frame_000001.jpg ...
 *   capture-manifest.json
 * Rejected (blurry) frames stay on the phone. Returns the number of photos written.
 */
fun exportCaptureZip(context: Context, destination: Uri, imagesDirectory: File): Int {
    val photos = imagesDirectory.listFiles { f ->
        f.isFile && (f.extension.equals("jpg", true) || f.extension.equals("jpeg", true))
    }?.sortedBy { it.name } ?: emptyList()
    val manifest = File(imagesDirectory, MANIFEST_NAME)
    val out = context.contentResolver.openOutputStream(destination)
        ?: throw IllegalStateException("Unable to open destination for writing.")
    ZipOutputStream(out.buffered()).use { zip ->
        // JPEGs barely compress; storing them would be faster, but STORED needs a CRC per entry.
        for (photo in photos) {
            zip.putNextEntry(ZipEntry("images/${photo.name}"))
            photo.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        if (manifest.isFile) {
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            manifest.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return photos.size
}

const val MANIFEST_NAME = "capture-manifest.json"
const val REJECTED_DIR = "rejected"
