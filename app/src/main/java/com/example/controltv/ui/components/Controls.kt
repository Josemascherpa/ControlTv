package com.example.controltv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controltv.domain.model.RemoteKey

// Piezas de pantalla que se usan en varios lugares. Sólo dibujan y avisan cuando se tocan.

/** Botón redondo con un símbolo y, debajo, su nombre escrito (más fácil que adivinar el ícono). */
@Composable
fun RemoteButton(
    symbol: String,
    label: String,
    size: Dp = 64.dp,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalButton(
            onClick = onClick,
            shape = CircleShape,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            modifier = Modifier.size(size),
        ) {
            // contentDescription es lo que lee en voz alta el lector de pantalla (accesibilidad).
            Text(symbol, fontSize = 26.sp, modifier = Modifier.semantics { contentDescription = label })
        }
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Las flechas en cruz con OK en el medio. */
@Composable
fun DPad(press: (RemoteKey) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteButton("▲", "Arriba", size = 68.dp, showLabel = false) { press(RemoteKey.UP) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteButton("◀", "Izquierda", size = 68.dp, showLabel = false) { press(RemoteKey.LEFT) }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = { press(RemoteKey.OK) },
                shape = CircleShape,
                modifier = Modifier.size(96.dp),
            ) { Text("OK", fontSize = 24.sp) }
            Spacer(Modifier.size(8.dp))
            RemoteButton("▶", "Derecha", size = 68.dp, showLabel = false) { press(RemoteKey.RIGHT) }
        }
        RemoteButton("▼", "Abajo", size = 68.dp, showLabel = false) { press(RemoteKey.DOWN) }
    }
}

/** Botón ancho y alto, fácil de tocar. "tonal" = color más suave, para las opciones secundarias. */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    tonal: Boolean = false,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
    if (tonal) {
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.height(60.dp)) { content() }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.height(60.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
