package com.example.controltv.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.TvDevice

/**
 * El control remoto. Tiene dos pestañas para no amontonar todo en una sola pantalla:
 *   - Control: los botones de siempre (encender, flechas, volumen…) y escribir en la TV.
 *   - Internet: buscar en Google/YouTube (escribiendo o hablando), páginas favoritas y
 *     botones para moverse dentro de la página.
 */
@Composable
fun RemoteScreen(
    device: TvDevice,
    state: RemoteUiState,
    actions: RemoteActions,
    modifier: Modifier = Modifier,
) {
    // Vibración corta al tocar un botón, para que se sienta como un control físico.
    val haptics = LocalHapticFeedback.current
    val press: (RemoteKey) -> Unit = { key ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        actions.press(key)
    }
    var tab by rememberSaveable { mutableIntStateOf(TAB_CONTROL) }

    Column(modifier.fillMaxSize()) {
        Header(device, onDisconnect = actions::disconnect)
        PrimaryTabRow(selectedTabIndex = tab) {
            BigTab("🎮  Control", selected = tab == TAB_CONTROL) { tab = TAB_CONTROL }
            BigTab("🌐  Internet", selected = tab == TAB_INTERNET) { tab = TAB_INTERNET }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (tab) {
                TAB_CONTROL -> ControlTab(state.features, press, actions)
                else -> InternetTab(state.features, state.recentSearches, press, actions)
            }
        }
    }
}

private const val TAB_CONTROL = 0
private const val TAB_INTERNET = 1

@Composable
private fun BigTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = { Text(text, style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.height(60.dp),
    )
}

@Composable
private fun Header(device: TvDevice, onDisconnect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleLarge)
            Text(
                device.brand.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDisconnect) { Text("Cambiar TV") }
    }
}
