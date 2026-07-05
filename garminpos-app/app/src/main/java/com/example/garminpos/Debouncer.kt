package com.example.garminpos

class Debouncer(private val windowMillis: Long = 2000L) {
    private val lastSent = HashMap<String, Long>()

    fun shouldSend(sn: String, now: Long): Boolean {
        val prev = lastSent[sn]
        if (prev != null && now - prev < windowMillis) return false
        lastSent[sn] = now
        return true
    }
}
