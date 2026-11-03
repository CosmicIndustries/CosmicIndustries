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
- YASim or JSBSim flight-dynamics exporters.

Required subsequent aircraft-engineering capabilities:
- Generate native FlightGear YASim aircraft configuration.
- Generate native JSBSim-ML aircraft and referenced model files.
- Maintain a shared, simulator-independent flight-dynamics profile with backend-specific exporters.
- Validate each generated model with the actual simulator solver/runtime. Missing aerodynamic or mass-property data must remain explicit; visual reconstruction alone is not sufficient.

See [Flight-Dynamics Outputs: YASim + JSBSim](docs/FLIGHT_DYNAMICS_EXPORTS.md).

Target:
- minSdk 26.
- targetSdk / compileSdk 35.
- Kotlin 2.1.20, AGP 8.7.2, Gradle 8.9.
- Room 2.7.0, Compose BOM 2025.03.00.
