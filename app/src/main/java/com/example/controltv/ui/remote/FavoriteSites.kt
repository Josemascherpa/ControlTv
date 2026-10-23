package com.example.controltv.ui.remote

/** Una página que se abre con un solo toque. */
data class FavoriteSite(val name: String, val url: String)

// Para cambiar los favoritos alcanza con editar esta lista.
val FAVORITE_SITES: List<FavoriteSite> = listOf(
    FavoriteSite("Google", "https://www.google.com"),
    FavoriteSite("YouTube", "https://www.youtube.com"),
    FavoriteSite("Clarín", "https://www.clarin.com"),
    FavoriteSite("La Nación", "https://www.lanacion.com.ar"),
    FavoriteSite("Infobae", "https://www.infobae.com"),
    FavoriteSite("Wikipedia", "https://es.wikipedia.org"),
)
