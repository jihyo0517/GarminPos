package com.example.garminpos

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) : ServerLocator {
    private val sp = context.getSharedPreferences("garminpos", Context.MODE_PRIVATE)

    fun registerOnChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterOnChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    var laptopIp: String
        get() = sp.getString("ip", "192.168.1.5") ?: "192.168.1.5"
        set(v) = sp.edit().putString("ip", v).apply()

    override var port: Int
        get() = sp.getInt("port", 8000)
        set(v) = sp.edit().putInt("port", v).apply()

    override fun updateIp(ip: String) { laptopIp = ip }

    var device: String
        get() = sp.getString("device", "tablet1") ?: "tablet1"
        set(v) = sp.edit().putString("device", v).apply()

    var debounceMillis: Long
        get() = sp.getLong("debounce", 2000L)
        set(v) = sp.edit().putLong("debounce", v).apply()

    var burstGapMillis: Long
        get() = sp.getLong("burst", 100L)
        set(v) = sp.edit().putLong("burst", v).apply()

    override val baseUrl: String get() = "http://$laptopIp:$port"
}
