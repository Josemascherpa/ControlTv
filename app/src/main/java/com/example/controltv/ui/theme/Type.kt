package com.example.controltv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

// Partimos de los tamaños de letra de Material y los agrandamos un poco en toda la app,
// para que se lea bien sin anteojos. "copy" crea una copia del estilo cambiando sólo lo que pasamos.
private val base = Typography()

val Typography = Typography(
    headlineMedium = base.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp),
    titleLarge = base.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = base.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 19.sp, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodySmall = base.bodySmall.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = base.labelLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
    labelMedium = base.labelMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
)
