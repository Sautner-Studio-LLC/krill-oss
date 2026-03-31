package krill.zone.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import krill.zone.app.*

/**
 * Reusable edit + close toolbar for full-screen view screens (Diagram, Camera, etc.)
 */
@Composable
fun ViewScreenToolbar(onEdit: () -> Unit, onClose: () -> Unit) {
    Row(horizontalArrangement = Arrangement.End) {
        Spacer(modifier = Modifier.weight(1f, true))
        IconButton(onClick = onEdit) {
            EmojiText("✏️")
        }
        IconButton(onClick = onClose) {
            EmojiText("✖️")
        }
    }
}
