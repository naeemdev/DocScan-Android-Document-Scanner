package com.docscan.app.data.repository

import com.docscan.app.domain.model.CropQuad
import com.docscan.app.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropCodecTest {
    @Test
    fun roundTrips() {
        val crop = CropQuad(Point(0.1f, 0.2f), Point(0.9f, 0.1f), Point(0.95f, 0.9f), Point(0.05f, 0.8f))
        assertEquals(crop, decodeCrop(encodeCrop(crop)))
    }

    @Test
    fun garbageDecodesToNoCrop() {
        assertNull(decodeCrop("1,2,3"))
        assertNull(decodeCrop("a,b,c,d,e,f,g,h"))
    }
}
