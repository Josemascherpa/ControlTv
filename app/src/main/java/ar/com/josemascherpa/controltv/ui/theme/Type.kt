package ar.com.josemascherpa.controltv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Partimos de los tamaños de letra de Material y los ajustamos: lo bastante grandes para leer
// sin anteojos, pero sin que la pantalla se vea pesada. "copy" crea una copia del estilo
// cambiando sólo lo que pasamos.
private val base = Typography()

val Typography = Typography(
    headlineMedium = base.headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
)
