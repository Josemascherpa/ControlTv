package com.example.controltv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.controltv.ui.discovery.DiscoveryScreen
import com.example.controltv.ui.discovery.DiscoveryViewModel
import com.example.controltv.ui.messages.UserMessage
import com.example.controltv.ui.messages.text
import com.example.controltv.ui.remote.RemoteScreen
import com.example.controltv.ui.remote.RemoteViewModel
import com.example.controltv.ui.theme.ControlTvTheme

// La Activity es la "puerta de entrada" de la app: Android la abre al tocar el ícono.
// Con Compose, casi no hace nada más que llamar a setContent con la UI.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ControlTvTheme {
                ControlTvApp()
            }
        }
    }
}

@Composable
fun ControlTvApp(
    discoveryViewModel: DiscoveryViewModel = viewModel(factory = DiscoveryViewModel.Factory),
    remoteViewModel: RemoteViewModel = viewModel(factory = RemoteViewModel.Factory),
) {
    // collectAsStateWithLifecycle convierte el StateFlow en estado de Compose:
    // cada vez que el ViewModel lo cambia, esta función se vuelve a ejecutar ("recomposición").
    val discovery by discoveryViewModel.state.collectAsStateWithLifecycle()
    val remote by remoteViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // LaunchedEffect corre código una vez (o cuando cambia la "key").
    // Unit = sólo al entrar: buscamos TVs apenas abre la app.
    LaunchedEffect(Unit) { discoveryViewModel.scan() }

    ShowMessage(discovery.message, snackbar, discoveryViewModel::messageShown)
    ShowMessage(remote.message, snackbar, remoteViewModel::messageShown)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        // Navegación simple: si hay una TV conectada mostramos el control; si no, la búsqueda.
        val device = remote.device
        if (device != null) {
            // El botón "atrás" del teléfono vuelve a la lista de TVs.
            BackHandler { remoteViewModel.disconnect() }
            RemoteScreen(
                device = device,
                state = remote,
                actions = remoteViewModel,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            DiscoveryScreen(
                state = discovery,
                onScan = discoveryViewModel::scan,
                onConnect = discoveryViewModel::connect,
                onConnectManual = discoveryViewModel::connectManual,
                onCancelKey = discoveryViewModel::cancelKeyRequest,
                onSubmitCode = discoveryViewModel::submitPairingCode,
                onCancelCode = discoveryViewModel::cancelPairingCode,
                onShowSetupGuide = discoveryViewModel::showSetupGuide,
                onConfirmSetupGuide = discoveryViewModel::confirmSetupGuide,
                onDismissSetupGuide = discoveryViewModel::dismissSetupGuide,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** Muestra el aviso abajo de la pantalla y le avisa al ViewModel cuando ya se mostró. */
@Composable
private fun ShowMessage(message: UserMessage?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it.text())
            onShown()
        }
    }
}
