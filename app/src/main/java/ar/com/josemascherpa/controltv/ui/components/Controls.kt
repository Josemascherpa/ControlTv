package ar.com.josemascherpa.controltv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.josemascherpa.controltv.domain.model.RemoteKey
import ar.com.josemascherpa.controltv.domain.model.ScrollDirection

// Piezas de pantalla que se usan en varios lugares. Sólo dibujan y avisan cuando se tocan.
// Los íconos vienen de la librería "Material Icons" (androidx.compose.material.icons).

/** Botón redondo con un ícono y, debajo, su nombre escrito (más fácil que adivinar el ícono). */
@Composable
fun RemoteButton(
    icon: ImageVector,
    label: String,
    size: Dp = 56.dp,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(size)) {
            // contentDescription es lo que lee en voz alta el lector de pantalla (accesibilidad).
            Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.46f))
        }
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Las flechas en cruz con OK en el medio, dentro de un círculo como el de un control real. */
@Composable
fun DPad(press: (RemoteKey) -> Unit) {
    Box(
        Modifier
            .size(DPAD_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        DPadArrow(Icons.Rounded.KeyboardArrowUp, "Arriba", Alignment.TopCenter) { press(RemoteKey.UP) }
        DPadArrow(Icons.Rounded.KeyboardArrowDown, "Abajo", Alignment.BottomCenter) { press(RemoteKey.DOWN) }
        DPadArrow(Icons.Rounded.KeyboardArrowLeft, "Izquierda", Alignment.CenterStart) { press(RemoteKey.LEFT) }
        DPadArrow(Icons.Rounded.KeyboardArrowRight, "Derecha", Alignment.CenterEnd) { press(RemoteKey.RIGHT) }
        Button(
            onClick = { press(RemoteKey.OK) },
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.align(Alignment.Center).size(84.dp),
        ) { Text("OK", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun BoxScope.DPadArrow(icon: ImageVector, label: String, position: Alignment, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.align(position).size(64.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(38.dp),
        )
    }
}

private val DPAD_SIZE = 216.dp

/**
 * Pad táctil, como el de una notebook:
 *   - un dedo deslizando mueve el cursor de la TV ([onMove]),
 *   - un toque corto hace click ([onClick]),
 *   - dos dedos deslizando suben o bajan la página ([onScroll], si la TV lo permite).
 * No sabe nada de marcas: sólo dibuja y detecta gestos.
 */
@Composable
fun TouchPad(
    onMove: (dx: Int, dy: Int) -> Unit,
    onClick: () -> Unit,
    onScroll: ((ScrollDirection) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // rememberUpdatedState: el detector de gestos se arma una sola vez, pero siempre tiene que
    // llamar a la versión más nueva de cada función. Sin esto se quedaría con la primera.
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnScroll by rememberUpdatedState(onScroll)

    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .pointerInput(Unit) {
                // awaitEachGesture: espera que alguien apoye un dedo y sigue todo el gesto hasta que
                // levanta todos los dedos. Acá miramos a mano cuántos dedos hay apoyados.
                awaitEachGesture {
                    val firstDown = awaitFirstDown()
                    var slideDistance = 0f
                    var usedTwoFingers = false
                    var scrollPendingX = 0f
                    var scrollPendingY = 0f
                    var lastTime = firstDown.uptimeMillis
                    val scrollStep = SCROLL_STEP_DP.dp.toPx()

                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.filter { it.pressed }
                        lastTime = event.changes.first().uptimeMillis

                        if (fingers.size >= 2) {
                            usedTwoFingers = true
                            // Promedio de lo que se movieron los dedos. Como en una notebook:
                            // dedos hacia arriba = la página baja.
                            scrollPendingX += fingers.map { it.positionChange().x }.average().toFloat()
                            scrollPendingY += fingers.map { it.positionChange().y }.average().toFloat()
                            // Se mueve sólo en el eje que más se deslizó: horizontal o vertical.
                            if (kotlin.math.abs(scrollPendingX) > kotlin.math.abs(scrollPendingY)) {
                                while (kotlin.math.abs(scrollPendingX) >= scrollStep) {
                                    // Dedos hacia la izquierda = la página va hacia la derecha.
                                    val direction = if (scrollPendingX < 0) ScrollDirection.RIGHT else ScrollDirection.LEFT
                                    currentOnScroll?.invoke(direction)
                                    scrollPendingX -= if (scrollPendingX < 0) -scrollStep else scrollStep
                                }
                                scrollPendingY = 0f
                            } else {
                                while (kotlin.math.abs(scrollPendingY) >= scrollStep) {
                                    val direction = if (scrollPendingY < 0) ScrollDirection.DOWN else ScrollDirection.UP
                                    currentOnScroll?.invoke(direction)
                                    scrollPendingY -= if (scrollPendingY < 0) -scrollStep else scrollStep
                                }
                                scrollPendingX = 0f
                            }
                        } else if (fingers.size == 1 && !usedTwoFingers) {
                            val drag = fingers.first().positionChange()
                            slideDistance += drag.getDistance()
                            if (drag != Offset.Zero) {
                                currentOnMove((drag.x * POINTER_SPEED).toInt(), (drag.y * POINTER_SPEED).toInt())
                            }
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })

                    // Toque corto y casi sin mover el dedo = click.
                    val isTap = !usedTwoFingers &&
                        slideDistance < viewConfiguration.touchSlop &&
                        lastTime - firstDown.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                    if (isTap) currentOnClick()
                }
            }
            .semantics { contentDescription = "Pad para mover el mouse de la TV" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            Icons.Rounded.TouchApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(48.dp),
        )
        Text(
            if (onScroll != null) {
                "Deslizá un dedo para mover la flecha\nTocá para hacer click\nDeslizá dos dedos para mover la página"
            } else {
                "Deslizá un dedo para mover la flecha\nTocá para hacer click"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        }
    }
}

// Cuánto se mueve el cursor de la TV por cada píxel que mueve el dedo.
private const val POINTER_SPEED = 1.5f

// Cuánto tienen que deslizarse los dos dedos para que la página se mueva un paso.
private const val SCROLL_STEP_DP = 40

/** Botón ancho y alto, fácil de tocar. "tonal" = color más suave, para las opciones secundarias. */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tonal: Boolean = false,
    onClick: () -> Unit,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
    val buttonModifier = modifier.heightIn(min = 54.dp)
    if (tonal) {
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = buttonModifier, content = content)
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = buttonModifier, content = content)
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
