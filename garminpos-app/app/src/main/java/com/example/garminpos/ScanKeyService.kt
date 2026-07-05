package com.example.garminpos

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ScanKeyService : AccessibilityService() {
    private val TAG = "SNCAP"
    private lateinit var assembler: ScanAssembler
    private lateinit var debouncer: Debouncer
    private var sender: ScanSender? = null
    // SharedPreferences 는 리스너를 약한 참조로만 들고 있으므로 필드로 강하게 잡아둬야 함
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyPrefs() }

    override fun onServiceConnected() {
        applyPrefs()
        Prefs(this).registerOnChangeListener(prefListener)
    }

    private fun applyPrefs() {
        val p = Prefs(this)
        assembler = ScanAssembler(p.burstGapMillis)
        debouncer = Debouncer(p.debounceMillis)
        sender?.close()
        sender = ScanSender(p.baseUrl, p.device)
        Log.d(TAG, "applyPrefs baseUrl=${p.baseUrl} device=${p.device} burst=${p.burstGapMillis} debounce=${p.debounceMillis}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyEvent action=${event.action} keyCode=${event.keyCode} unicode=${event.unicodeChar} dev=${event.device?.name}")
        if (event.action == KeyEvent.ACTION_DOWN) {
            val c: Char = when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> '\n'
                else -> {
                    val u = event.unicodeChar
                    if (u != 0) u.toChar() else return false
                }
            }
            val sn = assembler.onChar(c, System.currentTimeMillis())
            if (sn != null) {
                Log.d(TAG, "assembled SN=$sn")
                if (debouncer.shouldSend(sn, System.currentTimeMillis())) {
                    Log.d(TAG, "SEND $sn")
                    sender?.send(sn, System.currentTimeMillis())
                } else {
                    Log.d(TAG, "debounced (suppressed) $sn")
                }
            }
        }
        return false // 항상 통과 → POS 정상 입력
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        Prefs(this).unregisterOnChangeListener(prefListener)
        sender?.close()
        super.onDestroy()
    }
}
