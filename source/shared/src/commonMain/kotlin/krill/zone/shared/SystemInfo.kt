package krill.zone.shared

object SystemInfo {
    private var isServer = false

    var wasmPort = 0

    /** API key passed via query string (kiosk mode: ?api_key=...) */
    var wasmApiKey: String? = null

    private var isReady = false

    fun isServer(): Boolean {  return isServer }


    fun setServer(value: Boolean ) {   isServer = value  }


    fun setReady(value: Boolean) {

            isReady = value

    }
}