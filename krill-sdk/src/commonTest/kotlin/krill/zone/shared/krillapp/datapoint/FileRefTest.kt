package krill.zone.shared.krillapp.datapoint

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for krill-oss#217: the `FILE` snapshot value type
 * (claim-check file references).
 *
 * Covers:
 *  - [FileRef] round-trips through JSON.
 *  - [DataType.FILE] is a distinct, additive enum value.
 *  - A serialized [FileRef] fits inside [Snapshot.value] exactly as the
 *    design intends — never as raw bytes.
 */
class FileRefTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `FileRef round-trips through JSON`() {
        val original = FileRef(
            hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85",
            sizeBytes = 204_800L,
            mime = "image/png",
            host = "installid-abc123",
            name = "snapshot.png",
        )
        val encoded = json.encodeToString(FileRef.serializer(), original)
        val decoded = json.decodeFromString(FileRef.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `FileRef name defaults to empty string`() {
        val ref = FileRef(hash = "abc", sizeBytes = 1, mime = "text/plain", host = "h1")
        assertEquals("", ref.name)
    }

    @Test
    fun `DataType FILE is a distinct additive value alongside the existing set`() {
        assertEquals(
            setOf(DataType.TEXT, DataType.JSON, DataType.DIGITAL, DataType.DOUBLE, DataType.COLOR, DataType.FILE),
            DataType.entries.toSet(),
        )
    }

    @Test
    fun `a serialized FileRef fits inside Snapshot value as a FILE-typed snapshot`() {
        val ref = FileRef(hash = "deadbeef", sizeBytes = 42, mime = "application/octet-stream", host = "h1")
        val serializedRef = json.encodeToString(FileRef.serializer(), ref)
        val snapshot = Snapshot(timestamp = 1000L, value = serializedRef)

        assertEquals(serializedRef, snapshot.value)
        val decoded = json.decodeFromString(FileRef.serializer(), snapshot.value)
        assertEquals(ref, decoded)
    }
}
