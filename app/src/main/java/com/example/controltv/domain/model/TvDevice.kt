package com.example.controltv.domain.model

// Una "data class" es una clase pensada para guardar datos: Kotlin le genera
// automáticamente equals(), toString() y copy(). Con "val" es inmutable: no se puede cambiar.
data class TvDevice(
    val ip: String,
    val name: String,
    val brand: TvBrand,
)
