package ar.com.josemascherpa.controltv.domain.controller

import ar.com.josemascherpa.controltv.domain.model.TvDevice

/**
 * Crea el controlador correcto para una TV. La lógica sólo conoce esta interfaz;
 * la implementación (que sí sabe de cada marca) vive en la capa "data".
 */
fun interface TvControllerFactory {
    fun create(device: TvDevice): TvController
}
