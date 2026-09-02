package com.yks2027.tracker.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.time.ISTANBUL
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

/**
 * M3 (PRD §12) — Glance home-screen countdown. Day-granular; the OS refreshes it every
 * 30 minutes (updatePeriodMillis), which is plenty for a value that changes daily.
 */
class CountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Same process as the app → the Koin singleton (one DataStore per file).
        val settings = GlobalContext.get().get<SettingsRepository>().settings.first()
        val today = LocalDate.now(ISTANBUL)
        val examDay = Instant.ofEpochMilli(settings.tytExamAt).atZone(ISTANBUL).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, examDay).coerceAtLeast(0)
        val estimated = !settings.datesConfirmed

        provideContent {
            Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text(
                    // v1.2 rebrand — year derives from the configured TYT date.
                    settings.yksYearLabel,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    if (days > 0) "$days gün" else "Sınav günü!",
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                )
                if (estimated) {
                    Text(
                        "tahmini tarih",
                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color.Gray)),
                    )
                }
            }
        }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}
