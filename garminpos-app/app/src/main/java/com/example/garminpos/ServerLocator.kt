package com.example.garminpos

/** SN 수신 서버의 현재 위치. IP 가 DHCP 로 바뀌면 updateIp 로 갱신된다. */
interface ServerLocator {
    val baseUrl: String
    val port: Int
    fun updateIp(ip: String)
}
