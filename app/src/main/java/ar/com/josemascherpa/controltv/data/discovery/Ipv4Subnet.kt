package ar.com.josemascherpa.controltv.data.discovery

/** Usa la máscara real para la difusión. El barrido se limita a 1022 vecinos en redes enormes. */
internal class Ipv4Subnet(address: String, private val prefixLength: Int) {
    private val value = address.split('.').fold(0L) { result, byte -> (result shl 8) or byte.toLong() }
    private fun mask(prefix: Int): Long = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    private fun format(ip: Long): String = (3 downTo 0).joinToString(".") { ((ip shr (it * 8)) and 255).toString() }

    init { require(prefixLength in 1..32) }

    fun broadcast(): String = format((value and mask(prefixLength)) or (mask(prefixLength) xor 0xFFFFFFFFL))

    fun hosts(): List<String> {
        val prefix = prefixLength.coerceAtLeast(22)
        val network = value and mask(prefix)
        val end = network or (mask(prefix) xor 0xFFFFFFFFL)
        return ((network + 1) until end).filterNot { it == value }.map(::format)
    }
}
