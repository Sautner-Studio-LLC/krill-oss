package krill.zone.app.startup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import krill.zone.app.*

/**
 * PIN entry screen shown during FTUE for native apps (desktop, Android, iOS).
 * The user enters their cluster's 4-digit PIN to join the mesh.
 *
 * @param onPinEntered Called with the 4-digit PIN when the user submits
 * @param errorMessage Optional error to display (e.g., "Wrong PIN — check your cluster PIN")
 */
@Composable
fun PinEntryScreen(
    onPinEntered: (String) -> Unit,
    errorMessage: String? = null
) {
    var pin by remember { mutableStateOf("") }
    var showError by remember(errorMessage) { mutableStateOf(errorMessage) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = CommonLayout.SERVER_FORM_MAX_WIDTH)
                .padding(CommonLayout.PADDING_LARGE)
        ) {
            Column(
                modifier = Modifier.padding(CommonLayout.PADDING_LARGE),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_LARGE)
            ) {
                Text(
                    text = "Join Krill Cluster",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Enter the 4-digit PIN configured during server installation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { newValue ->
                        // Only accept digits, max 4
                        val filtered = newValue.filter { it.isDigit() }.take(4)
                        pin = filtered
                        showError = null
                    },
                    label = { Text("Cluster PIN") },
                    placeholder = { Text("0000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pin.length == 4) onPinEntered(pin)
                        }
                    ),
                    modifier = Modifier.width(CommonLayout.SERVER_FIELD_WIDTH),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    )
                )

                showError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = { onPinEntered(pin) },
                    enabled = pin.length == 4,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
