package com.example.controltv.domain.model

/**
 * Una TV que ya se conectó alguna vez desde este teléfono ("Mis TVs").
 * El nombre es el de [device], que la persona puede cambiar (ej. "TV del living").
 *
 * @param macAddresses las MAC de la TV, para poder prenderla cuando está apagada (Wake-on-LAN).
 */
data class KnownTv(
    val device: TvDevice,
    val macAddresses: List<String> = emptyList(),
) {
    /** Si sabemos su MAC, la podemos intentar prender aunque esté apagada. */
    val canWakeUp: Boolean get() = macAddresses.isNotEmpty()
}
