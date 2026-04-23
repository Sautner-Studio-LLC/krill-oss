package krill.zone.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import krill.zone.app.*
import krill.zone.shared.krillapp.executor.compute.*

/**
 * Reusable time range selector. Renders a horizontally scrollable row of FilterChips
 * for each ComputeTimeRange entry (excluding NONE). Used by the Graph editor and the
 * Diagram view toolbar so both surfaces share the same control and defaults.
 */
@Composable
fun TimeRangeSelector(
    selectedRange: ComputeTimeRange,
    onRangeSelected: (ComputeTimeRange) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_SMALL),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLabel) {
            Text(
                "Range:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ComputeTimeRange.entries.filter { it != ComputeTimeRange.YEAR }.forEach { range ->
            FilterChip(
                selected = range == selectedRange,
                onClick = { onRangeSelected(range) },
                label = { Text(range.title()) }
            )
        }
    }
}
