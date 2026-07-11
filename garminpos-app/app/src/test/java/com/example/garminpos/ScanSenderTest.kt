package com.example.garminpos

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScanSenderTest {

    private class FakeLocator(
        @Volatile var ip: String,
        private val goodPort: Int,
        private val badPort: Int,
    ) : ServerLocator {
        override val port: Int = 0 // 테스트에서는 discover 를 직접 주입하므로 사용 안 함
        override val baseUrl: String
            get() = if (ip == "good") "http://127.0.0.1:$goodPort" else "http://127.0.0.1:$badPort"
        override fun updateIp(ip: String) { this.ip = ip }
    }

    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    @Test fun rediscoversServerAfterSendFailure() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val received = CountDownLatch(1)
        var body: String? = null
        server.createContext("/scan") { ex ->
            body = String(ex.requestBody.readAllBytes())
            ex.sendResponseHeaders(200, -1)
            ex.close()
            received.countDown()
        }
        server.start()
        try {
            val locator = FakeLocator("bad", server.address.port, closedPort())
            val sender = ScanSender(locator, "tablet1",
                retryBaseDelayMillis = 1L, discover = { "good" })
            sender.send("SN123", 42L)
            assertTrue("전송이 재탐색 후 성공해야 함", received.await(5, TimeUnit.SECONDS))
            assertTrue(body!!.contains("sn=SN123"))
            assertEquals("good", locator.ip)
            sender.close()
        } finally {
            server.stop(0)
        }
    }

    @Test fun reportsDropAfterAllRetriesFail() {
        val locator = FakeLocator("bad", closedPort(), closedPort())
        val dropped = CountDownLatch(1)
        var droppedSn: String? = null
        val sender = ScanSender(locator, "tablet1",
            retryBaseDelayMillis = 1L, discover = { null },
            onDrop = { sn -> droppedSn = sn; dropped.countDown() })
        sender.send("LOST1", 42L)
        assertTrue("모든 재시도 실패 시 onDrop 이 호출되어야 함", dropped.await(15, TimeUnit.SECONDS))
        assertEquals("LOST1", droppedSn)
        sender.close()
    }
}
