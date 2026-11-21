package com.cosmicindustries.threegs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.cosmicindustries.threegs.data.AppDatabase
import com.cosmicindustries.threegs.data.ProjectEntity
import com.cosmicindustries.threegs.data.ProjectDao
import com.cosmicindustries.threegs.capture.BlurJudge
import com.cosmicindustries.threegs.capture.CameraLocks
import com.cosmicindustries.threegs.capture.CoveragePanel
import com.cosmicindustries.threegs.capture.CoverageTracker
import com.cosmicindustries.threegs.capture.IntervalShutter
import com.cosmicindustries.threegs.capture.MANIFEST_NAME
import com.cosmicindustries.threegs.capture.Pass
import com.cosmicindustries.threegs.capture.captureOneFrame
import com.cosmicindustries.threegs.capture.exportCaptureZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

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
        Text("Build 003 · Locked photogrammetry capture", style = MaterialTheme.typography.labelLarge)

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
                                .put("applicationVersion", "0.3.0")
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

/** Kept frames in the images folder (rejected ones live in images/rejected/). */
private fun countFrames(imagesDirectory: File): Int =
    imagesDirectory.listFiles { file ->
        file.isFile && file.name.startsWith("frame_") && file.extension.equals("jpg", true)
    }?.size ?: 0

@Composable
private fun CaptureScreen(project: ProjectEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val imagesDirectory = remember(project.id) {
        File(project.directoryPath, "sources/images").apply { mkdirs() }
    }
    var frameCount by remember(project.id) { mutableStateOf(countFrames(imagesDirectory)) }
    var status by remember(project.id) { mutableStateOf("Frame the aircraft, then tap Lock settings.") }
    var isCapturing by remember { mutableStateOf(false) }
    var locked by remember(project.id) { mutableStateOf(false) }
    var timedRunning by remember(project.id) { mutableStateOf(false) }
    var pass by remember(project.id) { mutableStateOf(Pass.MID) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val tracker = remember(project.id) { CoverageTracker(context) }
    val judge = remember(project.id) { BlurJudge() }
    DisposableEffect(tracker) {
        tracker.restoreFrom(File(imagesDirectory, MANIFEST_NAME))
        tracker.start()
        onDispose { tracker.stop() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        status = if (granted) "Camera permission granted." else "Camera permission denied."
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val count = exportCaptureZip(context, uri, imagesDirectory)
                    withContext(Dispatchers.Main) { status = "Exported $count photos to capture ZIP." }
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) { status = "Export failed: " + (error.message ?: "unknown error") }
                }
            }
        }
    }

    val cameraController = remember(project.id) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            CameraLocks.configureForPhotogrammetry(this)
        }
    }

    DisposableEffect(cameraController, lifecycleOwner, permissionGranted) {
        if (permissionGranted) cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()   // unbinding also drops any lock
            locked = false
        }
    }

    // One shot, shared by the Capture button and the timed shutter.
    suspend fun shoot() {
        if (isCapturing) return
        val next = maxOf(countFrames(imagesDirectory) + 1, frameCount + 1)
        isCapturing = true
        try {
            val outcome = captureOneFrame(
                cameraController, mainExecutor, project, imagesDirectory, next, pass, locked, tracker, judge
            )
            frameCount = countFrames(imagesDirectory)
            status = outcome.message
        } catch (error: Exception) {
            status = "Capture failed: " + (error.message ?: "unknown error")
        } finally {
            isCapturing = false
        }
    }

    val shutter = remember(project.id) { IntervalShutter(scope, intervalMs = 700L) { shoot() } }
    DisposableEffect(shutter) { onDispose { shutter.stop() } }

    Row(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (permissionGranted) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                    }
                },
                update = { it.controller = cameraController }
            )
        } else {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Camera permission is required to capture a dataset.")
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera permission")
                }
            }
        }

        Column(
            modifier = Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("← Projects") }
                Text(project.name, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Photos: " + frameCount + " · " + (if (locked) "Locked" else "Not locked"),
                style = MaterialTheme.typography.labelLarge
            )
            Text(status, style = MaterialTheme.typography.bodySmall)

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = permissionGranted && !isCapturing,
                onClick = {
                    scope.launch {
                        if (!locked) {
                            status = "Locking focus, exposure and white balance…"
                            try {
                                val result = CameraLocks.lock(cameraController, mainExecutor)
                                locked = true
                                judge.reset()
                                status = if (result.focusSucceeded) "Locked. Pick a pass and start."
                                else "Locked, but focus didn't confirm. Check the preview is sharp, then unlock and lock again."
                            } catch (error: Exception) {
                                status = error.message ?: "Lock failed."
                            }
                        } else {
                            shutter.stop()
                            timedRunning = false
                            CameraLocks.unlock(cameraController, mainExecutor)
                            locked = false
                            status = "Unlocked."
                        }
                    }
                }
            ) { Text(if (locked) "Unlock" else "Lock settings") }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = permissionGranted && locked,
                onClick = {
                    if (shutter.isRunning) {
                        shutter.stop()
                        timedRunning = false
                        status = "Timed capture stopped."
                    } else {
                        shutter.start()
                        timedRunning = true
                        status = "Timed capture running. Walk slowly."
                    }
                }
            ) { Text(if (timedRunning) "Stop timed capture" else "Start timed capture") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = permissionGranted && !isCapturing && !timedRunning,
                onClick = { scope.launch { shoot() } }
            ) { Text(if (isCapturing) "Capturing…" else "Capture one photo") }

            CoveragePanel(tracker = tracker, selected = pass, onSelect = { pass = it })

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = frameCount > 0 && !isCapturing && !timedRunning,
                onClick = {
                    val fileName = project.name.replace(Regex("[^A-Za-z0-9._-]"), "_") + "-capture.zip"
                    exportLauncher.launch(fileName)
                }
            ) { Text("Export capture ZIP") }
            Text(
                "Lock once the aircraft fills the middle of the frame. Do each pass (Low, Mid, High, Under) until its ring reads 12/12. Blurry frames are set aside automatically. GPS and device serial data are not copied into the capture manifest.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
