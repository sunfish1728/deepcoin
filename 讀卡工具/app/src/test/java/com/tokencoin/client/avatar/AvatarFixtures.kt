package com.tokencoin.client.avatar

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun glb(json: String, bin: ByteArray? = null, version: Int = 2): ByteArray {
    val raw = json.toByteArray()
    val padded = raw + ByteArray((4 - raw.size % 4) % 4) { 32 }
    val binary = bin?.let { it + ByteArray((4 - it.size % 4) % 4) }
    val size = 20 + padded.size + (binary?.let { 8 + it.size } ?: 0)
    return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(0x46546c67); putInt(version); putInt(size)
        putInt(padded.size); putInt(0x4e4f534a); put(padded)
        if (binary != null) { putInt(binary.size); putInt(0x004e4942); put(binary) }
    }.array()
}

internal fun vrm1(name: String = "測試形象"): ByteArray = glb(JSONObject().apply {
    put("extensions", JSONObject().put("VRMC_vrm", JSONObject().put("meta", JSONObject().put("name", name))))
}.toString())

internal fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        entries.forEach { (path, data) -> zip.putNextEntry(ZipEntry(path)); zip.write(data); zip.closeEntry() }
    }
    return bytes.toByteArray()
}

internal fun File.writeFixture(name: String, bytes: ByteArray): File = File(this, name).apply { writeBytes(bytes) }

internal fun avatar(id: String, charId: String = "", source: String = "import", installedAt: Long = 1) =
    AvatarInfo(id, id, charId, "model.vrm", 1, emptyList(), "", "", source, 10, installedAt, "{}")
