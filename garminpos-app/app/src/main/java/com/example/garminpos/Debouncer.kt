package com.example.garminpos

class Debouncer(private val windowMillis: Long = 2000L) {
    private val lastSent = HashMap<String, Long>()

    internal val trackedCount: Int get() = lastSent.size

    fun shouldSend(sn: String, now: Long): Boolean {
        val prev = lastSent[sn]
        if (prev != null && now - prev < windowMillis) return false
        lastSent[sn] = now
        // 장기 실행 시 무한히 커지지 않게, 커지면 만료된 기록을 정리
        if (lastSent.size > MAX_TRACKED) {
            lastSent.entries.removeAll { now - it.value >= windowMillis }
        }
        return true
    }

    private companion object { const val MAX_TRACKED = 64 }
}
