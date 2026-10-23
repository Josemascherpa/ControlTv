package com.example.controltv.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.ui.messages.connectingHint

/**
 * Pantalla inicial: busca TVs en la red y deja elegir una (o escribir la IP a mano).
 *
 * Fijate que no recibe el ViewModel: recibe el estado y funciones ("callbacks").
 * Así la pantalla sólo dibuja, es más fácil de entender y se puede previsualizar.
 */
@Composable
fun DiscoveryScreen(
    state: DiscoveryUiState,
    onScan: () -> Unit,
    onConnect: (device: TvDevice, key: String?) -> Unit,
    onConnectManual: (String) -> Unit,
    onCancelKey: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onCancelCode: () -> Unit,
    onShowSetupGuide: () -> Unit,
    onConfirmSetupGuide: () -> Unit,
    onDismissSetupGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable guarda el texto aunque la pantalla se recree (por ejemplo al rotar).
    var manualIp by rememberSaveable { mutableStateOf("") }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Conectá tu TV", style = MaterialTheme.typography.headlineMedium)
        Text(
            "El teléfono y la TV tienen que estar en la misma red WiFi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        state.lastDevice?.let { last ->
            Button(onClick = { onConnect(last, null) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text("Reconectar a ${last.name}", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onScan,
            enabled = !state.scanning,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (state.scanning) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Buscando TVs…", style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Buscar TVs", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(12.dp))

        // LazyColumn es una lista que sólo dibuja los elementos visibles (como RecyclerView).
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.devices, key = { it.ip }) { device ->
                DeviceCard(device, onClick = { onConnect(device, null) })
            }
            // Con la depuración apagada estas TVs no aparecen: dejamos la ayuda siempre a mano.
            item {
                TextButton(onClick = onShowSetupGuide, modifier = Modifier.fillMaxWidth()) {
                    Text("¿Tenés Google TV o Fire TV y no aparece?", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Text("¿No aparece? Escribí la IP de la TV:", style = MaterialTheme.typography.labelLarge)
        Text(
            "La encontrás en la TV en Ajustes > Red > Estado de la red.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            Button(onClick = { onConnectManual(manualIp) }, enabled = manualIp.isNotBlank()) {
                Text("Conectar")
            }
        }
    }

    state.connecting?.let { ConnectingDialog(it) }
    state.keyRequest?.let { device ->
        KeyDialog(device, onConfirm = { key -> onConnect(device, key) }, onDismiss = onCancelKey)
    }
    state.codeRequest?.let { request ->
        PairingCodeDialog(request, onConfirm = onSubmitCode, onDismiss = onCancelCode)
    }
    state.setupGuide?.let { request ->
        DebuggingSetupGuide(request, onConfirm = onConfirmSetupGuide, onDismiss = onDismissSetupGuide)
    }
}

@Composable
private fun DeviceCard(device: TvDevice, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${device.brand.label} · ${device.ip}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectingDialog(device: TvDevice) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Conectando con ${device.name}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(device.brand.pairing.connectingHint())
            }
        },
    )
}

// Para las TVs que no muestran un cartel de "¿Permitir?" (Sony): hay que inventar una clave
// en la TV y escribirla acá.
@Composable
private fun KeyDialog(device: TvDevice, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(key) }, enabled = key.isNotBlank()) { Text("Conectar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Clave de ${device.name}") },
        text = {
            Column {
                Text(
                    "En la TV andá a Ajustes > Red > Configuración de red doméstica > Control IP. " +
                        "Poné Autenticación en \"Normal y clave precompartida\", elegí una clave " +
                        "(por ejemplo 1234) y escribila acá.",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = { Text("1234") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

// Google TV: la TV muestra un código y la persona lo escribe acá (como al emparejar un parlante).
@Composable
private fun PairingCodeDialog(request: CodeRequest, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(code) }, enabled = code.length == CODE_LENGTH && !request.checking) {
                Text("Conectar")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Escribí el código de la TV") },
        text = {
            Column {
                Text(
                    "En la pantalla de ${request.device.name} apareció un código de 6 números y letras " +
                        "(por ejemplo 4A21F0). Escribilo acá.",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    // Sólo dejamos los caracteres que puede tener el código, y en mayúscula.
                    onValueChange = { typed ->
                        code = typed.uppercase().filter { it in HEX_CHARS }.take(CODE_LENGTH)
                    },
                    placeholder = { Text("4A21F0") },
                    singleLine = true,
                    isError = request.wrongCode,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (request.wrongCode) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "El código no coincide. Fijate bien en la TV y escribilo de nuevo.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (request.checking) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            }
        },
    )
}

private const val CODE_LENGTH = 6
private const val HEX_CHARS = "0123456789ABCDEF"
