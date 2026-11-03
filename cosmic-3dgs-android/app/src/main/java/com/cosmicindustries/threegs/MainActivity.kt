package com.cosmicindustries.threegs

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.exifinterface.media.ExifInterface
import com.cosmicindustries.threegs.data.AppDatabase
import com.cosmicindustries.threegs.data.ProjectEntity
import com.cosmicindustries.threegs.data.ProjectDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.get(this).projectDao()
        val projectsRoot = File(filesDir, "projects").apply { mkdirs() }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProjectHome(dao = dao, projectsRoot = projectsRoot)
                }
            }
        }
    }
}

@Composable
private fun ProjectHome(
    dao: ProjectDao,
    projectsRoot: File
) {
    val projects by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var projectName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Offline project workspace ready.") }
    var captureProject by remember { mutableStateOf<ProjectEntity?>(null) }

    val activeProject = captureProject
    if (activeProject != null) {
        CaptureScreen(project = activeProject, onBack = { captureProject = null })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("COSMIC AIRCRAFT CAPTURE", style = MaterialTheme.typography.headlineMedium)
        Text("Offline photogrammetry dataset capture", style = MaterialTheme.typography.titleMedium)
        Text("Build 002 · Camera capture", style = MaterialTheme.typography.labelLarge)

        OutlinedTextField(
            value = projectName,
            onValueChange = { projectName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Project name") },
            singleLine = true
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = projectName.isNotBlank(),
            onClick = {
                val name = projectName.trim()
                scope.launch {
                    try {
                        val id = UUID.randomUUID().toString()
                        val directory = File(projectsRoot, id)
                        withContext(Dispatchers.IO) {
                            check(directory.mkdirs()) { "Could not create project directory." }
                            val createdAt = System.currentTimeMillis()
                            val manifest = JSONObject()
                                .put("schemaVersion", 1)
                                .put("projectId", id)
                                .put("name", name)
                                .put("createdAtEpochMs", createdAt)
                                .put("application", "Cosmic Aircraft Capture")
                                .put("applicationVersion", "0.2.0")
                                .put("status", "project-created")
                            File(directory, "project.json").writeText(manifest.toString(2))
                            File(directory, "sources/images").mkdirs()
                            dao.insert(
                                ProjectEntity(
                                    id = id,
                                    name = name,
                                    createdAtEpochMs = createdAt,
                                    directoryPath = directory.absolutePath
                                )
                            )
                        }
                        projectName = ""
                        status = "Created project: " + name
                    } catch (error: Exception) {
                        status = "Create failed: " + (error.message ?: "unknown error")
                    }
                }
            }
        ) { Text("Create project") }

        Text(status, style = MaterialTheme.typography.bodySmall)
        Text("Projects (" + projects.size + ")", style = MaterialTheme.typography.titleLarge)

        if (projects.isEmpty()) {
            Text("Create a project, then open Capture to photograph an aircraft or object.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onCapture = { captureProject = project },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    dao.deleteById(project.id)
                                    File(project.directoryPath).deleteRecursively()
                                }
                                status = "Deleted project: " + project.name
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Photos stay in app-private storage until you export a capture ZIP. Processing is performed by the Linux workbench; the phone is the capture client.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, onCapture: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(project.id, style = MaterialTheme.typography.labelSmall)
                Text(project.directoryPath, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCapture) { Text("Capture") }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun CaptureScreen(project: ProjectEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val imagesDirectory = remember(project.id) {
        File(project.directoryPath, "sources/images").apply { mkdirs() }
    }
    var frameCount by remember(project.id) {
        mutableStateOf(imagesDirectory.listFiles { file ->
            file.isFile && (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true))
        }?.size ?: 0)
    }
    var status by remember(project.id) { mutableStateOf("Ready. Keep the object in focus and overlap viewpoints.") }
    var isCapturing by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        status = if (granted) "Camera permission granted." else "Camera permission denied."
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Unable to open destination for writing.")
                    ZipOutputStream(output).use { zip ->
                        val files = imagesDirectory.listFiles { file ->
                            file.isFile && (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true) || file.name == "capture-manifest.json")
                        }?.sortedBy { it.name } ?: emptyList()
                        for (file in files) {
                            zip.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    withContext(Dispatchers.Main) { status = "Exported " + frameCount + " photos to capture ZIP." }
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) { status = "Export failed: " + (error.message ?: "unknown error") }
                }
            }
        }
    }

    val cameraController = remember(project.id) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        }
    }

    DisposableEffect(cameraController, lifecycleOwner, permissionGranted) {
        if (permissionGranted) cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Projects") }
            Text(project.name, style = MaterialTheme.typography.titleMedium)
        }
        Text("Photogrammetry capture", style = MaterialTheme.typography.headlineSmall)
        Text("Photos: " + frameCount + " · Back camera · Full-resolution JPEG", style = MaterialTheme.typography.labelLarge)

        if (permissionGranted) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                    }
                },
                update = { it.controller = cameraController }
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCapturing,
                onClick = {
                    val existing = imagesDirectory.listFiles { file -> file.name.startsWith("frame_") && file.extension.equals("jpg", true) }?.size ?: 0
                    val nextNumber = (existing + 1).coerceAtLeast(frameCount + 1)
                    val imageFile = File(imagesDirectory, "frame_" + nextNumber.toString().padStart(6, '0') + ".jpg")
                    val output = ImageCapture.OutputFileOptions.Builder(imageFile).build()
                    isCapturing = true
                    status = "Capturing frame " + nextNumber + "…"
                    cameraController.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        appendCaptureManifest(project, imagesDirectory, imageFile, nextNumber)
                                        val newCount = imagesDirectory.listFiles { file ->
                                            file.isFile && file.extension.equals("jpg", true)
                                        }?.size ?: 0
                                        withContext(Dispatchers.Main) {
                                            frameCount = newCount
                                            isCapturing = false
                                            status = "Saved " + imageFile.name
                                        }
                                    } catch (error: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                            status = "Photo saved, manifest update failed: " + (error.message ?: "unknown error")
                                        }
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                status = "Capture failed: " + exception.message
                            }
                        }
                    )
                }
            ) { Text(if (isCapturing) "Capturing…" else "Capture photo") }
        } else {
            Text("Camera permission is required to capture a dataset.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = frameCount > 0 && !isCapturing,
            onClick = {
                val fileName = project.name.replace(Regex("[^A-Za-z0-9._-]"), "_") + "-capture.zip"
                exportLauncher.launch(fileName)
            }
        ) { Text("Export capture ZIP for desktop processing") }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            "Capture around the object from multiple angles with substantial overlap. Avoid changing zoom or focus distance mid-sequence. GPS and device serial data are not copied into the capture manifest.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun appendCaptureManifest(project: ProjectEntity, imagesDirectory: File, imageFile: File, frameNumber: Int) {
    val manifestFile = File(imagesDirectory, "capture-manifest.json")
    val root = if (manifestFile.isFile) JSONObject(manifestFile.readText()) else JSONObject()
    val frames = root.optJSONArray("frames") ?: JSONArray()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
    val exif = ExifInterface(imageFile.absolutePath)
    val frame = JSONObject()
        .put("index", frameNumber)
        .put("file", imageFile.name)
        .put("capturedAtEpochMs", System.currentTimeMillis())
        .put("widthPx", bounds.outWidth)
        .put("heightPx", bounds.outHeight)
        .put("cameraFacing", "back")
        .put("orientation", exif.getAttribute(ExifInterface.TAG_ORIENTATION) ?: JSONObject.NULL)
    val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
    if (focalLength != null) frame.put("focalLength", focalLength)
    frames.put(frame)
    root.put("schemaVersion", 1)
        .put("projectId", project.id)
        .put("projectName", project.name)
        .put("captureMode", "multi_view_photogrammetry")
        .put("updatedAtEpochMs", System.currentTimeMillis())
        .put("locationDataStored", false)
        .put("deviceIdentifiersStored", false)
        .put("frames", frames)
    manifestFile.writeText(root.toString(2))
}
