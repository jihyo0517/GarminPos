package com.example.garminpos

class ScanAssembler(private val burstGapMillis: Long = 100L) {
    private val sb = StringBuilder()
    private var lastTime = -1L

    /** Enter 시 조립된 SN 반환(없으면 null). 그 외 문자는 누적. 간격 초과 시 버퍼 리셋. */
    fun onChar(c: Char, now: Long): String? {
        if (lastTime >= 0 && now - lastTime > burstGapMillis && sb.isNotEmpty()) {
            sb.setLength(0)
        }
        lastTime = now
        if (c == '\n' || c == '\r') {
            if (sb.isEmpty()) return null
            val sn = sb.toString()
            sb.setLength(0)
            return sn
        }
        sb.append(c)
        return null
    }
}
