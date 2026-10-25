package com.example.controltv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.TvFeature
import com.example.controltv.ui.components.BigButton
import com.example.controltv.ui.components.DPad
import com.example.controltv.ui.components.Hint
import com.example.controltv.ui.components.RemoteButton

/** Pestaña "Control": los botones de un control remoto común y el botón para escribir. */
@Composable
fun ControlTab(
    features: Set<TvFeature>,
    press: (RemoteKey) -> Unit,
    actions: RemoteActions,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RemoteButton(Icons.Rounded.PowerSettingsNew, "Prender/Apagar") { press(RemoteKey.POWER) }
        RemoteButton(Icons.Rounded.Home, "Inicio") { press(RemoteKey.HOME) }
        RemoteButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") { press(RemoteKey.BACK) }
        RemoteButton(Icons.Rounded.Menu, "Menú") { press(RemoteKey.MENU) }
    }

    DPad(press)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RockerControl(
            "Volumen", Icons.Rounded.Add, Icons.Rounded.Remove,
            onUp = { press(RemoteKey.VOLUME_UP) }, onDown = { press(RemoteKey.VOLUME_DOWN) },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RemoteButton(Icons.AutoMirrored.Rounded.VolumeOff, "Silencio") { press(RemoteKey.MUTE) }
            RemoteButton(Icons.Rounded.PlayArrow, "Pausa / Play") { press(RemoteKey.PLAY_PAUSE) }
        }
        RockerControl(
            "Canal", Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown,
            onUp = { press(RemoteKey.CHANNEL_UP) }, onDown = { press(RemoteKey.CHANNEL_DOWN) },
        )
    }

    // Sólo mostramos el botón para escribir si esta TV lo permite.
    if (TvFeature.TEXT in features) {
        var showKeyboard by rememberSaveable { mutableStateOf(false) }
        BigButton("Escribir en la TV", icon = Icons.Rounded.Keyboard, tonal = true) { showKeyboard = true }
        if (showKeyboard) {
            KeyboardDialog(
                onSendText = actions::sendText,
                onEnter = actions::sendEnter,
                onClose = { showKeyboard = false },
            )
        }
    }
}

// Botones + / − dentro de una "píldora", como el volumen y el canal de un control real.
@Composable
private fun RockerControl(label: String, up: ImageVector, down: ImageVector, onUp: () -> Unit, onDown: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RockerButton(up, "$label más", onUp)
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
        RockerButton(down, "$label menos", onDown)
    }
}

@Composable
private fun RockerButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(60.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(30.dp),
        )
    }
}

/**
 * Ventana para escribir en la TV. Queda abierta después de enviar, así se puede mandar
 * el texto y después tocar "Aceptar" sin volver a abrirla.
 */
@Composable
private fun KeyboardDialog(onSendText: (String) -> Unit, onEnter: () -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        onSendText(text)
        text = ""
    }
    // FocusRequester sirve para pedir que el cursor aparezca solo en el campo (y se abra el teclado).
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Escribir en la TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint("Escribí acá y tocá \"Enviar\". Después tocá \"Aceptar\" para confirmar en la TV.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Escribí acá…") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Enviar", Modifier.weight(1f), enabled = text.isNotBlank(), onClick = send)
                    BigButton("Aceptar ⏎", Modifier.weight(1f), tonal = true, onClick = onEnter)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}
