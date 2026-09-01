package com.yks2027.tracker.core.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass

/** PRD §10.2 — Tab S9 FE+ landscape ≈ Expanded; phones are Compact. */
@Composable
fun isExpandedWidth(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
