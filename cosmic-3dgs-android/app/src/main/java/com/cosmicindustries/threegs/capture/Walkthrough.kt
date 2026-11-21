package com.cosmicindustries.threegs.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.File

/** What is being scanned. Aircraft come first; the rest share the same pipeline. */
enum class Vehicle(val label: String) {
    AIRPLANE("Airplane"), HELICOPTER("Helicopter"), CAR("Car or truck"), BOAT("Boat"), OTHER("Other object")
}

enum class StepKind { LOCK, ORBIT, PHOTOS, SCALE }

/**
 * One step of a guided walk-through.
 * ORBIT steps are done when [target] heading sectors (of 12) are filled for [pass];
 * PHOTOS steps when [target] sharp photos are kept for [pass].
 */
data class GuideStep(
    val id: String,
    val title: String,
    val how: String,
    val kind: StepKind,
    val pass: Pass,
    val target: Int,
)

object Walkthroughs {
    private val lock = GuideStep("lock", "Lock the camera",
        "Stand back until the whole vehicle fits in the middle of the frame, on its brightest side, then tap Lock settings.",
        StepKind.LOCK, Pass.MID, 1)
    private fun mid(what: String) = GuideStep("mid", "Walk around at chest height",
        "Start at the nose or front and walk all the way around $what, about two steps back, with timed capture on. Keep it all in frame.",
        StepKind.ORBIT, Pass.MID, 12)
    private fun low(what: String) = GuideStep("low", "Walk around low",
        "Crouch so the camera is at wheel height and walk around $what again.",
        StepKind.ORBIT, Pass.LOW, 12)
    private fun high(what: String) = GuideStep("high", "Walk around high",
        "Hold the phone overhead (or use a step stool) and walk around $what, tilting down at the top surfaces.",
        StepKind.ORBIT, Pass.HIGH, 12)
    private fun under(what: String, n: Int) = GuideStep("under", "Underside",
        "Crouch low and aim up at $what. Cover as much of the underside as you can reach safely.",
        StepKind.ORBIT, Pass.UNDER, n)
    private fun details(what: String, n: Int) = GuideStep("details", "Close-ups",
        "Single shots, close in: $what. Take two or three angles of each.",
        StepKind.PHOTOS, Pass.DETAIL, n)
    private fun cabin(what: String, n: Int) = GuideStep("cabin", "Cabin",
        "$what Overlap each photo with the last by about half.",
        StepKind.PHOTOS, Pass.CABIN, n)
    private val eyepoint = GuideStep("eyepoint", "Pilot's eye view",
        "Sit in the left seat as if flying. Hold the phone at your eye position, level, and take three photos straight ahead. This sets the sim's eyepoint.",
        StepKind.PHOTOS, Pass.CABIN, 3)
    private val scale = GuideStep("scale", "Scale reference",
        "Put two pieces of tape on the ground about 3 m apart beside the vehicle, measure between them, take two photos showing both marks, and enter the distance below.",
        StepKind.SCALE, Pass.SCALE, 2)

    fun forVehicle(v: Vehicle): List<GuideStep> = when (v) {
        Vehicle.AIRPLANE -> listOf(
            lock, mid("the aircraft, wingtips and tail included"), low("the aircraft"),
            high("the aircraft"), under("the wings, belly and gear bays", 8),
            details("props and spinners, gear legs and wheels, antennas, lights, control-surface gaps", 30),
            cabin("Open the door. Sweep the panel left to right from the pilot seat, then the seats, floor and rear cabin.", 40),
            eyepoint, scale)
        Vehicle.HELICOPTER -> listOf(
            lock, mid("the helicopter, rotor tips and tail rotor included"), low("the helicopter"),
            high("the helicopter, looking down on the rotor head"), under("the belly and skids", 6),
            details("rotor head, tail rotor, skids, antennas, lights", 30),
            cabin("Sweep the panel from the pilot seat, then the seats and floor.", 30),
            eyepoint, scale)
        Vehicle.CAR -> listOf(
            lock, mid("the vehicle"), low("the vehicle, bumpers and wheels"),
            high("the vehicle, roof and hood"),
            details("wheels and tires, lights, grille, mirrors, badges", 25),
            cabin("Sweep the dash from the driver seat, then the seats and floor.", 30),
            eyepoint.copy(title = "Driver's eye view",
                how = "Sit in the driver seat as if driving. Hold the phone at your eye position, level, and take three photos straight ahead."),
            scale)
        Vehicle.BOAT -> listOf(
            lock, mid("the boat (from the dock or a trailer)"), low("the hull at the waterline"),
            high("the boat, deck and cockpit"),
            details("helm, cleats, rails, outboard or drive, windscreen", 25),
            cabin("Sweep the helm and seating area.", 25),
            eyepoint.copy(title = "Helm eye view",
                how = "Stand or sit at the helm as if driving and take three level photos straight ahead."),
            scale)
        Vehicle.OTHER -> listOf(lock, mid("the object"), low("the object"), high("the object"),
            details("anything with fine detail", 15), scale)
    }

    fun progress(step: GuideStep, tracker: CoverageTracker, locked: Boolean, scaleEntered: Boolean): Float =
        when (step.kind) {
            StepKind.LOCK -> if (locked) 1f else 0f
            StepKind.ORBIT -> (tracker.count(step.pass).toFloat() / step.target).coerceAtMost(1f)
            StepKind.PHOTOS -> (photosFor(step, tracker).toFloat() / step.target).coerceAtMost(1f)
            StepKind.SCALE -> ((tracker.photoCount(Pass.SCALE).coerceAtMost(step.target) +
                (if (scaleEntered) step.target else 0)).toFloat() / (2 * step.target))
        }

    /** Cabin and eyepoint share the CABIN pass; the eyepoint counts only what's beyond the cabin target. */
    private fun photosFor(step: GuideStep, tracker: CoverageTracker): Int = when (step.id) {
        "eyepoint" -> tracker.stepPhotos["eyepoint"] ?: 0
        "cabin" -> tracker.stepPhotos["cabin"] ?: 0
        else -> tracker.photoCount(step.pass)
    }
}

/**
 * The walk-through card: the current step, what to do, its progress and the whole scan's.
 * Overall progress is coverage (how much of the vehicle has been photographed), not a mesh:
 * a live mesh needs on-phone reconstruction, which comes later.
 */
@Composable
fun GuidePanel(
    steps: List<GuideStep>,
    index: Int,
    tracker: CoverageTracker,
    locked: Boolean,
    scaleText: String,
    onScaleText: (String) -> Unit,
    onIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaleEntered = scaleText.toDoubleOrNull()?.let { it > 0.2 } == true
    val step = steps[index]
    val stepProgress = Walkthroughs.progress(step, tracker, locked, scaleEntered)
    val overall = steps.map { Walkthroughs.progress(it, tracker, locked, scaleEntered) }.average().toFloat()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Scan ${"%.0f".format(overall * 100)}% covered", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { overall }, modifier = Modifier.fillMaxWidth())
            Text("Step ${index + 1} of ${steps.size}: ${step.title}" + if (stepProgress >= 1f) " ✓" else "",
                style = MaterialTheme.typography.titleSmall)
            Text(step.how, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { stepProgress }, modifier = Modifier.fillMaxWidth())
            Text(
                when (step.kind) {
                    StepKind.LOCK -> if (locked) "Locked." else "Not locked yet."
                    StepKind.ORBIT -> "${tracker.count(step.pass)} of ${step.target} directions covered"
                    StepKind.PHOTOS -> "${(stepProgress * step.target).toInt()} of ${step.target} photos"
                    StepKind.SCALE -> "${tracker.photoCount(Pass.SCALE)} of ${step.target} photos" +
                        if (scaleEntered) ", distance entered" else ", distance not entered"
                },
                style = MaterialTheme.typography.labelSmall,
            )
            if (step.kind == StepKind.SCALE) {
                OutlinedTextField(
                    value = scaleText,
                    onValueChange = onScaleText,
                    label = { Text("Tape distance (meters)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(enabled = index > 0, onClick = { onIndex(index - 1) }) { Text("Back") }
                TextButton(enabled = index < steps.size - 1, onClick = { onIndex(index + 1) }) {
                    Text(if (stepProgress >= 1f) "Next step" else "Skip")
                }
            }
        }
    }
}

/** Vehicle type and scale distance, kept in the project's project.json. */
data class ProjectMeta(val vehicle: Vehicle, val scaleDistanceM: Double?)

fun readProjectMeta(projectDir: File): ProjectMeta {
    val json = runCatching { JSONObject(File(projectDir, "project.json").readText()) }.getOrNull()
    val vehicle = runCatching { Vehicle.valueOf(json?.optString("vehicleType") ?: "") }.getOrDefault(Vehicle.AIRPLANE)
    val scale = json?.optJSONObject("scaleReference")?.optDouble("distanceM", Double.NaN)?.takeIf { !it.isNaN() }
    return ProjectMeta(vehicle, scale)
}

fun writeScaleDistance(projectDir: File, meters: Double?) {
    val file = File(projectDir, "project.json")
    val json = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    if (meters == null) json.remove("scaleReference")
    else json.put("scaleReference", JSONObject().put("distanceM", meters))
    file.writeText(json.toString(2))
}
