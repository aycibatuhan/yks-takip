package com.yks2027.tracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.yks2027.tracker.core.ui.YksTheme
import com.yks2027.tracker.feature.aikoc.AiKocScreen
import com.yks2027.tracker.feature.exams.ExamsScreen
import com.yks2027.tracker.feature.notes.NotesScreen
import com.yks2027.tracker.feature.planner.PastWeeksScreen
import com.yks2027.tracker.feature.planner.PlannerScreen
import com.yks2027.tracker.feature.settings.SettingsScreen
import com.yks2027.tracker.feature.timer.TimerScreen
import com.yks2027.tracker.feature.topics.TopicsScreen
import com.yks2027.tracker.ui.AppRoot
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.compose.KoinContext

/**
 * v2.0 desktop "tour": real screens rendered OFFSCREEN with the real Koin graph and (when
 * seeded) a real v1.3 database, at expanded (1280dp) and compact (700dp) widths, light and
 * dark. Produces the PNGs delivered as screenshots. The shell (rail vs bottom bar) is
 * captured through AppRoot; feature screens are composed directly because NavHost's
 * back-stack lifecycles assert the AWT thread, which the offscreen scene does not use.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopTourTest {

    private fun shot(name: String, width: Int, height: Int, dark: Boolean, drive: ComposeUiTest.() -> Unit = {}, content: @Composable () -> Unit) {
        DesktopTestApp.ensureStarted()
        runDesktopComposeUiTest(width = width, height = height) {
            setContent { KoinContext { YksTheme(darkTheme = dark) { content() } } }
            settle()
            drive()
            settle()
            val out = File(DesktopTestApp.tourOut, "$name.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue(out.length() > 10_000)
        }
    }

    private fun ComposeUiTest.settle() {
        repeat(6) { waitForIdle(); runBlocking { delay(150) } }
        waitForIdle()
    }

    private fun ComposeUiTest.tap(text: String) {
        onAllNodesWithText(text, substring = false)[0].performClick()
    }

    // --- shell (rail at expanded width, bottom bar at compact width) ---
    @Test fun anaSayfaExpandedLight() = shot("desktop_ana_sayfa", 1280, 800, dark = false) { AppRoot() }
    @Test fun anaSayfaDark() = shot("desktop_ana_sayfa_koyu", 1280, 800, dark = true) { AppRoot() }
    @Test fun compactAnaSayfa() = shot("desktop_compact_ana_sayfa", 700, 900, dark = false) { AppRoot() }

    // --- feature screens with the seeded data ---
    @Test fun denemelerListe() = shot("desktop_denemeler", 1280, 800, dark = false) { ExamsScreen(onAdd = {}, onEdit = {}) }
    @Test fun denemelerAnaliz() = shot("desktop_analiz", 1280, 800, dark = false, drive = { tap("Analiz") }) { ExamsScreen(onAdd = {}, onEdit = {}) }
    @Test fun konular() = shot("desktop_konular", 1280, 800, dark = false) { TopicsScreen() }
    @Test fun planlayici() = shot("desktop_planlayici", 1280, 800, dark = false) { PlannerScreen(onOpenHistory = {}) }
    @Test fun gecmisHaftalar() = shot("desktop_gecmis_haftalar", 1280, 800, dark = false) { PastWeeksScreen(onOpenWeek = {}, onBack = {}) }
    @Test fun sayac() = shot("desktop_sayac", 1280, 800, dark = false) { TimerScreen() }
    @Test fun notlar() = shot("desktop_notlar", 1280, 800, dark = false) { NotesScreen() }
    @Test fun aiKocExpanded() = shot("desktop_ai_koc", 1280, 800, dark = false) { AiKocScreen() }
    @Test fun aiKocDark() = shot("desktop_ai_koc_koyu", 1280, 800, dark = true) { AiKocScreen() }
    @Test fun ayarlar() = shot("desktop_ayarlar", 1280, 800, dark = false) { SettingsScreen() }
    @Test fun compactAiKoc() = shot("desktop_compact_ai_koc", 700, 900, dark = false) { AiKocScreen() }

    /** Compact vs expanded is decided by window width (currentWindowAdaptiveInfo), same as a live resize. */
    @Test
    fun aiKocSessionsPaneFollowsWidth() {
        DesktopTestApp.ensureStarted()
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent { KoinContext { YksTheme(darkTheme = false) { AiKocScreen() } } }
            settle()
            assertTrue("expanded: permanent sessions pane", onAllNodesWithText("Yeni sohbet", substring = true).fetchSemanticsNodes().size >= 2)
        }
        runDesktopComposeUiTest(width = 700, height = 900) {
            setContent { KoinContext { YksTheme(darkTheme = false) { AiKocScreen() } } }
            settle()
            // Only the top-bar title "Yeni sohbet" remains; the pane's "+ Yeni sohbet" button is not composed.
            assertTrue("compact: no permanent pane", onAllNodesWithText("+ Yeni sohbet", substring = false).fetchSemanticsNodes().isEmpty())
        }
    }
}
