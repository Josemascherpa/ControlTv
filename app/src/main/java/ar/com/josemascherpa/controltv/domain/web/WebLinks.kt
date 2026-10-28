package ar.com.josemascherpa.controltv.domain.web

import java.net.URLEncoder

enum class SearchEngine { GOOGLE, YOUTUBE }

/** Arma direcciones web a partir de lo que escribe la persona. Kotlin puro, fácil de testear. */
object WebLinks {

    fun search(engine: SearchEngine, query: String): String {
        val encoded = encode(query.trim())
        return when (engine) {
            SearchEngine.GOOGLE -> "https://www.google.com/search?q=$encoded"
            SearchEngine.YOUTUBE -> "https://www.youtube.com/results?search_query=$encoded"
        }
    }

    /** Parece una dirección (ej. "clarin.com"): tiene un punto y no tiene espacios. */
    fun looksLikeAddress(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.contains('.') && !trimmed.contains(' ')
    }

    /** Si el texto es una dirección web devuelve la URL completa; si no, null (hay que buscarlo). */
    fun toUrlOrNull(input: String): String? {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            looksLikeAddress(trimmed) -> "https://$trimmed"
            else -> null
        }
    }

    // Convierte espacios, acentos y símbolos a un formato válido dentro de una URL.
    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
