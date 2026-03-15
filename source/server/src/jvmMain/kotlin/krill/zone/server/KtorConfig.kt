package krill.zone.server


import co.touchlab.kermit.*
import io.ktor.server.engine.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import java.io.*
import java.security.*
import java.security.cert.*
import java.security.spec.*
import java.util.*

private val logger = Logger.withTag("KtorConfig")


private const val CERT_DIR = "/etc/krill/certs"

private const val NODE_DIR = "/srv/krill/data/nodes"
private const val PASSWORD_FILE = "$CERT_DIR/.pfx_password"

/**
 * Reads the PFX password from the secure password file.

 * 
 * Note: The password is read as bytes and converted directly to CharArray to minimize
 * the time sensitive data spends as an immutable String in memory.
 */
private fun readPfxPassword(): CharArray {
    val passwordFile = File(PASSWORD_FILE)
    return if (passwordFile.exists() && passwordFile.canRead()) {
        try {
            val bytes = passwordFile.readBytes()
            val chars = String(bytes, Charsets.UTF_8).trim().toCharArray()
            // Clear the byte array for security
            bytes.fill(0)
            if (chars.isNotEmpty()) {
                logger.i { "Using password from secure password file" }
                chars
            } else {
                logger.w { "Password file is empty, using legacy password" }
                throw IllegalArgumentException("Password file is empty, you may need to re-install krill")
            }
        } catch (e: Exception) {
            logger.e(e) { "Error reading password file, using legacy password" }
            throw IllegalArgumentException("Password file is empty, you may need to re-install krill")
        }
    } else {
        logger.e { "Password file not found at $PASSWORD_FILE, using legacy password" }
        throw IllegalArgumentException("Password file is empty, you may need to re-install krill")
    }
}

fun ApplicationEngine.Configuration.envConfig(config: krill.zone.ServerConfig) {

    val serverFile = File(NODE_DIR, installId())
    if (serverFile.exists()) {
        val server = fastJson.decodeFromString<Node>(serverFile.readText())
        server.run {
            config.port
        }
    }

    val password = readPfxPassword()
    val keyStoreFile = File("$CERT_DIR/krill.crt")
    val keyStore = loadKeyStoreFromPem(
        certFile = File("$CERT_DIR/krill.crt"),
        keyFile = File("$CERT_DIR/krill.key"),
        password = password
    )
    val configStre = File("/etc/krill/config.json")
    val config = fastJson.decodeFromString<krill.zone.ServerConfig>(configStre.readText())

    sslConnector(
        keyStore = keyStore,
        keyAlias = "krill",
        keyStorePassword = { password },
        privateKeyPassword = { password }) {
        port = config.port
        keyStorePath = keyStoreFile
    }
}


fun loadKeyStoreFromPem(certFile: File, keyFile: File, password: CharArray): KeyStore {
    val certFactory = CertificateFactory.getInstance("X.509")
    val certificate = certFile.inputStream().use { certFactory.generateCertificate(it) }

    val privateKey = readPrivateKeyFromPem(keyFile)

    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null, null) // initialize empty

    keyStore.setKeyEntry(
        "krill", privateKey, password,
        arrayOf(certificate)
    )
    return keyStore
}


fun readPrivateKeyFromPem(pemFile: File): PrivateKey {
    val pemContent = pemFile.readText()

    // Detect key type from PEM headers
    val isRsaKey = pemContent.contains("-----BEGIN RSA PRIVATE KEY-----")
    val isEcKey = pemContent.contains("-----BEGIN EC PRIVATE KEY-----")

    val cleanedContent = pemContent
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
        .replace("-----BEGIN EC PRIVATE KEY-----", "")
        .replace("-----END EC PRIVATE KEY-----", "")
        .replace("\\s".toRegex(), "")

    val decoded = Base64.getDecoder().decode(cleanedContent)
    val spec = PKCS8EncodedKeySpec(decoded)

    // If we know the key type from headers, use that
    if (isRsaKey) {
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(spec)
        } catch (e: Exception) {
            logger.d(e) { "Error loading RSA private key: ${e.message}" }
            throw e
        }
    }

    if (isEcKey) {
        return try {
            KeyFactory.getInstance("EC").generatePrivate(spec)
        } catch (e: Exception) {
            logger.d(e) { "Error loading EC private key: ${e.message}" }
            throw e
        }
    }

    // For generic PKCS8 "BEGIN PRIVATE KEY", try RSA first, then EC
    return try {
        KeyFactory.getInstance("RSA").generatePrivate(spec)
    } catch (rsaException: Exception) {
        logger.d { "Not an RSA key, trying EC..." }
        try {
            KeyFactory.getInstance("EC").generatePrivate(spec)
        } catch (ecException: Exception) {
            logger.e(ecException) { "Failed to load private key as RSA or EC" }
            throw IllegalArgumentException(
                "Unable to parse private key. Tried RSA and EC algorithms. " +
                        "RSA error: ${rsaException.message}, EC error: ${ecException.message}"
            )
        }
    }
}
