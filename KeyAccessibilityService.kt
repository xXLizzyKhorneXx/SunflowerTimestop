package com.example.vpnhelper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class KeyAccessibilityService : AccessibilityService() {
    private val tag = "KeyAccService"
    private val keyState = ConcurrentHashMap<Int, Long>() // last-pressed timestamp
    private val SIMULTANEOUS_WINDOW_MS = 250L
    private val controlPort = MyVpnService.SERVER_PORT
    private var ctrlSocket: Socket? = null
    private var ctrlOut: OutputStream? = null
    private val handler = Handler()

    // configurable keys (change via shared prefs or hardcode)
    // set these to your two right-controller keycodes
    private val keyA = 96   // example: KEYCODE_BUTTON_A (replace)
    private val keyB = 97   // example: KEYCODE_BUTTON_B (replace)

    override fun onServiceConnected() {
        super.onServiceConnected()
        requestKeyEvents(true)
        connectControlSocket()
        Log.i(tag, "Accessibility service connected")
    }

    private fun connectControlSocket() {
        Thread {
            try {
                ctrlSocket = Socket("127.0.0.1", controlPort)
                ctrlOut = ctrlSocket!!.getOutputStream()
            } catch (e: Exception) {
                // ignore; will try again when sending
            }
        }.start()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            keyState[code] = now

            // check simultaneous detection
            if (isSimultaneous(code, keyA, keyB, now)) {
                sendToggle()
            }
        }
        return super.onKeyEvent(event)
    }

    private fun isSimultaneous(code: Int, a: Int, b: Int, now: Long): Boolean {
        val other = if (code == a) b else if (code == b) a else -1
        if (other == -1) return false
        val t = keyState[other] ?: return false
        return Math.abs(now - t) <= SIMULTANEOUS_WINDOW_MS
    }

    private fun sendToggle() {
        Thread {
            try {
                if (ctrlOut == null) connectControlSocket()
                ctrlOut?.write("CMD:TOGGLE\n".toByteArray())
                ctrlOut?.flush()
            } catch (e: Exception) {
                // try reconnect on next press
                connectControlSocket()
            }
        }.start()
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
}
