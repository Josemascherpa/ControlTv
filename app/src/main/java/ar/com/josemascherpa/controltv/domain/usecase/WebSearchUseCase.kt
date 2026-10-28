package ar.com.josemascherpa.controltv.domain.usecase

import ar.com.josemascherpa.controltv.domain.controller.ContentSearch
import ar.com.josemascherpa.controltv.domain.controller.WebBrowser
import ar.com.josemascherpa.controltv.domain.controller.asWebBrowser
import ar.com.josemascherpa.controltv.domain.error.TvError
import ar.com.josemascherpa.controltv.domain.model.TvFeature
import ar.com.josemascherpa.controltv.domain.repository.SearchHistory
import ar.com.josemascherpa.controltv.domain.session.TvSession
import ar.com.josemascherpa.controltv.domain.web.SearchEngine
import ar.com.josemascherpa.controltv.domain.web.WebLinks

/**
 * Buscar o abrir páginas en la TV, guardando lo buscado en el historial.
 * Decide qué hacer según lo que pueda la TV: si tiene navegador abre la página, y si no,
 * usa el buscador propio de la TV (ej. Roku).
 */
class WebSearchUseCase(
    private val session: TvSession,
    private val history: SearchHistory,
) {
    suspend fun search(query: String, engine: SearchEngine) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        // "when" con "is" pregunta de qué tipo es; adentro de cada rama Kotlin ya lo trata como ese tipo.
        when (val tv = session.current()) {
            is WebBrowser -> tv.openUrl(WebLinks.search(engine, trimmed))
            is ContentSearch -> tv.search(trimmed)
            else -> throw TvError.FeatureNotSupported(TvFeature.BROWSER)
        }
        history.add(trimmed)
    }

    /** Si lo escrito es una dirección (ej. "clarin.com") la abre; si no, lo busca en Google. */
    suspend fun openAddressOrSearch(input: String) {
        val url = WebLinks.toUrlOrNull(input) ?: return search(input, SearchEngine.GOOGLE)
        session.current().asWebBrowser().openUrl(url)
        history.add(input.trim())
    }

    /** Abre una página favorita. No se guarda en el historial: ya está a un toque. */
    suspend fun openPage(url: String) {
        session.current().asWebBrowser().openUrl(url)
    }
}
