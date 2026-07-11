package com.example.garminpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class DiscoveryTest {
    @Test fun findsServerViaUdp() {
        val responder = DatagramSocket(0, InetAddress.getLoopbackAddress())
        try {
            thread(isDaemon = true) {
                val buf = ByteArray(64)
                val p = DatagramPacket(buf, buf.size)
                responder.receive(p)
                if (String(p.data, 0, p.length) == "SN_DISCOVER_V1") {
                    val r = "SN_SERVER_V1".toByteArray()
                    responder.send(DatagramPacket(r, r.size, p.address, p.port))
                }
            }
            val ip = Discovery.discover(responder.localPort, 2000, InetAddress.getLoopbackAddress())
            assertEquals("127.0.0.1", ip)
        } finally {
            responder.close()
        }
    }

    @Test fun returnsNullOnTimeout() {
        // 아무도 응답하지 않는 포트 → null
        val silent = DatagramSocket(0, InetAddress.getLoopbackAddress())
        try {
            val ip = Discovery.discover(silent.localPort, 200, InetAddress.getLoopbackAddress())
            assertNull(ip)
        } finally {
            silent.close()
        }
    }
}
