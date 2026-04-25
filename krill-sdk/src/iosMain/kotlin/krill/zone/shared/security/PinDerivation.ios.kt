/**
 * iOS / iPadOS actual of [PinDerivation], backed by Apple's CoreCrypto via
 * Kotlin/Native cinterop. CoreCrypto is part of the iOS system and ships with
 * every device, so this implementation has no transitive runtime dependency.
 *
 * The cinterop calls go through `kotlinx.cinterop.usePinned` to safely pass
 * Kotlin `ByteArray`s into the C ABI without GC interference.
 */
package krill.zone.shared.security

import kotlinx.cinterop.*
import platform.CoreCrypto.*

@OptIn(ExperimentalForeignApi::class)
actual object PinDerivation {

    actual fun deriveBearerToken(pin: String): String {
        return hmacSha256(BEARER_HMAC_KEY.encodeToByteArray(), pin.encodeToByteArray()).toHex()
    }

    actual fun deriveBeaconToken(pin: String, nodeUuid: String, epochSeconds: Long): String {
        val nodeKey = hmacSha256(nodeUuid.encodeToByteArray(), pin.encodeToByteArray())
        val window = epochSeconds / TOTP_WINDOW_SECONDS
        val hmac = hmacSha256(nodeKey, window.toBigEndianBytes())
        return hmac.take(4).toByteArray().toHex()
    }

    private fun pbkdf2(password: String, salt: String, iterations: Int, keyLength: Int): ByteArray {
        val result = ByteArray(keyLength)
        val saltBytes = salt.encodeToByteArray()

        result.usePinned { resultPinned ->
            saltBytes.usePinned { saltPinned ->
                CCKeyDerivationPBKDF(
                    kCCPBKDF2,
                    password,
                    password.length.toULong(),
                    saltPinned.addressOf(0).reinterpret<UByteVar>(),
                    saltBytes.size.toULong(),
                    kCCPRFHmacAlgSHA256,
                    iterations.toUInt(),
                    resultPinned.addressOf(0).reinterpret<UByteVar>(),
                    keyLength.toULong()
                )
            }
        }
        return result
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val result = ByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
        key.usePinned { keyPinned ->
            data.usePinned { dataPinned ->
                result.usePinned { resultPinned ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        keyPinned.addressOf(0),
                        key.size.toULong(),
                        dataPinned.addressOf(0),
                        data.size.toULong(),
                        resultPinned.addressOf(0)
                    )
                }
            }
        }
        return result
    }
}

private fun ByteArray.toHex(): String = joinToString("") {
    val v = it.toInt() and 0xFF
    "${(v shr 4).digitToChar(16)}${(v and 0xF).digitToChar(16)}"
}

private fun Long.toBigEndianBytes(): ByteArray {
    val bytes = ByteArray(8)
    for (i in 7 downTo 0) {
        bytes[7 - i] = (this shr (i * 8) and 0xFF).toByte()
    }
    return bytes
}