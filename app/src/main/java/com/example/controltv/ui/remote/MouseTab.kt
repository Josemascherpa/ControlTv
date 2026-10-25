package com.example.controltv.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvFeature
import com.example.controltv.ui.components.BigButton
import com.example.controltv.ui.components.TouchPad

/** Pestaña "Mouse": un pad que ocupa casi toda la pantalla, con Inicio y Atrás a mano. */
@Composable
fun MouseTab(
    features: Set<TvFeature>,
    press: (RemoteKey) -> Unit,
    actions: RemoteActions,
    modifier: Modifier = Modifier,
) {
    // Subir/bajar con dos dedos sólo se ofrece si la TV tiene navegador (es lo que sabe mover páginas).
    val onScroll: ((ScrollDirection) -> Unit)? =
        if (TvFeature.BROWSER in features) actions::scroll else null

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Inicio", Modifier.weight(1f), icon = Icons.Rounded.Home, tonal = true) { press(RemoteKey.HOME) }
            BigButton("Atrás", Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack, tonal = true) {
                press(RemoteKey.BACK)
            }
        }
        // weight(1f): el pad ocupa todo el alto que sobra debajo de los botones.
        TouchPad(
            onMove = actions::movePointer,
            onClick = actions::click,
            onScroll = onScroll,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
