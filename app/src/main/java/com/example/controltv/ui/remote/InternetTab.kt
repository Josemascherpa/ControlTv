package com.example.controltv.ui.remote

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvFeature
import com.example.controltv.domain.web.SearchEngine
import com.example.controltv.domain.web.WebLinks
import com.example.controltv.ui.components.BigButton
import com.example.controltv.ui.components.DPad
import com.example.controltv.ui.components.Hint
import com.example.controltv.ui.components.SectionTitle

/**
 * Pestaña "Internet": buscar, abrir páginas y moverse en ellas.
 * Lo que se muestra depende de lo que pueda la TV ([features]), no de la marca.
 */
@Composable
fun InternetTab(
    features: Set<TvFeature>,
    recentSearches: List<String>,
    press: (RemoteKey) -> Unit,
    actions: RemoteActions,
) {
    val canBrowse = TvFeature.BROWSER in features
    val canSearchContent = TvFeature.CONTENT_SEARCH in features

    if (!canBrowse && !canSearchContent) {
        Hint("Esta TV no permite buscar ni abrir páginas desde el teléfono.", Modifier.fillMaxWidth())
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    val hasQuery = query.isNotBlank()
    // Si lo que escribió parece una dirección (ej. clarin.com), ofrecemos abrirla directamente.
    val showOpenPage = canBrowse && WebLinks.looksLikeAddress(query)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(if (canBrowse) "¿Qué querés buscar?" else "Buscar películas y series")
        Hint(
            if (canBrowse) "Escribí o tocá el micrófono y hablá."
            else "Esta TV no tiene navegador de internet, pero podés buscar en sus aplicaciones.",
        )
        SearchField(query, onQueryChange = { query = it }, onGo = {
            if (showOpenPage) actions.openWebsite(query) else actions.search(query, SearchEngine.GOOGLE)
        })

        if (canBrowse) {
            if (showOpenPage) {
                BigButton("Abrir esta página", icon = Icons.Rounded.Language, enabled = hasQuery) { actions.openWebsite(query) }
            }
            BigButton("Buscar en Google", icon = Icons.Rounded.Search, enabled = hasQuery, tonal = showOpenPage) {
                actions.search(query, SearchEngine.GOOGLE)
            }
            BigButton("Buscar en YouTube", icon = Icons.Rounded.SmartDisplay, enabled = hasQuery, tonal = true) {
                actions.search(query, SearchEngine.YOUTUBE)
            }
        } else {
            // Sin navegador, el caso de uso usa el buscador propio de la TV (el motor da igual).
            BigButton("Buscar en la TV", icon = Icons.Rounded.Search, enabled = hasQuery) { actions.search(query, SearchEngine.GOOGLE) }
        }
    }

    if (recentSearches.isNotEmpty()) {
        RecentSearches(recentSearches, onPick = { query = it })
    }

    if (canBrowse) {
        Favorites(onOpen = actions::openFavorite)
        HorizontalDivider()
        PageNavigation(press, onScroll = actions::scroll)
    }
}

/** Campo de búsqueda con un botón de micrófono para dictar en vez de escribir. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onGo: () -> Unit) {
    val context = LocalContext.current
    // Abre el dictado por voz de Google en el teléfono y nos devuelve lo que entendió.
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onQueryChange)
        }
    }
    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Decí qué querés buscar")
        try {
            voice.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Este teléfono no tiene dictado por voz", Toast.LENGTH_LONG).show()
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Ej: el clima de mañana") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        trailingIcon = {
            IconButton(onClick = startVoice, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = "Buscar hablando", tint = MaterialTheme.colorScheme.primary)
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onGo() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecentSearches(searches: List<String>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("Últimas búsquedas")
        Hint("Tocá una para volver a escribirla.")
        searches.forEach { recent ->
            OutlinedButton(onClick = { onPick(recent) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    recent,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Favorites(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Páginas favoritas")
        // chunked(2) arma pares para dibujar la lista en dos columnas.
        FAVORITE_SITES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { site ->
                    BigButton(site.name, Modifier.weight(1f), tonal = true) { onOpen(site.url) }
                }
            }
        }
    }
}

// Para leer una página en la TV: subir/bajar, flechas para elegir un link y OK para entrar.
@Composable
private fun PageNavigation(press: (RemoteKey) -> Unit, onScroll: (ScrollDirection) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionTitle("Moverse en la página", Modifier.fillMaxWidth())
        Hint("Usá las flechas para elegir un link y OK para abrirlo.", Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Subir", Modifier.weight(1f), icon = Icons.Rounded.KeyboardArrowUp, tonal = true) { onScroll(ScrollDirection.UP) }
            BigButton("Bajar", Modifier.weight(1f), icon = Icons.Rounded.KeyboardArrowDown, tonal = true) { onScroll(ScrollDirection.DOWN) }
        }
        Spacer(Modifier.height(16.dp))
        DPad(press)
        Spacer(Modifier.height(16.dp))
        BigButton("Volver a la página anterior", icon = Icons.AutoMirrored.Rounded.ArrowBack, tonal = true) { press(RemoteKey.BACK) }
    }
}
