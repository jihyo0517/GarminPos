package com.example.garminpos

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * SN 수신기(노트북)를 UDP 브로드캐스트로 찾는다.
 * 요청 "SN_DISCOVER_V1" → 수신기가 "SN_SERVER_V1" 로 응답하면 그 발신 IP 가 서버.
 * 노트북 IP 가 DHCP 로 바뀌어도 태블릿이 스스로 다시 찾을 수 있게 한다.
 */
object Discovery {
    const val REQUEST = "SN_DISCOVER_V1"
    const val RESPONSE = "SN_SERVER_V1"

    fun discover(port: Int, timeoutMs: Int = 1000, target: InetAddress = broadcastAddress()): String? {
        return try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = timeoutMs
                val req = REQUEST.toByteArray()
                sock.send(DatagramPacket(req, req.size, target, port))
                val buf = ByteArray(64)
                val resp = DatagramPacket(buf, buf.size)
                sock.receive(resp)
                val msg = String(resp.data, 0, resp.length).trim()
                if (msg.startsWith(RESPONSE)) resp.address.hostAddress else null
            }
        } catch (e: Exception) {
            null // 타임아웃 포함 — 못 찾으면 기존 IP 유지
        }
    }

    private fun broadcastAddress(): InetAddress = InetAddress.getByName("255.255.255.255")
}
