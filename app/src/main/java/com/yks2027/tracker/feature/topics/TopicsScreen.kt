package com.yks2027.tracker.feature.topics

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
import androidx.hilt.navigation.compose.hiltViewModel
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val weakWrongs: Int,
)

data class TopicsUiState(
    val subject: Subject = Subject.TYT_MATEMATIK,
    val rows: List<TopicRowUi> = emptyList(),
    val studiedCount: Int = 0,
    val total: Int = 0,
)

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val topicDao: TopicDao,
    examDao: ExamDao,
    private val clock: IstanbulClock,
) : ViewModel() {

    private val selectedSubject = MutableStateFlow(Subject.TYT_MATEMATIK)
    val subject = selectedSubject.asStateFlow()

    private val statuses = topicDao.observeStatuses()
        .map { list -> list.associateBy { it.topicId } }

    private val weakWrongs = examDao.observeWeakTopicRows()
        .map { rows -> rows.groupBy { it.topicId }.mapValues { (_, g) -> g.sumOf { it.wrongSum } } }

    val ui = combine(selectedSubject, statuses, weakWrongs) { subject, statusMap, weakMap ->
        val topics = TopicCatalog.topicsFor(subject)
        val rows = topics.map { topic ->
            val status = statusMap[topic.id]
            TopicRowUi(
                topic = topic,
                studied = status?.studied == true,
                practiced = status?.practiced == true,
                reviewed = status?.reviewed == true,
                weakWrongs = weakMap[topic.id] ?: 0,
            )
        }
        TopicsUiState(
            subject = subject,
            rows = rows,
            studiedCount = rows.count { it.studied },
            total = rows.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TopicsUiState())

    fun select(subject: Subject) {
        selectedSubject.value = subject
    }

    fun toggle(row: TopicRowUi, field: StatusField) {
        viewModelScope.launch {
            topicDao.upsertStatus(
                TopicStatusEntity(
                    topicId = row.topic.id,
                    studied = if (field == StatusField.STUDIED) !row.studied else row.studied,
                    practiced = if (field == StatusField.PRACTICED) !row.practiced else row.practiced,
                    reviewed = if (field == StatusField.REVIEWED) !row.reviewed else row.reviewed,
                    updatedAt = clock.now().toEpochMilli(),
                ),
            )
        }
    }

    enum class StatusField { STUDIED, PRACTICED, REVIEWED }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(viewModel: TopicsViewModel = hiltViewModel()) {
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
                        LinearProgressIndicator(
                            progress = { if (ui.total == 0) 0f else ui.studiedCount.toFloat() / ui.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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
                        TopicRow(row, onToggle = { field -> viewModel.toggle(row, field) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(row: TopicRowUi, onToggle: (TopicsViewModel.StatusField) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.topic.label, style = MaterialTheme.typography.bodyMedium)
                if (row.weakWrongs > 0) {
                    Text(
                        "${row.weakWrongs} yanlış işareti",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            FilterChip(
                selected = row.studied,
                onClick = { onToggle(TopicsViewModel.StatusField.STUDIED) },
                label = { Text("Çalıştım") },
            )
            FilterChip(
                selected = row.practiced,
                onClick = { onToggle(TopicsViewModel.StatusField.PRACTICED) },
                label = { Text("Soru") },
            )
            FilterChip(
                selected = row.reviewed,
                onClick = { onToggle(TopicsViewModel.StatusField.REVIEWED) },
                label = { Text("Tekrar") },
            )
        }
    }
}
