package com.tokencoin.client.avatar

data class AvatarResolution(val active: AvatarInfo?, val needsDownload: Boolean)

/** 按契約順序決定形象；等待下載時先使用手動預設或最新形象。 */
object AvatarResolver {
    fun resolve(
        installed: List<AvatarInfo>,
        cardCharId: String = "",
        cardAvatarUrl: String = "",
        selections: Map<String, String> = emptyMap(),
        manualDefaultId: String? = null,
    ): AvatarResolution {
        val newest = installed.sortedByDescending { it.installedAt }
        if (cardCharId.isNotEmpty()) {
            newest.firstOrNull { it.id == selections[cardCharId] }?.let { return AvatarResolution(it, false) }
            newest.firstOrNull { it.charId == cardCharId }?.let { return AvatarResolution(it, false) }
        }
        if (cardAvatarUrl.isNotBlank()) {
            newest.firstOrNull { it.source == cardAvatarUrl }?.let { return AvatarResolution(it, false) }
        }
        val fallback = newest.firstOrNull { it.id == manualDefaultId } ?: newest.firstOrNull()
        return AvatarResolution(fallback, cardAvatarUrl.isNotBlank())
    }
}
