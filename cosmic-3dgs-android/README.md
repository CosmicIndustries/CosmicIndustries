# Cosmic Aircraft Capture — Android Build 003

Offline CameraX capture companion for the Linux-first Cosmic Aircraft Workbench. Package ID: `com.cosmicindustries.aircraftcapture` (side-by-side with the retired Build 001 shell).

Implemented in Build 002:
- Offline project list backed by Room / SQLite.
- Project creation and deletion.
- CameraX live preview using the rear camera.
- Full-resolution JPEG capture into each project's `sources/images/` directory.
- Per-image capture manifest with frame index, timestamp, image dimensions, orientation and available focal-length EXIF.
- Capture ZIP export through Android's document picker.
- No network permission and no GPS/device-serial data in the generated capture manifest.

Added in Build 003 (0.3.0):
- Lock settings button: focus, exposure and white balance held for the whole sequence (CameraX focus-metering lock + Camera2 AE/AWB lock).
- Zoom pinned at 1.0 on the main lens; pinch-to-zoom, tap-to-focus and flash off.
- Landscape-locked capture screen; EXIF orientation reset to normal so every frame shares one pixel layout (original tag kept in the manifest).
- Timed shutter (every 0.7 s) alongside single shots.
- Blur check (variance of the Laplacian vs. recent sharp frames); blurry frames move to `sources/images/rejected/` and stay out of the export.
- Coverage tracker: Low / Mid / High / Under passes, 12 heading sectors each, from the gyro (game rotation vector).
- Manifest schema 2: exposure time, ISO, white balance, sharpness, pass, aim, lock state, size and exposure-drift warnings.
- Export layout `images/*.jpg` + `capture-manifest.json`, ready for COLMAP.

Not yet implemented:
- Video capture / video-to-frame extraction on-device.
- Camera intrinsics calibration or lens-distortion calibration.
- Visual-inertial pose estimation / SLAM.
- COLMAP or feature matching on the phone.
- Gaussian training or splat rendering on the phone.
- Live masking, object segmentation, or reconstruction preview.

The capture ZIP is intended to be imported into the desktop workbench. COLMAP/GSplat reconstruction runs on a suitable Linux workstation or CUDA host; the phone is a capture and review client, not the training device.

Target:
- minSdk 26.
- compileSdk 36, targetSdk 35.
- Kotlin 2.1.20, AGP 8.10.1, Gradle 8.11.1.
- CameraX 1.6.2, Room 2.7.0, Compose BOM 2025.03.00.

The capture manifest intentionally excludes GPS coordinates and device serial numbers. Original EXIF remains in the JPEGs as written by CameraX; the desktop workbench should strip sensitive EXIF before sharing datasets externally.
