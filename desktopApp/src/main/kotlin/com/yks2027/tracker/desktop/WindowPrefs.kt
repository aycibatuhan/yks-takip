package com.yks2027.tracker.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition
import java.io.File
import java.util.Properties

/** Tiny properties file next to the database: last window size and position. */
data class WindowPrefs(val width: Int, val height: Int, val x: Int?, val y: Int?) {
    companion object {
        private const val MIN_W = 900
        private const val MIN_H = 600

        fun load(file: File): WindowPrefs {
            val p = Properties()
            runCatching { if (file.exists()) file.inputStream().use { p.load(it) } }
            val w = p.getProperty("width")?.toIntOrNull()?.coerceAtLeast(MIN_W) ?: 1280
            val h = p.getProperty("height")?.toIntOrNull()?.coerceAtLeast(MIN_H) ?: 800
            return WindowPrefs(w, h, p.getProperty("x")?.toIntOrNull(), p.getProperty("y")?.toIntOrNull())
        }

        fun save(file: File, size: DpSize, position: WindowPosition) {
            val p = Properties()
            p.setProperty("width", size.width.value.toInt().coerceAtLeast(MIN_W).toString())
            p.setProperty("height", size.height.value.toInt().coerceAtLeast(MIN_H).toString())
            if (position is WindowPosition.Absolute) {
                p.setProperty("x", position.x.value.toInt().toString())
                p.setProperty("y", position.y.value.toInt().toString())
            }
            runCatching { file.parentFile?.mkdirs(); file.outputStream().use { p.store(it, "YKS Takip window") } }
        }
    }
}
