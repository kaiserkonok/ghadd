package org.ghadd

/** Minimal DNS + IPv4/UDP packet helpers - just enough for a local DNS filter. */
object Dns {

    /** Returns the offset just past the first question (name + type + class), or -1. */
    private fun questionEnd(dns: ByteArray): Int {
        if (dns.size < 17) return -1
        var o = 12
        while (o < dns.size) {
            val len = dns[o].toInt() and 0xFF
            if (len == 0) {
                o += 1
                break
            }
            if (len and 0xC0 != 0) return -1 // compression pointer: not expected in a query
            o += len + 1
        }
        val end = o + 4
        return if (end <= dns.size) end else -1
    }

    /** Domain name from the first question, e.g. "www.example.com", or null if malformed. */
    fun questionName(dns: ByteArray): String? {
        if (dns.size < 17) return null
        val sb = StringBuilder()
        var o = 12
        while (o < dns.size) {
            val len = dns[o].toInt() and 0xFF
            if (len == 0) return if (sb.isEmpty()) null else sb.toString()
            if (len and 0xC0 != 0 || o + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) sb.append((dns[o + i].toInt() and 0xFF).toChar())
            o += len + 1
        }
        return null
    }

    /** Builds an NXDOMAIN ("this site does not exist") reply for the given query. */
    fun nxdomain(query: ByteArray): ByteArray? {
        val end = questionEnd(query)
        if (end < 0) return null
        val out = query.copyOf(end)
        out[2] = (0x80 or (query[2].toInt() and 0x01)).toByte() // QR=1, keep RD
        out[3] = 0x83.toByte()                                  // RA=1, RCODE=3 (NXDOMAIN)
        out[4] = 0; out[5] = 1                                  // QDCOUNT = 1
        for (i in 6..11) out[i] = 0                             // no answer/authority/extra
        return out
    }

    /** Wraps [payload] in an IPv4 + UDP packet. */
    fun udp4(src: ByteArray, dst: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[6] = 0x40                      // Don't Fragment
        p[8] = 64                        // TTL
        p[9] = 17                        // UDP
        System.arraycopy(src, 0, p, 12, 4)
        System.arraycopy(dst, 0, p, 16, 4)
        val sum = checksum(p, 0, 20)
        p[10] = (sum shr 8).toByte(); p[11] = sum.toByte()

        p[20] = (srcPort shr 8).toByte(); p[21] = srcPort.toByte()
        p[22] = (dstPort shr 8).toByte(); p[23] = dstPort.toByte()
        val udpLen = 8 + payload.size
        p[24] = (udpLen shr 8).toByte(); p[25] = udpLen.toByte()
        // UDP checksum left at 0 = "not computed", which is valid for IPv4.
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    private fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len) {
            sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
