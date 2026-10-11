package com.tokencoin.writer.card

import java.net.URI
import java.util.Base64

/** Web fallback: canonical card JSON in fragment; the fragment is not sent to the host. */
object WebCardLink {
    fun validate(value: String): URI {
        val uri = runCatching { URI(value.trim()) }.getOrElse { throw IllegalArgumentException("網頁版網址格式錯誤") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "網頁版網址需為 HTTPS，不能含帳號密碼或 #"
        }
        return uri
    }

    fun create(webUrl: String, card: TokenCard): String {
        val uri = validate(webUrl)
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(TokenCardCodec.encode(card))
        return uri.toASCIIString() + "#card=" + payload
    }

    fun decode(value: String): DecodedCard {
        val uri = URI(value)
        require((uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) ||
            (uri.scheme == "tokencoin" && uri.host == "card")) { "不是鯨幣卡片連結" }
        val fragment = uri.rawFragment ?: throw IllegalArgumentException("連結沒有卡片資料")
        require(fragment.startsWith("card=")) { "連結沒有卡片資料" }
        val payload = fragment.removePrefix("card=")
        require(payload.length <= 90000 && payload.matches(Regex("^[A-Za-z0-9_-]+$"))) { "卡片連結格式錯誤" }
        return TokenCardCodec.decode(Base64.getUrlDecoder().decode(payload))
    }
}
