# Cosmic 3DGS — Android Build 001

Kotlin / Jetpack Compose offline project-foundation APK.

Implemented:
- Offline project list backed by Room / SQLite.
- Project creation and deletion.
- App-private project directories.
- Per-project `project.json` manifest.
- No network permission or runtime network dependency.

Not implemented in Build 001:
- CameraX / Camera2 capture.
- Camera calibration export.
- Camera pose estimation / bundle adjustment.
- Gaussian training.
- Gaussian PLY parsing or rendering.
- Mesh reconstruction or aircraft-workbench export.

Target:
- minSdk 26.
- targetSdk / compileSdk 35.
- Kotlin 2.1.20, AGP 8.7.2, Gradle 8.9.
- Room 2.7.0, Compose BOM 2025.03.00.
