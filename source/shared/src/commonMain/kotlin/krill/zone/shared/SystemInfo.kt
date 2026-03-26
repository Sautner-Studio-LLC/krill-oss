package krill.zone.shared

object SystemInfo {
    private var isServer = false

    var wasmPort = 0

    private var isReady = false

    fun isServer(): Boolean {  return isServer }


    fun setServer(value: Boolean ) {   isServer = value  }


    fun setReady(value: Boolean) {

            isReady = value

    }
}