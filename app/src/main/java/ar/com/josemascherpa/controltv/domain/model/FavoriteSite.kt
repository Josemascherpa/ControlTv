package ar.com.josemascherpa.controltv.domain.model

import java.net.URI

/** Una página guardada. Sólo aceptamos direcciones web completas y sin credenciales. */
data class FavoriteSite(val name: String, val url: String) {
    companion object {
        fun from(name: String, address: String): FavoriteSite? {
            val input = address.trim()
            val url = if ("://" in input) input else "https://$input"
            val uri = try { URI(url) } catch (_: IllegalArgumentException) { return null }
                catch (_: java.net.URISyntaxException) { return null }
            if (name.isBlank() || uri.host.isNullOrBlank() || uri.userInfo != null ||
                uri.scheme.lowercase() !in setOf("http", "https")) return null
            return FavoriteSite(name.trim(), uri.toASCIIString())
        }

        val defaults: List<FavoriteSite> = listOf(
            FavoriteSite("Google", "https://www.google.com"),
            FavoriteSite("YouTube", "https://www.youtube.com"),
            FavoriteSite("Clarín", "https://www.clarin.com"),
            FavoriteSite("La Nación", "https://www.lanacion.com.ar"),
            FavoriteSite("Infobae", "https://www.infobae.com"),
            FavoriteSite("Wikipedia", "https://es.wikipedia.org"),
        )
    }
}
