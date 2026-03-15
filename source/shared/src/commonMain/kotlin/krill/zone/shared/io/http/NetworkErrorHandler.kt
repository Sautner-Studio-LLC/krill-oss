package krill.zone.shared.io.http

fun Exception.isSSLError(): Boolean {
    val message = this.message ?: ""

    return message.contains("signature") ||
            message.contains("certification")

}

