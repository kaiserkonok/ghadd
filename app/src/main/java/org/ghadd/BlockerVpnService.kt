package org.ghadd

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/** Shared, observable app state (read by the UI). */
object AppState {
    var vpnRunning by mutableStateOf(false)
}

/**
 * A local-only VPN. It claims a fake DNS server address, so every DNS lookup on the phone
 * (browsers, incognito, every app) comes through here. Porn domains get "does not exist";
 * everything else is forwarded to a real DNS server. No traffic is routed through anyone.
 *
 * To stop apps skipping the filter with their own DNS-over-HTTPS / DNS-over-TLS, the well
 * known public DNS addresses are routed into the tunnel and silently dropped, so apps fall
 * back to the system resolver (which is us).
 */
class BlockerVpnService : VpnService() {

    @Volatile
    private var running = false
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val pool = Executors.newFixedThreadPool(6)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) start()
        return START_STICKY
    }

    private fun start() {
        val builder = Builder()
            .setSession("Ghadd")
            .setMtu(1500)
            .setBlocking(true)
            .addAddress(CLIENT_IP, 32)
            .addDnsServer(DNS_IP)
            .addRoute(DNS_IP, 32)
        DROPPED_DNS_IPS.forEach { builder.addRoute(it, 32) }

        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish failed", e)
            null
        }
        if (fd == null) {
            stopSelf()
            return
        }
        tun = fd
        running = true
        AppState.vpnRunning = true

        // Load the blocklist off the main thread (it is large). Queries sent by the phone
        // meanwhile just wait in the tunnel until the loop starts reading.
        worker = Thread({
            DomainFilter.load(this)
            loop(fd)
        }, "dns-filter").also { it.start() }
    }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n <= 0) continue
                handlePacket(buf.copyOf(n), output)
            }
        } catch (e: IOException) {
            if (running) Log.w(TAG, "tunnel read ended", e)
        } finally {
            running = false
            AppState.vpnRunning = false
        }
    }

    private fun handlePacket(pkt: ByteArray, output: FileOutputStream) {
        if (pkt.size < 28) return
        if ((pkt[0].toInt() shr 4) != 4) return                 // IPv4 only
        val ihl = (pkt[0].toInt() and 0x0F) * 4
        if (pkt[9].toInt() != 17) return                         // UDP only
        if (pkt.size < ihl + 8) return

        // Only queries addressed to our fake DNS server get an answer; the rest (the
        // dropped DoH/DoT addresses) is silently discarded.
        val dst = pkt.copyOfRange(16, 20)
        if (!dst.contentEquals(DNS_IP_BYTES)) return
        val dstPort = ((pkt[ihl + 2].toInt() and 0xFF) shl 8) or (pkt[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val srcIp = pkt.copyOfRange(12, 16)
        val srcPort = ((pkt[ihl].toInt() and 0xFF) shl 8) or (pkt[ihl + 1].toInt() and 0xFF)
        val total = ((pkt[2].toInt() and 0xFF) shl 8) or (pkt[3].toInt() and 0xFF)
        val dnsStart = ihl + 8
        val dnsEnd = minOf(total, pkt.size)
        if (dnsEnd <= dnsStart) return
        val query = pkt.copyOfRange(dnsStart, dnsEnd)

        pool.execute {
            try {
                val name = Dns.questionName(query)
                val reply: ByteArray? =
                    if (name != null && DomainFilter.isBlocked(name)) {
                        SiteBlockEvents.onBlocked(this, name)
                        Dns.nxdomain(query)
                    } else {
                        forward(query)
                    }
                if (reply != null) {
                    val out = Dns.udp4(DNS_IP_BYTES, srcIp, 53, srcPort, reply)
                    synchronized(output) { output.write(out) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "query failed", e)
            }
        }
    }

    /** Sends the query to a real DNS server over a socket that bypasses our own tunnel. */
    private fun forward(query: ByteArray): ByteArray? {
        for (server in UPSTREAMS) {
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 3000
                    s.connect(InetSocketAddress(InetAddress.getByName(server), 53))
                    s.send(DatagramPacket(query, query.size))
                    val buf = ByteArray(4096)
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    return buf.copyOf(p.length)
                }
            } catch (_: IOException) {
                // try the next server
            }
        }
        return null
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        running = false
        AppState.vpnRunning = false
        try {
            tun?.close()
        } catch (_: IOException) {
        }
        tun = null
        pool.shutdownNow()
    }

    companion object {
        private const val TAG = "BlockerVpn"
        private const val CLIENT_IP = "10.111.222.2"
        private const val DNS_IP = "10.111.222.1"
        private val DNS_IP_BYTES = byteArrayOf(10, 111, 222.toByte(), 1)

        // Plain resolvers only. Do NOT use "family" resolvers (e.g. 1.1.1.3): they force
        // SafeSearch on Google, which breaks normal searching. Ghadd does its own filtering.
        private val UPSTREAMS = listOf("1.1.1.1", "9.9.9.9", "8.8.8.8")

        // Public resolvers apps could use to bypass us. Packets to these are dropped.
        private val DROPPED_DNS_IPS = listOf(
            "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4",
            "149.112.112.112", "208.67.222.222", "208.67.220.220",
            "94.140.14.14", "94.140.15.15", "185.228.168.9", "185.228.169.9",
            "45.90.28.0", "45.90.30.0", "76.76.2.0", "76.76.10.0"
        )
    }
}
