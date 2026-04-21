package krill.zone.shared.node

import kotlinx.serialization.*

@Serializable
enum class DigitalState {
    ON, OFF
}

fun DigitalState.toDouble() : Double {
    return try {
        when (this) {
            DigitalState.ON -> 1.0
            DigitalState.OFF -> 0.0
        }
    }catch (_: Exception) {
        0.0
    }
}

enum class NodeState {
    PAUSED, INFO, WARN, SEVERE, ERROR, PAIRING, NONE, PROCESSING, EXECUTED, DELETING, CREATED, USER_EDIT, USER_SUBMIT, SNAPSHOT_UPDATE, UNAUTHORISED, EDITING
}
