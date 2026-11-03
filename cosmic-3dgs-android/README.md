# Cosmic Aircraft Capture — Android Build 002

Offline CameraX capture companion for the Linux-first Cosmic Aircraft Workbench.

Implemented in Build 002:
- Offline project list backed by Room / SQLite.
- Project creation and deletion.
- CameraX live preview using the rear camera.
- Full-resolution JPEG capture into each project's `sources/images/` directory.
- Per-image capture manifest with frame index, timestamp, image dimensions, orientation and available focal-length EXIF.
- Capture ZIP export through Android's document picker.
- No network permission and no GPS/device-serial data in the generated capture manifest.

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
