package com.tokencoin.writer.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.tokencoin.writer.card.DecodedCard
import com.tokencoin.writer.card.TokenCard
import com.tokencoin.writer.card.TokenCardCodec
import java.io.IOException

data class TagInfo(
    val uid: String,
    val techs: List<String>,
    val ndefType: String?,
    val maxSize: Int?,
    val writable: Boolean?,
    val canMakeReadOnly: Boolean?,
)

data class ReadResult(
    val info: TagInfo,
    val usedBytes: Int,
    /** 解析成功的 Token幣資料；卡上沒有 Token幣記錄時為 null */
    val decoded: DecodedCard?,
    /** 解析失敗原因 */
    val error: String?,
    /** 卡上所有記錄的簡述 */
    val records: List<String>,
)

data class WriteResult(val info: TagInfo, val bytes: Int, val locked: Boolean)

object NfcIo {

    fun buildMessage(card: TokenCard, aarPackage: String?): NdefMessage {
        val records = mutableListOf(NdefRecord.createMime(TokenCard.MIME, TokenCardCodec.encode(card)))
        if (!aarPackage.isNullOrBlank()) records += NdefRecord.createApplicationRecord(aarPackage.trim())
        return NdefMessage(records.toTypedArray())
    }

    fun info(tag: Tag): TagInfo {
        val ndef = Ndef.get(tag)
        return TagInfo(
            uid = tag.id.joinToString(":") { "%02X".format(it) },
            techs = tag.techList.map { it.substringAfterLast('.') },
            ndefType = ndef?.type?.substringAfterLast('.'),
            maxSize = ndef?.maxSize,
            writable = ndef?.isWritable,
            canMakeReadOnly = ndef?.let { runCatching { it.canMakeReadOnly() }.getOrNull() },
        )
    }

    /** 寫入並回讀驗證；lock=true 時寫入後永久鎖為唯讀。 */
    fun write(tag: Tag, msg: NdefMessage, lock: Boolean): WriteResult {
        val size = msg.byteArrayLength
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) throw IOException("這張卡片已被鎖定為唯讀，無法寫入")
                if (size > ndef.maxSize) {
                    throw IOException("容量不足：資料 $size bytes，卡片可用 ${ndef.maxSize} bytes")
                }
                ndef.writeNdefMessage(msg)
                val back = ndef.ndefMessage
                if (back == null || !back.toByteArray().contentEquals(msg.toByteArray())) {
                    throw IOException("回讀驗證失敗，請重新寫入")
                }
                if (lock) {
                    if (!ndef.canMakeReadOnly()) throw IOException("已寫入，但此卡片不支援鎖定")
                    if (!ndef.makeReadOnly()) throw IOException("已寫入，但鎖定失敗")
                }
            } finally {
                runCatching { ndef.close() }
            }
            return WriteResult(info(tag), size, lock)
        }

        val formatable = NdefFormatable.get(tag)
            ?: throw IOException("不支援的卡片（不是 NDEF 標籤）")
        formatable.connect()
        try {
            if (lock) formatable.formatReadOnly(msg) else formatable.format(msg)
        } finally {
            runCatching { formatable.close() }
        }
        return WriteResult(info(tag), size, lock)
    }

    fun read(tag: Tag): ReadResult {
        val ndef = Ndef.get(tag)
            ?: return ReadResult(info(tag), 0, null, "卡片沒有 NDEF 資料（可能是空白未格式化的卡）", emptyList())
        val msg: NdefMessage? = try {
            ndef.connect()
            ndef.ndefMessage
        } finally {
            runCatching { ndef.close() }
        }
        val info = info(tag)
        if (msg == null) return ReadResult(info, 0, null, "空白卡片", emptyList())

        val summaries = msg.records.map(::describe)
        val record = findCardRecord(msg)
            ?: return ReadResult(info, msg.byteArrayLength, null, "卡片上沒有 Token幣 資料", summaries)

        return try {
            ReadResult(info, msg.byteArrayLength, TokenCardCodec.decode(record.payload), null, summaries)
        } catch (e: Exception) {
            ReadResult(info, msg.byteArrayLength, null, "Token幣 資料損毀：${e.message}", summaries)
        }
    }

    fun findCardRecord(msg: NdefMessage): NdefRecord? = msg.records.firstOrNull {
        it.tnf == NdefRecord.TNF_MIME_MEDIA && String(it.type, Charsets.US_ASCII) == TokenCard.MIME
    }

    /** 從 NdefMessage（例如 NDEF_DISCOVERED intent）解析 Token幣；沒有或損毀時拋例外。 */
    fun decodeMessage(msg: NdefMessage): DecodedCard {
        val record = findCardRecord(msg) ?: throw IOException("卡片上沒有 Token幣 資料")
        return TokenCardCodec.decode(record.payload)
    }

    private fun describe(r: NdefRecord): String {
        val type = String(r.type, Charsets.US_ASCII)
        return when (r.tnf) {
            NdefRecord.TNF_MIME_MEDIA -> "MIME $type（${r.payload.size} bytes）"
            NdefRecord.TNF_EXTERNAL_TYPE ->
                if (type == "android.com:pkg") "Android App 記錄：${String(r.payload, Charsets.US_ASCII)}"
                else "外部類型 $type"
            NdefRecord.TNF_WELL_KNOWN -> r.toUri()?.let { "URI $it" } ?: "Well-known $type"
            NdefRecord.TNF_ABSOLUTE_URI -> "URI ${r.toUri()}"
            else -> "TNF ${r.tnf} $type"
        }
    }
}
