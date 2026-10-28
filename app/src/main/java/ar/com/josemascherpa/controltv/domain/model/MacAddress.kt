package ar.com.josemascherpa.controltv.domain.model

/** Valida los seis bytes completos; descarta direcciones vacías y de difusión. */
object MacAddress {
    fun normalize(input: String): String? {
        val mac = input.trim().replace('-', ':').uppercase()
        if (!Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}").matches(mac)) return null
        if (mac == "00:00:00:00:00:00" || mac == "FF:FF:FF:FF:FF:FF") return null
        return mac
    }
}
