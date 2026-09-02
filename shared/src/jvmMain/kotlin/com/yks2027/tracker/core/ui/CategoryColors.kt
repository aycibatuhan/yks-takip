package com.yks2027.tracker.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.yks2027.tracker.core.model.PlannerCategory

/**
 * PRD §6.3 — category colors are theme tokens in code, never DB values.
 * Dark container = accent at 16% alpha composited over the surface.
 */

val PlannerCategory.accent: Color
    get() = when (this) {
        PlannerCategory.GEOMETRI -> Color(0xFF0284C7)
        PlannerCategory.AYT_MAT -> Color(0xFFEA580C)
        PlannerCategory.TYT_MAT -> Color(0xFFD97706)
        PlannerCategory.TYT_DENEME -> Color(0xFF7C3AED)
        PlannerCategory.AYT_FIZIK -> Color(0xFF2563EB)
        PlannerCategory.AYT_KIMYA -> Color(0xFFE11D48)
        PlannerCategory.AYT_BIYOLOJI -> Color(0xFF16A34A)
        PlannerCategory.TYT_FEN -> Color(0xFF0D9488)
        PlannerCategory.AYT_DENEME -> Color(0xFFC026D3)
        PlannerCategory.TYT_TURKCE -> Color(0xFF9333EA)
    }

private val PlannerCategory.lightContainer: Color
    get() = when (this) {
        PlannerCategory.GEOMETRI -> Color(0xFFE0F2FE)
        PlannerCategory.AYT_MAT -> Color(0xFFFFEDD5)
        PlannerCategory.TYT_MAT -> Color(0xFFFEF3C7)
        PlannerCategory.TYT_DENEME -> Color(0xFFEDE9FE)
        PlannerCategory.AYT_FIZIK -> Color(0xFFDBEAFE)
        PlannerCategory.AYT_KIMYA -> Color(0xFFFFE4E6)
        PlannerCategory.AYT_BIYOLOJI -> Color(0xFFDCFCE7)
        PlannerCategory.TYT_FEN -> Color(0xFFCCFBF1)
        PlannerCategory.AYT_DENEME -> Color(0xFFFAE8FF)
        PlannerCategory.TYT_TURKCE -> Color(0xFFF3E8FF)
    }

@Composable
fun PlannerCategory.containerColor(): Color =
    if (LocalDarkTheme.current) {
        accent.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        lightContainer
    }

@Composable
fun PlannerCategory.contentColor(): Color =
    if (LocalDarkTheme.current) {
        accent.copy(alpha = 0.72f).compositeOver(Color.White)
    } else {
        accent
    }
