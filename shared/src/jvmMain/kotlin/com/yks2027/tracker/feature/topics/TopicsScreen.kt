package com.yks2027.tracker.feature.topics

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.TopicDao
import com.yks2027.tracker.core.database.TopicStatusEntity
import com.yks2027.tracker.core.model.Subject
import com.yks2027.tracker.core.model.Topic
import com.yks2027.tracker.core.model.TopicCatalog
import com.yks2027.tracker.core.time.IstanbulClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * M4 — konu takip çizelgesi (the competitor-standard topic checklist): per-topic
 * çalıştım / soru çözdüm / tekrar states with per-subject progress, plus a weak badge
 * (total wrongs from exam_topic_marks) so the student sees WHERE to aim the states.
 */

data class TopicRowUi(
    val topic: Topic,
    val studied: Boolean,
    val practiced: Boolean,
    val reviewed: Boolean,
    val needsReview: Boolean,
    val confidence: Int,
    val weakWrongs: Int,
)

data class TopicsUiState(
    val subject: Subject = Subject.TYT_MATEMATIK,
    val rows: List<TopicRowUi> = emptyList(),
    val studiedCount: Int = 0,
    val total: Int = 0,
    val needsReviewCount: Int = 0,
    val filter: TopicFilter = TopicFilter.ALL,
)

class TopicsViewModel constructor(
    private val topicDao: TopicDao,
    examDao: ExamDao,
    private val clock: IstanbulClock,
) : ViewModel() {

    private val selectedSubject = MutableStateFlow(Subject.TYT_MATEMATIK)
    val subject = selectedSubject.asStateFlow()
    private val selectedFilter = MutableStateFlow(TopicFilter.ALL)

    private val statuses = topicDao.observeStatuses()
        .map { list -> list.associateBy { it.topicId } }

    private val weakWrongs = examDao.observeWeakTopicRows()
        .map { rows -> rows.groupBy { it.topicId }.mapValues { (_, g) -> g.sumOf { it.wrongSum } } }

    val ui = combine(selectedSubject, statuses, weakWrongs, selectedFilter) { subject, statusMap, weakMap, filter ->
        val topics = TopicCatalog.topicsFor(subject)
        val rows = topics.map { topic ->
            val status = statusMap[topic.id]
            TopicRowUi(
                topic = topic,
                studied = status?.studied == true,
                practiced = status?.practiced == true,
                reviewed = status?.reviewed == true,
                needsReview = status?.needsReview == true,
                confidence = status?.confidence ?: 0,
                weakWrongs = weakMap[topic.id] ?: 0,
            )
        }
        TopicsUiState(
            subject = subject,
            rows = TopicFilters.apply(rows, filter),
            studiedCount = rows.count { it.studied },
            total = rows.size,
            needsReviewCount = rows.count { it.needsReview },
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TopicsUiState())

    fun select(subject: Subject) {
        selectedSubject.value = subject
    }

    fun setFilter(filter: TopicFilter) {
        selectedFilter.value = filter
    }

    fun toggle(row: TopicRowUi, field: StatusField) {
        val now = clock.now().toEpochMilli()
        val studied = if (field == StatusField.STUDIED) !row.studied else row.studied
        val reviewed = if (field == StatusField.REVIEWED) !row.reviewed else row.reviewed
        // Ticking "tekrar ettim" clears the "tekrar gerekli" flag; raising the flag un-ticks it.
        val needsReview = when (field) {
            StatusField.NEEDS_REVIEW -> !row.needsReview
            StatusField.REVIEWED -> if (reviewed) false else row.needsReview
            else -> row.needsReview
        }
        save(
            row,
            studied = studied,
            practiced = if (field == StatusField.PRACTICED) !row.practiced else row.practiced,
            reviewed = if (field == StatusField.NEEDS_REVIEW && needsReview) false else reviewed,
            needsReview = needsReview,
            confidence = row.confidence,
            lastStudiedAt = if (field == StatusField.STUDIED && studied) now else null,
            now = now,
        )
    }

    /** 0 = not set, 1 = zayıf, 2 = orta, 3 = iyi; tapping the current level clears it. */
    fun setConfidence(row: TopicRowUi, level: Int) {
        val now = clock.now().toEpochMilli()
        save(row, row.studied, row.practiced, row.reviewed, row.needsReview, if (row.confidence == level) 0 else level, null, now)
    }

    private fun save(
        row: TopicRowUi, studied: Boolean, practiced: Boolean, reviewed: Boolean,
        needsReview: Boolean, confidence: Int, lastStudiedAt: Long?, now: Long,
    ) {
        viewModelScope.launch {
            val previous = topicDao.statusesOnce().firstOrNull { it.topicId == row.topic.id }
            topicDao.upsertStatus(
                TopicStatusEntity(
                    topicId = row.topic.id,
                    studied = studied,
                    practiced = practiced,
                    reviewed = reviewed,
                    updatedAt = now,
                    needsReview = needsReview,
                    confidence = confidence,
                    lastStudiedAt = lastStudiedAt ?: previous?.lastStudiedAt,
                ),
            )
        }
    }

    enum class StatusField { STUDIED, PRACTICED, REVIEWED, NEEDS_REVIEW }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(viewModel: TopicsViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Konular") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Subject.entries) { subject ->
                    FilterChip(
                        selected = ui.subject == subject,
                        onClick = { viewModel.select(subject) },
                        label = { Text(subject.label) },
                    )
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val pct = if (ui.total == 0) 0 else ui.studiedCount * 100 / ui.total
                    // v1.2 görsel paso — per-subject progress ring, number inside.
                    com.yks2027.tracker.core.ui.charts.ProgressRing(
                        progress = if (ui.total == 0) 0f else ui.studiedCount.toFloat() / ui.total,
                        modifier = Modifier.size(56.dp),
                        stroke = 6.dp,
                    ) {
                        Text("%$pct", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Çalışılan: ${ui.studiedCount}/${ui.total} — ${ui.subject.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (ui.needsReviewCount > 0) {
                            Text(
                                "Tekrar gereken: ${ui.needsReviewCount} konu",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (ui.total == 0) 0f else ui.studiedCount.toFloat() / ui.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // v2.1 — filters (brother's feedback: "tekrar edilmesi gereken" as a first-class view).
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                items(TopicFilter.entries) { f ->
                    FilterChip(
                        selected = ui.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }
            if (ui.rows.isEmpty()) {
                Text(
                    "Bu filtrede konu yok.",
                    Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                var lastGroup: String? = " "
                ui.rows.forEach { row ->
                    if (row.topic.group != lastGroup) {
                        lastGroup = row.topic.group
                        row.topic.group?.let { group ->
                            item(key = "g-${ui.subject.name}-$group") {
                                Text(
                                    group,
                                    Modifier.padding(top = 10.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    item(key = row.topic.id) {
                        TopicRow(row, onToggle = { field -> viewModel.toggle(row, field) }, onConfidence = { level -> viewModel.setConfidence(row, level) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(
    row: TopicRowUi,
    onToggle: (TopicsViewModel.StatusField) -> Unit,
    onConfidence: (Int) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = if (row.needsReview) {
            androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.topic.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (row.needsReview) FontWeight.SemiBold else FontWeight.Normal)
                    if (row.weakWrongs > 0) {
                        Text(
                            "${row.weakWrongs} yanlış işareti",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                // Self-assessed confidence: tap again to clear.
                listOf(1 to "Zayıf", 2 to "Orta", 3 to "İyi").forEach { (level, label) ->
                    FilterChip(
                        selected = row.confidence == level,
                        onClick = { onConfidence(level) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = row.studied,
                    onClick = { onToggle(TopicsViewModel.StatusField.STUDIED) },
                    label = { Text("Çalıştım") },
                )
                FilterChip(
                    selected = row.practiced,
                    onClick = { onToggle(TopicsViewModel.StatusField.PRACTICED) },
                    label = { Text("Soru çözdüm") },
                )
                FilterChip(
                    selected = row.reviewed,
                    onClick = { onToggle(TopicsViewModel.StatusField.REVIEWED) },
                    label = { Text("Tekrar ettim") },
                )
                FilterChip(
                    selected = row.needsReview,
                    onClick = { onToggle(TopicsViewModel.StatusField.NEEDS_REVIEW) },
                    label = { Text("Tekrar gerekli") },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        selectedLabelColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
    }
}
