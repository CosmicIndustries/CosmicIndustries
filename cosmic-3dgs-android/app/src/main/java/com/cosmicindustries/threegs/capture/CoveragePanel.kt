package com.cosmicindustries.threegs.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Pass picker plus a 12-sector ring for the selected pass. Filled sectors = headings
 * already photographed. Put it beside the preview in the landscape layout.
 */
@Composable
fun CoveragePanel(
    tracker: CoverageTracker,
    selected: Pass,
    onSelect: (Pass) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filledColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two rows of two so the chips fit the narrow landscape side panel.
        Pass.entries.chunked(2).forEach { rowPasses ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPasses.forEach { pass ->
                    FilterChip(
                        selected = pass == selected,
                        onClick = { onSelect(pass) },
                        label = { Text("${pass.label} ${tracker.count(pass)}/${CoverageTracker.SECTORS}") },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val sectors = tracker.filled[selected] ?: emptySet()
            Canvas(modifier = Modifier.size(88.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val sweep = 360f / CoverageTracker.SECTORS
                for (i in 0 until CoverageTracker.SECTORS) {
                    // Heading 0 (where the run started facing) at the top, clockwise.
                    drawArc(
                        color = if (i in sectors) filledColor else emptyColor,
                        startAngle = -90f + i * sweep + 2f,
                        sweepAngle = sweep - 4f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                }
            }
            val pose = tracker.latestPose
            Text(
                text = if (!tracker.hasSensor) "No motion sensor: coverage off."
                else if (pose == null) "Waiting for motion sensor..."
                else "Aim ${"%.0f".format(pose.elevationDeg)}° · heading ${"%.0f".format(pose.headingDeg)}°",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
