package com.yks2027.tracker.core.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * v2.0 — window-level keyboard shortcuts (desktop) delivered to whichever screen is
 * listening. Android never emits; screens simply never receive anything.
 */
object KeyboardShortcuts {
    enum class Shortcut { NewChat, Escape }

    private val _events = MutableSharedFlow<Shortcut>(extraBufferCapacity = 8)
    val events: SharedFlow<Shortcut> = _events

    fun emit(shortcut: Shortcut) {
        _events.tryEmit(shortcut)
    }
}
