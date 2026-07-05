package com.example.garminpos

import android.util.Log
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class ScanSender(private val baseUrl: String, private val device: String) {
    private val pool = Executors.newSingleThreadExecutor()

    fun send(sn: String, ts: Long) {
        pool.execute { trySend(sn, ts) }
    }

    private fun trySend(sn: String, ts: Long) {
        val body = "sn=" + enc(sn) + "&ts=" + ts + "&device=" + enc(device)
        var delay = 500L
        for (attempt in 1..5) {
            if (postOnce(body)) return
            if (attempt < 5) { Thread.sleep(delay); delay *= 2 }
        }
        Log.w("ScanSender", "drop after 5 retries: $sn")
    }

    private fun postOnce(body: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$baseUrl/scan").openConnection() as HttpURLConnection).apply {
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
