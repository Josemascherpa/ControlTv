package ar.com.josemascherpa.controltv.domain

import ar.com.josemascherpa.controltv.domain.model.FavoriteSite
import org.junit.Assert.*
import org.junit.Test

class FavoriteSiteTest {
    @Test fun `normaliza nombre y agrega https sin alterar rutas`() {
        assertEquals(FavoriteSite("Noticias", "https://example.com/actualidad?q=tv"), FavoriteSite.from(" Noticias ", " example.com/actualidad?q=tv "))
    }

    @Test fun `rechaza direcciones malformadas esquemas y credenciales`() {
        listOf("", "no es una página", "https://", "https://user:password@example.com", "ftp://example.com", "javascript:alert(1)", "file:///etc/passwd").forEach {
            assertNull(it, FavoriteSite.from("Página", it))
        }
        assertNull(FavoriteSite.from(" ", "example.com"))
    }
}
