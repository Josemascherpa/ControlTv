package com.example.controltv.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.controltv.domain.model.KnownTv

// "Mis TVs": las TVs que ya se conectaron alguna vez. Se pueden usar con un toque (aunque estén
// apagadas, si sabemos cómo prenderlas), cambiarles el nombre y quitarlas de la lista.

/** Cómo está una de "Mis TVs", según la última búsqueda. Cada estado dice qué pasa al tocarla. */
internal enum class KnownTvStatus(val label: String) {
    SEARCHING("Buscando…"),
    ON("Prendida · tocá para usarla"),
    OFF("Apagada · tocá para prenderla"),
    NOT_FOUND("No aparece · prendela con el control y tocá acá"),
}

internal fun knownTvStatus(tv: KnownTv, onNetwork: Boolean, scanning: Boolean): KnownTvStatus = when {
    onNetwork -> KnownTvStatus.ON
    scanning -> KnownTvStatus.SEARCHING
    tv.canWakeUp -> KnownTvStatus.OFF
    else -> KnownTvStatus.NOT_FOUND
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
internal fun KnownTvCard(
    tv: KnownTv,
    status: KnownTvStatus,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            Text(tv.device.name, style = MaterialTheme.typography.titleLarge)
            Text(tv.device.brand.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                status.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (status == KnownTvStatus.ON) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CardAction(Icons.Rounded.Edit, "Cambiar nombre", onRename)
                CardAction(Icons.Rounded.Delete, "Quitar", onRemove)
            }
        }
    }
}

// Botón chico con ícono y texto (nunca sólo el ícono, así se entiende qué hace).
@Composable
private fun CardAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
internal fun RenameDialog(tv: KnownTv, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // Arranca con el nombre actual escrito, para que sea fácil corregirlo.
    var name by rememberSaveable { mutableStateOf(tv.device.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Nombre de la TV") },
        text = {
            Column {
                Text("Escribí un nombre para reconocerla fácil, por ejemplo \"TV del living\".")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
internal fun RemoveDialog(tv: KnownTv, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onConfirm) { Text("Quitar") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("¿Quitar ${tv.device.name}?") },
        text = {
            Text(
                "Se borra de \"Mis TVs\". Si la volvés a buscar y conectar, aparece de nuevo.",
                modifier = Modifier.padding(top = 4.dp),
            )
        },
    )
}
