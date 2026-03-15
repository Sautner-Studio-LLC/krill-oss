package krill.zone.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import krill.zone.app.CommonLayout

/**
 * Pill-shaped label showing the pin name.
 */
@Composable
fun NamePill(
    name: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CommonLayout.CORNER_RADIUS_LARGE),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        tonalElevation = CommonLayout.ELEVATION_SMALL
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = CommonLayout.PADDING_SMALL, vertical = CommonLayout.CHIP_VERTICAL_PADDING),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
    }
}