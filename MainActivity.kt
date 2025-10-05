package com.example.vpnhelper

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val btn = Button(this).apply { text = "Start VPN Helper" }
        setContentView(btn)
        btn.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 1001)
            } else {
                startVpnService()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1001 && resultCode == RESULT_OK) startVpnService()
    }

    private fun startVpnService() {
        startForegroundService(Intent(this, MyVpnService::class.java))
        Toast.makeText(this, "VPN service starting — allow system prompt", Toast.LENGTH_LONG).show()
    }
}
