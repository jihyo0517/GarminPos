package com.example.garminpos

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
}
