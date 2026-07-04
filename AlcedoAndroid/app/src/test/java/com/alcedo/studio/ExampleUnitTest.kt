package com.alcedo.studio

import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun hashGeneration_isNotEmpty() {
        val hash = com.alcedo.studio.data.model.generateHash128()
        assertTrue(hash.isNotEmpty())
        assertEquals(32, hash.length)
    }

    @Test
    fun editHistory_defaultVersionCreated() {
        val history = com.alcedo.studio.data.model.EditHistory(boundImageId = 1u)
        assertNotNull(history.getDefaultVersion())
        assertEquals(history.defaultVersionId, history.activeVersionId)
    }

    @Test
    fun pipelineParams_copyWorks() {
        val params = com.alcedo.studio.data.model.PipelineParams(exposure = 1.5f)
        val copy = params.copy(contrast = 0.5f)
        assertEquals(1.5f, copy.exposure, 0.001f)
        assertEquals(0.5f, copy.contrast, 0.001f)
    }
}
