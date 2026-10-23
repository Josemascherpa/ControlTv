package com.example.controltv.domain

import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.session.TvSession
import com.example.controltv.domain.usecase.WebSearchUseCase
import com.example.controltv.domain.web.SearchEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchUseCaseTest {

    private val device = TvDevice("192.168.1.10", "Living", TvBrand.SAMSUNG)
    private val session = TvSession()
    private val history = FakeHistory()
    private val useCase = WebSearchUseCase(session, history)

    @Test
    fun `con navegador abre la busqueda de Google y la guarda en el historial`() = runBlocking {
        val tv = FakeBrowserTv(device).also(session::start)

        useCase.search("  el clima  ", SearchEngine.GOOGLE)

        assertEquals(listOf("https://www.google.com/search?q=el+clima"), tv.openedUrls)
        assertEquals(listOf("el clima"), history.items)
    }

    @Test
    fun `sin navegador usa el buscador propio de la TV`() = runBlocking {
        val tv = FakeSearchTv(device.copy(brand = TvBrand.ROKU)).also(session::start)

        useCase.search("Los Simuladores", SearchEngine.YOUTUBE)

        assertEquals(listOf("Los Simuladores"), tv.searches)
    }

    @Test
    fun `una direccion se abre directo`() = runBlocking {
        val tv = FakeBrowserTv(device).also(session::start)

        useCase.openAddressOrSearch("clarin.com")

        assertEquals(listOf("https://clarin.com"), tv.openedUrls)
    }

    @Test
    fun `abrir una pagina en una TV sin navegador da el error declarado`() = runBlocking {
        FakeTv(device).also(session::start)

        val error = runCatching { useCase.openPage("https://www.google.com") }.exceptionOrNull()

        assertTrue(error is TvError.FeatureNotSupported)
    }

    @Test(expected = TvError.NotConnected::class)
    fun `sin TV conectada avisa que no hay conexion`(): Unit = runBlocking {
        useCase.search("hola", SearchEngine.GOOGLE)
    }
}
