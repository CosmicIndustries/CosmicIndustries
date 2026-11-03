package com.cosmicindustries.threegs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cosmicindustries.threegs.data.AppDatabase
import com.cosmicindustries.threegs.data.ProjectEntity
import com.cosmicindustries.threegs.data.ProjectDao
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

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("COSMIC 3DGS", style = MaterialTheme.typography.headlineMedium)
        Text("Offline Gaussian Splatting Workbench", style = MaterialTheme.typography.titleMedium)
        Text("Build 001 · Project foundation", style = MaterialTheme.typography.labelLarge)

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
                                .put("application", "Cosmic 3DGS")
                                .put("applicationVersion", "0.1.0")
                                .put("status", "project-created")
                            File(directory, "project.json").writeText(manifest.toString(2))
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
                        status = "Created project: $name"
                    } catch (error: Exception) {
                        status = "Create failed: ${error.message ?: "unknown error"}"
                    }
                }
            }
        ) { Text("Create project") }

        Text(status, style = MaterialTheme.typography.bodySmall)
        Text("Projects (${projects.size})", style = MaterialTheme.typography.titleLarge)

        if (projects.isEmpty()) {
            Text("No projects yet. Create one to initialize its local workspace.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    dao.deleteById(project.id)
                                    File(project.directoryPath).deleteRecursively()
                                }
                                status = "Deleted project: ${project.name}"
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "This build creates offline project folders and JSON manifests. " +
                "Camera capture, pose estimation, Gaussian training, and splat rendering " +
                "are not implemented in Build 001.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(project.id, style = MaterialTheme.typography.labelSmall)
                Text(project.directoryPath, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
