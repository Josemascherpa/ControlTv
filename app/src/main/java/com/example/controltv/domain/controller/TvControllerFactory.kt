package com.example.controltv.domain.controller

import com.example.controltv.domain.model.TvDevice

/**
 * Crea el controlador correcto para una TV. La lógica sólo conoce esta interfaz;
 * la implementación (que sí sabe de cada marca) vive en la capa "data".
 */
fun interface TvControllerFactory {
    fun create(device: TvDevice): TvController
}
