package com.example.controltv.domain.model

// Los botones del control remoto, independientes de la marca.
// Cada controlador traduce estos valores al código que entiende su TV.
enum class RemoteKey {
    POWER, HOME, BACK, MENU,
    UP, DOWN, LEFT, RIGHT, OK,
    VOLUME_UP, VOLUME_DOWN, MUTE,
    CHANNEL_UP, CHANNEL_DOWN,
    PLAY_PAUSE,
}

/** Para qué lado mover una página de internet. Cada dirección sabe qué flecha le corresponde. */
enum class ScrollDirection(val arrow: RemoteKey) {
    UP(RemoteKey.UP),
    DOWN(RemoteKey.DOWN),
}
