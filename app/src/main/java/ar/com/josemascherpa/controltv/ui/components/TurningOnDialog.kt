package ar.com.josemascherpa.controltv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Cartel de espera mientras se prende una TV apagada (puede tardar hasta medio minuto). */
@Composable
fun TurningOnDialog(tvName: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Prendiendo $tvName") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Esperá unos segundos, la TV tarda un poco en prender.")
            }
        },
    )
}
