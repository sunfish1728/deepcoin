package com.tokencoin.client.avatar

import org.junit.Assert.*
import org.junit.Test

class AvatarResolverTest {
    private val selected = avatar("selected")
    private val match = avatar("match", charId = "card", installedAt = 2)
    private val newest = avatar("newest", installedAt = 10)
    private val url = "https://example/avatar"
    private val downloaded = avatar("url", source = url, installedAt = 3)
    private val all = listOf(selected, match, newest, downloaded)

    @Test fun rule1SelectionBeatsCharAndUrl() {
        assertEquals(AvatarResolution(selected, false), AvatarResolver.resolve(all, "card", url, mapOf("card" to "selected"), "newest"))
    }

    @Test fun rule2NewestCharacterMatchBeatsUrl() {
        val newerMatch = match.copy(id = "newer", installedAt = 5)
        assertEquals(AvatarResolution(newerMatch, false), AvatarResolver.resolve(all + newerMatch, "card", url, mapOf("card" to "deleted")))
    }

    @Test fun rule3InstalledUrlNeedsNoDownload() {
        assertEquals(AvatarResolution(downloaded, false), AvatarResolver.resolve(all, "other", url, manualDefaultId = "selected"))
    }

    @Test fun rule3MissingUrlDownloadsWithFallback() {
        assertEquals(AvatarResolution(selected, true), AvatarResolver.resolve(all, "other", "https://new", manualDefaultId = "selected"))
        assertEquals(AvatarResolution(newest, true), AvatarResolver.resolve(all, "other", "https://new"))
        assertEquals(AvatarResolution(null, true), AvatarResolver.resolve(emptyList(), cardAvatarUrl = url))
    }

    @Test fun rule4ManualDefault() {
        assertEquals(AvatarResolution(selected, false), AvatarResolver.resolve(all, manualDefaultId = "selected"))
    }

    @Test fun rule5NewestAndStaleDefault() {
        assertEquals(AvatarResolution(newest, false), AvatarResolver.resolve(all, manualDefaultId = "deleted"))
        assertEquals(AvatarResolution(newest, false), AvatarResolver.resolve(all))
    }

    @Test fun rule6EmptyLibrary() {
        assertEquals(AvatarResolution(null, false), AvatarResolver.resolve(emptyList(), "card", selections = mapOf("card" to "deleted")))
    }

    @Test fun emptyCharacterDoesNotApplySelectionOrMatch() {
        assertEquals(AvatarResolution(downloaded, false), AvatarResolver.resolve(all, "", url, mapOf("" to "selected")))
    }
}
