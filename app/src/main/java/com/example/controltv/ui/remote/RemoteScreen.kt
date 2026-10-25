package com.example.controltv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.model.TvFeature

/**
 * El control remoto. Tiene pestañas para no amontonar todo en una sola pantalla:
 *   - Control: los botones de siempre (encender, flechas, volumen…) y el botón para escribir.
 *   - Mouse: un pad táctil a pantalla completa (sólo en TVs con cursor).
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
    // Sólo se muestran las pestañas que esta TV puede usar (ej. "Mouse" sólo si tiene cursor).
    val tabs = RemoteTab.entries.filter { it.isAvailable(state.features) }
    var selectedName by rememberSaveable { mutableStateOf(RemoteTab.CONTROL.name) }
    val tab = tabs.firstOrNull { it.name == selectedName } ?: RemoteTab.CONTROL

    Column(modifier.fillMaxSize()) {
        Header(device, onDisconnect = actions::disconnect)
        PrimaryTabRow(selectedTabIndex = tabs.indexOf(tab)) {
            tabs.forEach { BigTab(it, selected = it == tab) { selectedName = it.name } }
        }
        if (tab == RemoteTab.MOUSE) {
            // El pad ocupa todo el alto disponible, por eso acá no hay scroll.
            MouseTab(state.features, press, actions, Modifier.padding(16.dp))
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (tab) {
                    RemoteTab.CONTROL -> ControlTab(state.features, press, actions)
                    else -> InternetTab(state.features, state.recentSearches, press, actions)
                }
            }
        }
    }
}

private enum class RemoteTab(val label: String, val icon: ImageVector) {
    CONTROL("Control", Icons.Rounded.SettingsRemote),
    MOUSE("Mouse", Icons.Rounded.Mouse),
    INTERNET("Internet", Icons.Rounded.Public);

    fun isAvailable(features: Set<TvFeature>): Boolean = when (this) {
        MOUSE -> TvFeature.POINTER in features
        else -> true
    }
}

@Composable
private fun BigTab(tab: RemoteTab, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(tab.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun Header(device: TvDevice, onDisconnect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                device.brand.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDisconnect) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cambiar TV")
        }
    }
}
