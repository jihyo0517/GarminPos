package com.example.garminpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebouncerTest {
    @Test fun suppressesWithinWindow() {
        val d = Debouncer(windowMillis = 2000L)
        assertTrue(d.shouldSend("A", 0L))
        assertFalse(d.shouldSend("A", 1000L))
    }

    @Test fun allowsAfterWindow() {
        val d = Debouncer(windowMillis = 2000L)
        assertTrue(d.shouldSend("A", 0L))
        assertTrue(d.shouldSend("A", 2500L))
    }

    @Test fun differentSnIndependent() {
        val d = Debouncer(windowMillis = 2000L)
        assertTrue(d.shouldSend("A", 0L))
        assertTrue(d.shouldSend("B", 100L))
    }

    @Test fun prunesExpiredEntriesToAvoidUnboundedGrowth() {
        val d = Debouncer(windowMillis = 2000L)
        for (i in 0 until 200) assertTrue(d.shouldSend("SN$i", i.toLong()))
        // 윈도우가 한참 지난 뒤 새 스캔 → 만료된 기록은 정리되어야 함
        assertTrue(d.shouldSend("LAST", 10_000L))
        assertEquals(1, d.trackedCount)
    }
}
