package com.docscan.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainLogicTest {
    @Test
    fun movedShiftsItemForwardAndBack() {
        assertEquals(listOf(2, 3, 1), listOf(1, 2, 3).moved(0, 2))
        assertEquals(listOf(3, 1, 2), listOf(1, 2, 3).moved(2, 0))
        assertEquals(listOf(2, 1, 3), listOf(1, 2, 3).moved(0, 1))
    }

    @Test
    fun safeFileNameStripsPathCharacters() {
        assertEquals("a_b_c.pdf", safeFileName("a/b\\c.pdf"))
        assertEquals("document", safeFileName("   "))
        assertTrue(safeFileName("x".repeat(500)).length <= 80)
    }
}
