package com.alcedo.studio

import com.alcedo.studio.data.model.*
import com.alcedo.studio.utils.IdGenerator
import com.alcedo.studio.utils.LruCache
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    // ============================================================
    // Data Model Tests
    // ============================================================

    @Test
    fun hashGeneration_isNotEmpty() {
        val hash = generateHash128()
        assertTrue(hash.isNotEmpty())
        assertEquals(32, hash.length)
    }

    @Test
    fun hashGeneration_isUnique() {
        val hash1 = generateHash128()
        val hash2 = generateHash128()
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun editHistory_defaultVersionCreated() {
        val history = EditHistory(boundImageId = 1L)
        assertNotNull(history.getDefaultVersion())
        assertEquals(history.defaultVersionId, history.activeVersionId)
    }

    @Test
    fun pipelineParams_copyWorks() {
        val params = PipelineParams(exposure = 1.5f)
        val copy = params.copy(contrast = 0.5f)
        assertEquals(1.5f, copy.exposure, 0.001f)
        assertEquals(0.5f, copy.contrast, 0.001f)
    }

    @Test
    fun pipelineParams_defaultValues() {
        val params = PipelineParams()
        assertEquals(0.0f, params.exposure, 0.001f)
        assertEquals(0.0f, params.contrast, 0.001f)
        assertEquals(0.0f, params.highlights, 0.001f)
        assertEquals(0.0f, params.shadows, 0.001f)
        assertEquals(6500.0f, params.whiteBalanceTemp, 0.001f)
        assertEquals(0.0f, params.whiteBalanceTint, 0.001f)
    }

    @Test
    fun sleeveElementEntity_defaultValues() {
        val entity = SleeveElementEntity(
            elementId = 1L,
            elementName = "test",
            elementType = 1,
            parentId = 0L,
            addedTime = 1000L,
            lastModifiedTime = 1000L
        )
        assertEquals(1L, entity.elementId)
        assertEquals("test", entity.elementName)
        assertEquals(0, entity.syncFlag)
        assertEquals(0L, entity.imageId)
    }

    @Test
    fun imageEntity_fullConstruction() {
        val entity = ImageEntity(
            id = 1,
            filePath = "/storage/photo.jpg",
            fileName = "photo.jpg",
            fileSize = 1024L,
            width = 1920,
            height = 1080,
            mimeType = "image/jpeg",
            dateAdded = 1000L,
            dateModified = 2000L,
            isRaw = false,
            iso = 400,
            fNumber = 2.8,
            focalLength = 50.0
        )
        assertEquals("photo.jpg", entity.fileName)
        assertEquals(1920, entity.width)
        assertEquals(400, entity.iso)
        assertFalse(entity.isRaw)
    }

    @Test
    fun editHistoryEntity_construction() {
        val entity = EditHistoryEntity(
            id = 1,
            imageId = 100L,
            versionId = "v1",
            createdTime = 1000L,
            paramsJson = """{"exposure":1.0}"""
        )
        assertEquals(100L, entity.imageId)
        assertEquals("v1", entity.versionId)
        assertTrue(entity.isActive)
    }

    @Test
    fun pipelinePresetEntity_construction() {
        val entity = PipelinePresetEntity(
            id = 1,
            name = "Cinematic",
            category = "Film",
            paramsJson = """{"exposure":0.5,"contrast":0.3}""",
            createdTime = 1000L,
            isBuiltIn = true
        )
        assertEquals("Cinematic", entity.name)
        assertTrue(entity.isBuiltIn)
    }

    // ============================================================
    // Utility Tests
    // ============================================================

    @Test
    fun idGenerator_nextId_isMonotonic() {
        val id1 = IdGenerator.nextId()
        val id2 = IdGenerator.nextId()
        assertTrue(id2 > id1)
    }

    @Test
    fun idGenerator_nextHash128_isValid() {
        val hash = IdGenerator.nextHash128()
        assertEquals(32, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun idGenerator_nextVersionId_hasPrefix() {
        val versionId = IdGenerator.nextVersionId()
        assertTrue(versionId.startsWith("v-"))
    }

    @Test
    fun lruCache_putAndGet() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        assertEquals(1, cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun lruCache_eviction() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3) // Should evict "a"
        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun lruCache_accessPreventsEviction() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a") // Access "a" to make it recently used
        cache.put("c", 3) // Should evict "b" not "a"
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun lruCache_remove() {
        val cache = LruCache<String, Int>(5)
        cache.put("a", 1)
        cache.remove("a")
        assertNull(cache.get("a"))
    }

    @Test
    fun lruCache_clear() {
        val cache = LruCache<String, Int>(5)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("a"))
    }

    @Test
    fun lruCache_contains() {
        val cache = LruCache<String, Int>(5)
        cache.put("a", 1)
        assertTrue(cache.contains("a"))
        assertFalse(cache.contains("b"))
    }

    @Test
    fun timeProvider_currentTimeMillis() {
        val t1 = System.currentTimeMillis()
        Thread.sleep(1)
        val t2 = System.currentTimeMillis()
        assertTrue(t2 >= t1)
    }

    @Test
    fun timeProvider_nanoTime() {
        val t1 = System.nanoTime()
        val t2 = System.nanoTime()
        assertTrue(t2 >= t1)
    }

    // ============================================================
    // History/Version Tests
    // ============================================================

    @Test
    fun historyVersion_construction() {
        val version = HistoryVersion("v1", 1000L, "", "Initial")
        assertEquals("v1", version.versionId)
        assertEquals("Initial", version.name)
        assertEquals(0.0f, version.params.exposure, 0.001f)
    }

    @Test
    fun editHistory_multipleVersions() {
        val history = EditHistory(boundImageId = 1L)
        val v1 = HistoryVersion("v1", 1000L, history.defaultVersionId, "Edit 1")
        history.versions.add(v1)
        history.activeVersionId = "v1"
        assertEquals(2, history.versions.size)
        assertEquals("v1", history.activeVersionId)
    }

    // ============================================================
    // Data integrity Tests
    // ============================================================

    @Test
    fun sleeveElementTypes_correct() {
        assertEquals(1, SleeveElementEntity.TYPE_FILE)
        assertEquals(2, SleeveElementEntity.TYPE_FOLDER)
    }

    @Test
    fun imageEntity_rawFormats() {
        val rawFile = ImageEntity(
            id = 1, filePath = "/test.NEF", fileName = "test.NEF",
            fileSize = 25000000L, width = 6000, height = 4000,
            mimeType = "image/x-nikon-nef", dateAdded = 1000L, dateModified = 1000L,
            isRaw = true, rawMake = "Nikon", rawModel = "D850"
        )
        assertTrue(rawFile.isRaw)
        assertEquals("Nikon", rawFile.rawMake)
    }

    @Test
    fun pipelineParams_allFieldsAccessible() {
        val params = PipelineParams(
            exposure = 1.0f,
            contrast = 0.5f,
            highlights = -0.3f,
            shadows = 0.4f,
            midtones = 0.1f,
            whiteBalanceTemp = 5500f,
            whiteBalanceTint = 5f,
            saturation = 0.2f,
            vibrance = 0.3f,
            clarity = 0.4f,
            sharpen = 0.5f,
            toneCurvePoints = 5,
            filmGrain = 0.1f,
            vignette = 0.2f
        )
        assertEquals(1.0f, params.exposure, 0.001f)
        assertEquals(0.5f, params.contrast, 0.001f)
        assertEquals(-0.3f, params.highlights, 0.001f)
        assertEquals(0.4f, params.shadows, 0.001f)
        assertEquals(0.1f, params.midtones, 0.001f)
        assertEquals(5500f, params.whiteBalanceTemp, 0.001f)
        assertEquals(5f, params.whiteBalanceTint, 0.001f)
        assertEquals(5, params.toneCurvePoints)
    }
}
