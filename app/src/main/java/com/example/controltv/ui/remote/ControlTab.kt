package com.example.controltv.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.TvFeature
import com.example.controltv.ui.components.BigButton
import com.example.controltv.ui.components.DPad
import com.example.controltv.ui.components.Hint
import com.example.controltv.ui.components.RemoteButton
import com.example.controltv.ui.components.SectionTitle

/** Pestaña "Control": los botones de un control remoto común. */
@Composable
fun ControlTab(
    features: Set<TvFeature>,
    press: (RemoteKey) -> Unit,
    actions: RemoteActions,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RemoteButton("⏻", "Prender/Apagar") { press(RemoteKey.POWER) }
        RemoteButton("⌂", "Inicio") { press(RemoteKey.HOME) }
        RemoteButton("↩", "Atrás") { press(RemoteKey.BACK) }
        RemoteButton("☰", "Menú") { press(RemoteKey.MENU) }
    }

    DPad(press)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RockerControl("Volumen", onUp = { press(RemoteKey.VOLUME_UP) }, onDown = { press(RemoteKey.VOLUME_DOWN) })
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteButton("🔇", "Silencio") { press(RemoteKey.MUTE) }
            RemoteButton("⏯", "Pausa") { press(RemoteKey.PLAY_PAUSE) }
        }
        RockerControl("Canal", onUp = { press(RemoteKey.CHANNEL_UP) }, onDown = { press(RemoteKey.CHANNEL_DOWN) })
    }

    // Sólo mostramos el teclado si esta TV permite escribir.
    if (TvFeature.TEXT in features) {
        HorizontalDivider()
        KeyboardSection(onSendText = actions::sendText, onEnter = actions::sendEnter)
    }
}

// Botones + / − apilados, como el volumen y el canal de un control real.
@Composable
private fun RockerControl(label: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteButton("+", "$label más", showLabel = false, onClick = onUp)
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp))
        RemoteButton("−", "$label menos", showLabel = false, onClick = onDown)
    }
}

@Composable
private fun KeyboardSection(onSendText: (String) -> Unit, onEnter: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        onSendText(text)
        text = ""
    }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Escribir en la TV")
        Hint("Cuando en la TV aparezca un teclado, escribí acá y tocá \"Enviar\".")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Escribí acá…") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Enviar", Modifier.weight(1f), enabled = text.isNotBlank(), onClick = send)
            BigButton("Aceptar ⏎", Modifier.weight(1f), tonal = true, onClick = onEnter)
        }
    }
}
