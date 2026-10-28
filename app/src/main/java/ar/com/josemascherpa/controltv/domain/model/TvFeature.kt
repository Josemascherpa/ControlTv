package ar.com.josemascherpa.controltv.domain.model

/** Funciones extra que no todas las TVs tienen (los botones básicos los tienen todas). */
enum class TvFeature {
    /** Escribir texto en el teclado que aparece en la TV. */
    TEXT,

    /** Abrir páginas de internet y moverse por ellas. */
    BROWSER,

    /** Buscar películas y series en las aplicaciones de la TV (cuando no hay navegador, ej. Roku). */
    CONTENT_SEARCH,

    /** Mover un cursor (como un mouse) por la pantalla y hacer click. */
    POINTER,
}
