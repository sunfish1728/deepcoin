package com.tokencoin.client.avatar

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AvatarInstallerTest {
    @get:Rule val temp = TemporaryFolder()
    private fun root() = File(temp.root, "avatars")

    @Test fun installsSingleModelAndReloads() {
        val input = temp.root.writeFixture("original.vrm", vrm1())
        val info = AvatarInstaller.install(root(), input, charId = "card", source = "https://example/model")
        assertTrue(info.id.matches(Regex("[A-Za-z0-9_-]+")))
        assertEquals("測試形象", info.name)
        assertEquals("card", info.charId)
        assertEquals("model.vrm", info.modelPath)
        assertEquals("{}", info.manifestJson)
        assertEquals(listOf(info), AvatarInstaller.load(root()))
        assertArrayEquals(input.readBytes(), File(root(), "${info.id}/model.vrm").readBytes())
    }

    @Test fun stripsTopFolderIgnoresMacFilesAndPreservesManifest() {
        val raw = """{ "v":1, "name":"角色包", "char_id":"manifest", "model":"models/CHAR.GLB", "thumb":"art/icon.png", "reactions":{"pet":true} }"""
        val bytes = zipBytes("bundle/avatar.json" to raw.toByteArray(), "bundle/models/CHAR.GLB" to vrm1(),
            "bundle/art/icon.png" to byteArrayOf(7), "__MACOSX/._bundle" to byteArrayOf(8), "bundle/.DS_Store" to byteArrayOf(9))
        val info = AvatarInstaller.install(root(), temp.root.writeFixture("package.zip", bytes), charId = "caller")
        assertEquals("角色包", info.name)
        assertEquals("manifest", info.charId)
        assertEquals("models/CHAR.GLB", info.modelPath)
        assertEquals("art/icon.png", info.thumbPath)
        assertEquals(raw, info.manifestJson)
        assertEquals(raw, AvatarInstaller.load(root()).single().manifestJson)
        assertFalse(File(root(), "${info.id}/__MACOSX").exists())
        assertFalse(File(root(), "${info.id}/.DS_Store").exists())
    }

    // Android 的 filesDir 經過符號連結（/data/user/0 → /data/data）；用含 .. 的根目錄模擬「非 canonical 路徑」
    @Test fun manifestPathsStayRelativeWhenRootIsNotCanonical() {
        val raw = """{"v":1,"model":"model.vrm","thumb":"thumb.png"}"""
        val bytes = zipBytes("avatar.json" to raw.toByteArray(), "model.vrm" to vrm1(), "thumb.png" to byteArrayOf(1))
        val root = File(temp.root, "x/../avatars")
        val info = AvatarInstaller.install(root, temp.root.writeFixture("p.zip", bytes))
        assertEquals("model.vrm", info.modelPath)
        assertEquals("thumb.png", info.thumbPath)
        assertTrue(File(root, "${info.id}/${info.modelPath}").isFile)
    }

    @Test fun zipSlipLeavesNoInstallDirectories() {
        listOf("../escape", "a/../../escape", "a\\..\\escape", "/escape", "C:/escape").forEachIndexed { index, path ->
            val input = temp.root.writeFixture("bad$index.zip", zipBytes(path to byteArrayOf(1), "model.vrm" to vrm1()))
            assertThrows(IllegalArgumentException::class.java) { AvatarInstaller.install(root(), input) }
            assertTrue(root().listFiles().orEmpty().isEmpty())
        }
        assertFalse(File(temp.root, "escape").exists())
    }

    @Test fun multipleModelsRequireManifestModel() {
        val input = temp.root.writeFixture("two.zip", zipBytes("a.vrm" to vrm1(), "b.GLB" to vrm1()))
        assertThrows(IllegalArgumentException::class.java) { AvatarInstaller.install(root(), input) }
        assertTrue(root().listFiles().orEmpty().isEmpty())
    }

    @Test fun loadSkipsCorruptAndCleansStagingAndDeleteWorks() {
        val info = AvatarInstaller.install(root(), temp.root.writeFixture("valid.vrm", vrm1()))
        File(root(), "corrupt").mkdir()
        File(root(), "corrupt/info.json").writeText("{}")
        File(root(), ".tmp-stale").mkdir()
        File(root(), ".tmp-stale/file").writeText("unfinished")
        assertEquals(listOf(info), AvatarInstaller.load(root()))
        assertFalse(File(root(), ".tmp-stale").exists())
        AvatarInstaller.delete(root(), info.id)
        assertTrue(AvatarInstaller.load(root()).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { AvatarInstaller.delete(root(), "../escape") }
    }

    @Test fun extractsEmbeddedThumbnail() {
        val json = """{"extensions":{"VRMC_vrm":{"meta":{"thumbnailImage":0}}},
            "images":[{"bufferView":0,"mimeType":"image/jpeg"}],"bufferViews":[{"byteLength":3}]}"""
        val info = AvatarInstaller.install(root(), temp.root.writeFixture("thumb.vrm", glb(json, byteArrayOf(1, 2, 3))))
        assertEquals("thumb.jpg", info.thumbPath)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(root(), "${info.id}/thumb.jpg").readBytes())
    }

    @Test fun nameFallbackAndMissingModel() {
        val info = AvatarInstaller.install(root(), temp.root.writeFixture("filename.vrm", vrm1("")))
        assertEquals("filename", info.name)
        val input = temp.root.writeFixture("missing.zip", zipBytes("avatar.json" to """{"model":"missing.vrm"}""".toByteArray(), "real.vrm" to vrm1()))
        assertThrows(IllegalArgumentException::class.java) { AvatarInstaller.install(root(), input) }
        assertEquals(listOf(info), AvatarInstaller.load(root()))
    }

    @Test fun caseInsensitiveModelAndEntryLimit() {
        val info = AvatarInstaller.install(root(), temp.root.writeFixture("upper.zip", zipBytes("MODEL.VRM" to vrm1())))
        assertEquals("MODEL.VRM", info.modelPath)
        val entries = (0..2000).map { "file$it" to byteArrayOf() }.toTypedArray()
        assertThrows(IllegalArgumentException::class.java) {
            AvatarInstaller.install(root(), temp.root.writeFixture("many.zip", zipBytes(*entries)))
        }
        assertEquals(listOf(info), AvatarInstaller.load(root()))
    }

    @Test fun unsafeManifestAndInvalidMagicAreRejected() {
        val input = temp.root.writeFixture("manifest.zip", zipBytes("avatar.json" to """{"model":"../outside.vrm"}""".toByteArray(), "real.vrm" to vrm1()))
        assertThrows(IllegalArgumentException::class.java) { AvatarInstaller.install(root(), input) }
        val error = assertThrows(IllegalArgumentException::class.java) {
            AvatarInstaller.install(root(), temp.root.writeFixture("bad.bin", byteArrayOf(1, 2, 3, 4)))
        }
        assertEquals("不是 VRM 模型或角色包", error.message)
        assertTrue(root().listFiles().orEmpty().isEmpty())
    }
}
