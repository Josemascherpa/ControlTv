package ar.com.josemascherpa.controltv.domain.model

// Un "enum" es un tipo con un conjunto fijo de valores posibles.
// Cada marca usa un protocolo distinto; la capa "data" decide qué controlador crear para cada una.
enum class TvBrand(val label: String, val pairing: PairingMethod) {
    SAMSUNG("Samsung", PairingMethod.ACCEPT_ON_TV),
    LG("LG", PairingMethod.ACCEPT_ON_TV),
    GOOGLE_TV("Google TV / Android TV", PairingMethod.CODE_ON_TV),

    // Por ADB (modo desarrollador): Fire TV, y Android TV viejas que no tienen el control de Google TV.
    ANDROID("Fire TV / Android TV", PairingMethod.ALLOW_DEBUGGING),
    ROKU("Roku", PairingMethod.ENABLE_MOBILE_CONTROL),
    SONY("Sony Bravia", PairingMethod.PRE_SHARED_KEY),
}

/**
 * Qué tiene que hacer la persona la primera vez para que la TV acepte el teléfono.
 * La UI lo usa para mostrar la ayuda correcta sin tener que conocer cada marca.
 */
enum class PairingMethod {
    /** La TV muestra un mensaje "¿Permitir?" y hay que aceptarlo con el control (Samsung, LG). */
    ACCEPT_ON_TV,

    /** La TV muestra un código y hay que escribirlo en el teléfono (Google TV). */
    CODE_ON_TV,

    /** Hay que activar la depuración en la TV y aceptar "¿Permitir depuración?" (Android TV). */
    ALLOW_DEBUGGING,

    /** Hay que activar "Control por apps móviles" en los ajustes de la TV (Roku). */
    ENABLE_MOBILE_CONTROL,

    /** Hay que inventar una clave en la TV y escribirla en el teléfono (Sony). */
    PRE_SHARED_KEY,
}
