package com.example.controltv.domain.model

/**
 * Lo que encontró una búsqueda de TVs.
 * @param network la red que se recorrió, ej. "192.168.100." (o null si el teléfono no está en ningún WiFi).
 *   Se la mostramos a la persona cuando no aparece nada: ayuda a darse cuenta si el teléfono está en otra red.
 */
data class ScanResult(val network: String?, val devices: List<TvDevice>)
