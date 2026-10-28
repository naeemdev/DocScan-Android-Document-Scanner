package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.CropQuad
import com.docscan.app.domain.model.Point
import com.docscan.app.domain.model.isUsable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropTest {
    @Test
    fun fullAndSkewedQuadsAreUsable() {
        assertTrue(CropQuad.FULL.isUsable())
        assertTrue(CropQuad(Point(0.1f, 0.2f), Point(0.9f, 0.1f), Point(0.95f, 0.9f), Point(0.05f, 0.8f)).isUsable())
    }

    @Test
    fun crossedTinyOrOutOfBoundsQuadsAreRejected() {
        // Top corners swapped: the outline crosses itself.
        assertFalse(CropQuad(Point(0.9f, 0.1f), Point(0.1f, 0.1f), Point(0.9f, 0.9f), Point(0.1f, 0.9f)).isUsable())
        assertFalse(CropQuad(Point(0.5f, 0.5f), Point(0.51f, 0.5f), Point(0.51f, 0.51f), Point(0.5f, 0.51f)).isUsable())
        assertFalse(CropQuad(Point(-0.1f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f)).isUsable())
    }
}
