package ar.com.josemascherpa.controltv.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.josemascherpa.controltv.domain.model.FavoriteSite
import ar.com.josemascherpa.controltv.ui.components.BigButton
import ar.com.josemascherpa.controltv.ui.components.SectionTitle

/** La edición se abre aparte para que los accesos de todos los días sigan siendo simples. */
@Composable
internal fun Favorites(sites: List<FavoriteSite>, actions: RemoteActions) {
    var managing by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var originalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Páginas favoritas")
        sites.forEach { site ->
            BigButton(site.name, tonal = true) { actions.openFavorite(site.url) }
        }
        TextButton(onClick = { managing = true }) { Text("Editar páginas favoritas") }
    }
    if (managing) {
        AlertDialog(
            onDismissRequest = { managing = false },
            title = { Text("Tus páginas favoritas") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sites.forEachIndexed { index, site ->
                        Text(site.name)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                originalUrl = site.url
                                name = site.name
                                address = site.url
                                editing = true
                            }) { Text("Editar") }
                            TextButton(onClick = { actions.removeFavorite(site.url) }) { Text("Quitar") }
                        }
                        Row {
                            TextButton(enabled = index > 0, onClick = { actions.moveFavorite(site.url, -1) }) { Text("Subir") }
                            TextButton(enabled = index < sites.lastIndex, onClick = { actions.moveFavorite(site.url, 1) }) { Text("Bajar") }
                        }
                    }
                    Button(onClick = {
                        originalUrl = null
                        name = ""
                        address = ""
                        editing = true
                    }) { Text("Agregar página") }
                }
            },
            confirmButton = { TextButton(onClick = { managing = false }) { Text("Listo") } },
        )
    }
    if (editing) {
        val valid = FavoriteSite.from(name, address) != null
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(if (originalUrl == null) "Agregar página" else "Editar página") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                    OutlinedTextField(address, { address = it }, label = { Text("Dirección, por ejemplo wikipedia.org") }, singleLine = true)
                    if (address.isNotBlank() && !valid) Text("Escribí un nombre y una dirección web válida (http o https).")
                }
            },
            confirmButton = {
                Button(enabled = valid, onClick = {
                    actions.saveFavorite(originalUrl, name, address)
                    editing = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
        )
    }
}
