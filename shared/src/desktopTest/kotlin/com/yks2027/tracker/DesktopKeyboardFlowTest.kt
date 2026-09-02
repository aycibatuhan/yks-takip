package com.yks2027.tracker

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.ui.YksTheme
import com.yks2027.tracker.feature.exams.ExamEntryScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.compose.KoinContext
import org.koin.core.context.GlobalContext

/** Spec §7 — desktop keyboard flow: fill the TYT form, press Enter → the exam is saved. */
@OptIn(ExperimentalTestApi::class)
class DesktopKeyboardFlowTest {

    @Test
    fun enterSubmitsExamEntry() {
        DesktopTestApp.ensureStarted()
        val examDao = GlobalContext.get().get<ExamDao>()
        val before = runBlocking { examDao.allOnce().size }
        var done = false
        runDesktopComposeUiTest(width = 1280, height = 900) {
            setContent { KoinContext { YksTheme(darkTheme = false) { ExamEntryScreen(onDone = { done = true }) } } }
            repeat(4) { waitForIdle(); runBlocking { delay(150) } }
            // Four TYT sections × (doğru, yanlış) — the fields are the numeric OutlinedTextFields.
            val fields = onAllNodesWithText("", substring = true) // placeholder
            val dogru = listOf("30", "15", "28", "12"); val yanlis = listOf("5", "3", "6", "4")
            var i = 0
            fun typeInto(label: String, value: String) {
                onAllNodesWithText(label, substring = false)[i].performClick()
                onAllNodesWithText(label, substring = false)[i].performTextInput(value)
            }
            repeat(4) { idx ->
                i = idx
                typeInto("Doğru", dogru[idx])
                typeInto("Yanlış", yanlis[idx])
            }
            waitForIdle()
            onAllNodesWithText("Yanlış", substring = false)[3].performKeyInput { pressKey(Key.Enter) }
            repeat(8) { waitForIdle(); runBlocking { delay(200) } }
        }
        val after = runBlocking { examDao.allOnce().size }
        assertEquals("Enter must save exactly one exam", before + 1, after)
        assertEquals(true, done)
    }
}
