package krill.zone.shared.io.beacon

fun interface BeaconSender {
    suspend fun sendSignal()
}