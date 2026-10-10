package com.tokencoin.client.avatar

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VrmInspectorTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun vrm1MetadataAndThumbnailSlice() {
        val json = """{
            "extensions":{"VRMC_vrm":{"meta":{"name":"鯨魚娘","authors":["甲","乙"],
            "commercialUsage":"personalNonProfit","allowRedistribution":false,"modification":"prohibited",
            "avatarPermission":"everyone","thumbnailImage":0}}},
            "images":[{"bufferView":0,"mimeType":"image/png"}],
            "bufferViews":[{"buffer":0,"byteOffset":4,"byteLength":3}]
        }"""
        val file = temp.root.writeFixture("test.vrm", glb(json, byteArrayOf(9, 9, 9, 9, 1, 2, 3, 0)))
        val result = VrmInspector.inspect(file)
        assertEquals(1, result.vrmVersion)
        assertEquals("鯨魚娘", result.name)
        assertEquals(listOf("甲", "乙"), result.authors)
        assertEquals("個人非商業 · 禁止再配布 · 禁止修改 · 所有人可用", result.license)
        assertArrayEquals(byteArrayOf(1, 2, 3), result.thumbnailBytes)
        assertEquals("image/png", result.thumbnailMime)
    }

    @Test fun vrm0TextureToImageAndDefaultOffset() {
        val json = """{"extensions":{"VRM":{"meta":{"title":"舊形象","author":"作者","texture":0,
            "commercialUssageName":"Disallow","licenseName":"Redistribution_Prohibited","allowedUserName":"OnlyAuthor"}}},
            "textures":[{"source":1}],"images":[{}, {"bufferView":0,"mimeType":"image/jpeg"}],
            "bufferViews":[{"byteLength":2}]}"""
        val result = VrmInspector.inspect(temp.root.writeFixture("old.vrm", glb(json, byteArrayOf(4, 5))))
        assertEquals(0, result.vrmVersion)
        assertEquals("舊形象", result.name)
        assertEquals(listOf("作者"), result.authors)
        assertEquals("禁止商業使用 · 禁止再配布 · 僅作者可用", result.license)
        assertArrayEquals(byteArrayOf(4, 5), result.thumbnailBytes)
        assertEquals("image/jpeg", result.thumbnailMime)
    }

    @Test fun rejectsPlainGltf() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            VrmInspector.inspect(temp.root.writeFixture("plain.glb", glb("{}")))
        }
        assertTrue(error.message!!.contains("VRM"))
    }

    @Test fun rejectsNonGlb() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            VrmInspector.inspect(temp.root.writeFixture("bad.vrm", "not glb".toByteArray()))
        }
        assertEquals("不是 GLB 模型", error.message)
    }

    @Test fun rejectsVersionAndTruncation() {
        assertThrows(IllegalArgumentException::class.java) {
            VrmInspector.inspect(temp.root.writeFixture("v1.glb", glb("{}", version = 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VrmInspector.inspect(temp.root.writeFixture("short.glb", vrm1().dropLast(1).toByteArray()))
        }
    }

    @Test fun unknownLicenseValuesOmitted() {
        val result = VrmInspector.inspect(temp.root.writeFixture("unknown.vrm", glb(
            """{"extensions":{"VRMC_vrm":{"meta":{"commercialUsage":"future","modification":"future"}}}}""")))
        assertEquals("", result.license)
        assertNull(result.thumbnailBytes)
    }

    @Test fun thumbnailCannotReadOutsideBin() {
        val json = """{"extensions":{"VRMC_vrm":{"meta":{"thumbnailImage":0}}},
            "images":[{"bufferView":0}],"bufferViews":[{"byteOffset":3,"byteLength":4}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            VrmInspector.inspect(temp.root.writeFixture("bad.vrm", glb(json, byteArrayOf(1, 2, 3, 4))))
        }
    }
}
