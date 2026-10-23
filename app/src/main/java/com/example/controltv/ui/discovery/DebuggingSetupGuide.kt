package com.example.controltv.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.controltv.ui.components.BigButton
import com.example.controltv.ui.components.Hint
import com.example.controltv.ui.components.SectionTitle

/**
 * Guía paso a paso para activar la "depuración" en Google TV / Android TV y Fire TV.
 * Sin eso, estas TVs no dejan que el teléfono las maneje (y ni siquiera aparecen en la búsqueda).
 *
 * Ocupa toda la pantalla (no es un cartelito) porque son varios pasos y queremos letra grande.
 */
@Composable
fun DebuggingSetupGuide(
    request: SetupGuideRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Como no podemos saber de antemano si es una Google TV o una Fire TV, que la persona elija.
    var platform by rememberSaveable { mutableStateOf(TvPlatform.GOOGLE_TV) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Intro(request)
                    if (request.reason == SetupGuideReason.FAILED) Troubleshooting()
                    WhatIsIt()
                    PlatformPicker(platform, onPick = { platform = it })
                    Steps(platform)
                }
                // Los botones quedan fijos abajo, siempre a la vista.
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(confirmText(request), onClick = onConfirm)
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Cerrar", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private enum class TvPlatform(val label: String) {
    GOOGLE_TV("Google TV / Android TV"),
    FIRE_TV("Fire TV (Amazon)"),
}

private fun confirmText(request: SetupGuideRequest): String = when {
    request.device == null -> "Ya lo activé, buscar de nuevo"
    request.reason == SetupGuideReason.FAILED -> "Probar de nuevo"
    else -> "Ya lo activé, conectar"
}

@Composable
private fun Intro(request: SetupGuideRequest) {
    val name = request.device?.name ?: "tu TV"
    val (title, text) = when (request.reason) {
        SetupGuideReason.FIRST_TIME -> "Antes de conectar: un paso único" to
            "Para manejar $name desde el teléfono hay que activar una opción en la TV. " +
            "Se hace una sola vez y lleva unos 2 minutos."
        SetupGuideReason.FAILED -> "No se pudo conectar" to
            "La TV no aceptó al teléfono. Casi siempre es porque falta activar una opción en la TV " +
            "o porque no se aceptó el permiso. Revisá esto:"
        SetupGuideReason.NOT_FOUND -> "¿No aparece tu Google TV o Fire TV?" to
            "Las Google TV nuevas aparecen solas, sin hacer nada. Los Fire TV y algunas Android TV más " +
            "viejas aparecen recién después de activar una opción. Se hace una sola vez y lleva unos 2 minutos."
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Troubleshooting() {
    InfoCard(
        title = "Qué revisar",
        lines = listOf(
            "Al conectar, en la TV aparece «¿Permitir depuración?». Elegí «Aceptar». Si marcás " +
                "«Permitir siempre», no vuelve a preguntar.",
            "Si elegiste «Cancelar» por error, apagá y prendé la TV y probá de nuevo.",
            "Fijate que la opción siga activada: algunas actualizaciones de la TV la apagan solas.",
            "La TV y el teléfono tienen que estar conectados al mismo WiFi.",
        ),
        highlighted = true,
    )
}

@Composable
private fun WhatIsIt() {
    InfoCard(
        title = "¿Qué es esto y para qué sirve?",
        lines = listOf(
            "La opción se llama «depuración» y está dentro del «modo desarrollador» de la TV. " +
                "Viene en la TV de fábrica, oculta: no hay que instalar nada ni pagar.",
            "Sirve para que un teléfono conectado a tu mismo WiFi le mande órdenes a la TV, " +
                "igual que el control remoto.",
            "Es seguro: la primera vez la TV te pregunta si aceptás a este teléfono. " +
                "Sin tu permiso no se conecta nadie.",
            "No borra nada ni cambia tus aplicaciones. Si algún día querés, la apagás desde el mismo menú.",
        ),
    )
}

@Composable
private fun InfoCard(title: String, lines: List<String>, highlighted: Boolean = false) {
    val colors = if (highlighted) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title)
            lines.forEach { line ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                    Text(line, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PlatformPicker(selected: TvPlatform, onPick: (TvPlatform) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("¿Qué TV tenés?")
        Hint("Elegí una para ver los pasos.")
        TvPlatform.entries.forEach { platform ->
            BigButton(
                text = (if (platform == selected) "✓  " else "") + platform.label,
                tonal = platform != selected,
                onClick = { onPick(platform) },
            )
        }
    }
}

@Composable
private fun Steps(platform: TvPlatform) {
    val steps = when (platform) {
        TvPlatform.GOOGLE_TV -> GOOGLE_TV_STEPS
        TvPlatform.FIRE_TV -> FIRE_TV_STEPS
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Pasos con el control de la TV")
        steps.forEachIndexed { index, step -> Step(number = index + 1, text = step) }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

// Los nombres de los menús pueden variar un poco según el modelo; por eso a veces damos dos opciones.
private val GOOGLE_TV_STEPS = listOf(
    "Andá a Configuración (el engranaje ⚙️ arriba a la derecha) > Sistema > Información. " +
        "En algunos modelos es Preferencias del dispositivo > Información.",
    "Bajá hasta «Compilación del SO de Android TV» (o «Compilación») y apretá el botón del medio " +
        "del control 7 veces seguidas, hasta que diga «Ya sos desarrollador».",
    "Volvé atrás: ahora aparece «Opciones para desarrolladores». Entrá.",
    "Activá «Depuración por USB». Si también aparece «Depuración por red» o «Depuración ADB», activala.",
    "Volvé a esta app y tocá el botón de abajo. En la TV va a aparecer «¿Permitir depuración?»: " +
        "marcá «Permitir siempre» y elegí «Aceptar».",
)

private val FIRE_TV_STEPS = listOf(
    "Andá a Configuración (el engranaje ⚙️) > Mi Fire TV > Acerca de.",
    "Parate sobre el nombre de tu aparato (por ejemplo «Fire TV Stick») y apretá el botón del medio " +
        "del control 7 veces seguidas, hasta que diga «Ya sos desarrollador».",
    "Volvé atrás a Mi Fire TV: ahora aparece «Opciones para desarrolladores». Entrá.",
    "Activá «Depuración ADB».",
    "Volvé a esta app y tocá el botón de abajo. En la TV va a aparecer «¿Permitir depuración USB?»: " +
        "marcá «Permitir siempre desde esta computadora» y elegí «Aceptar».",
)
