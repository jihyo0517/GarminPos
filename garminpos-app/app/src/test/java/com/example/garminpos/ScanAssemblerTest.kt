package com.example.garminpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanAssemblerTest {
    @Test fun assemblesUntilEnter() {
        val a = ScanAssembler(burstGapMillis = 100L)
        var t = 0L
        assertNull(a.onChar('A', t)); t += 10
        assertNull(a.onChar('B', t)); t += 10
        assertNull(a.onChar('C', t)); t += 10
        assertEquals("ABC", a.onChar('\n', t))
    }

    @Test fun emptyEnterReturnsNull() {
        val a = ScanAssembler()
        assertNull(a.onChar('\n', 0L))
    }

    @Test fun burstGapResetsBuffer() {
        val a = ScanAssembler(burstGapMillis = 100L)
        assertNull(a.onChar('X', 0L))        // 잔류 'X'
        assertNull(a.onChar('A', 500L))      // 간격>100 → 리셋 후 'A'
        assertNull(a.onChar('B', 510L))
        assertEquals("AB", a.onChar('\n', 520L))
    }

    @Test fun secondScanAfterFirst() {
        val a = ScanAssembler()
        a.onChar('A', 0L); a.onChar('\n', 10L)
        assertNull(a.onChar('B', 20L))
        assertEquals("B", a.onChar('\n', 30L))
    }
}
