package com.example.vpnhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MyVpnService : VpnService() {
    companion object {
        const val MTU = 1500
        const val SERVER_PORT = 9999
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var forwarderThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupVpn()
        startForwarder()
        return START_STICKY
    }

    override fun onDestroy() {
        forwarderThread?.interrupt()
        vpnInterface?.close()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val chId = "kingcrimson_vpn"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(chId, "KingCrimson", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("KingCrimson VPN")
            .setContentText("VPN helper running")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
        startForeground(1, n as Notification)
    }

    private fun setupVpn() {
        if (vpnInterface != null) return
        val builder = Builder()
        builder.setMtu(MTU)
        builder.addAddress("10.10.10.2", 32) // local virtual address (doesn't need to route)
        builder.addRoute("0.0.0.0", 0)
        vpnInterface = builder.establish()
    }

    private fun startForwarder() {
        forwarderThread = thread(start = true, name = "tun-forwarder") {
            val pfd = vpnInterface!!.fileDescriptor
            val input = FileInputStream(pfd)
            val output = FileOutputStream(pfd)
            var sock: Socket? = null
            try {
                // Connect to Termux queuer on localhost
                sock = Socket("127.0.0.1", SERVER_PORT)
                val sockOut = sock.getOutputStream()
                val sockIn = sock.getInputStream()

                // Reader Thread: from socket -> TUN
                thread {
                    try {
                        val header = ByteArray(4)
                        while (!Thread.currentThread().isInterrupted) {
                            // Read 4 bytes len
                            var read = 0
                            while (read < 4) {
                                val r = sockIn.read(header, read, 4 - read)
                                if (r < 0) return@thread
                                read += r
                            }
                            val len = ByteBuffer.wrap(header).int
                            val pkt = ByteArray(len)
                            var got = 0
                            while (got < len) {
                                val r = sockIn.read(pkt, got, len - got)
                                if (r < 0) return@thread
                                got += r
                            }
                            output.write(pkt, 0, len)
                        }
                    } catch (e: Exception) {
                        // reconnect logic handled by outer loop
                    }
                }

                // Writer loop: from TUN -> socket (length-prefixed)
                val buffer = ByteArray(32767)
                while (!Thread.currentThread().isInterrupted) {
                    val len = input.read(buffer)
                    if (len > 0) {
                        val header = ByteBuffer.allocate(4).putInt(len).array()
                        sockOut.write(header)
                        sockOut.write(buffer, 0, len)
                        sockOut.flush()
                    } else {
                        Thread.sleep(1)
                    }
                }
            } catch (e: Exception) {
                // reconnect + backoff
                try { sock?.close() } catch (_: Exception) {}
            } finally {
                try { vpnInterface?.close() } catch (_: Exception) {}
            }
        }
    }
}
