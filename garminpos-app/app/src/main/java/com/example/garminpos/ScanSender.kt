package com.example.garminpos

import android.util.Log
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class ScanSender(
    private val locator: ServerLocator,
    private val device: String,
    private val retryBaseDelayMillis: Long = 500L,
    private val discover: () -> String? = { Discovery.discover(locator.port) },
    private val onDrop: (String) -> Unit = {},
) {
    private val pool = Executors.newSingleThreadExecutor()

    fun send(sn: String, ts: Long) {
        pool.execute { trySend(sn, ts) }
    }

    private fun trySend(sn: String, ts: Long) {
        val body = "sn=" + enc(sn) + "&ts=" + ts + "&device=" + enc(device)
        var delay = retryBaseDelayMillis
        for (attempt in 1..5) {
            if (postOnce(body)) return
            if (attempt == 1) rediscover() // 첫 실패 = 서버 IP 가 바뀌었을 수 있음
            if (attempt < 5) { Thread.sleep(delay); delay *= 2 }
        }
        Log.w("ScanSender", "drop after 5 retries: $sn")
        onDrop(sn)
    }

    private fun rediscover() {
        val ip = discover() ?: return
        Log.i("ScanSender", "rediscovered server at $ip")
        locator.updateIp(ip)
    }

    private fun postOnce(body: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("${locator.baseUrl}/scan").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2000
                readTimeout = 2000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { os: OutputStream -> os.write(body.toByteArray()) }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun close() { pool.shutdown() }
}
