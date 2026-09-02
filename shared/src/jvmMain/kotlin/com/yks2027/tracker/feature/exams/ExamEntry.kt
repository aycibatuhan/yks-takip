package com.yks2027.tracker.feature.exams

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.ExamEntity
import com.yks2027.tracker.core.database.ExamSectionEntity
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.NetCalculator
import com.yks2027.tracker.core.model.Subject
import com.yks2027.tracker.core.model.fullSections
import com.yks2027.tracker.core.model.subjectChoices
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.ui.isExpandedWidth
import com.yks2027.tracker.ui.ExamEntryRoute
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SectionInput(
    val subject: Subject,
    val questionCount: Int,
    val correct: String = "",
    val wrong: String = "",
)

/** M4 — structured topic mark: which catalog topic produced wrongs/blanks, and why. */
data class TopicMarkInput(
    val subject: Subject,
    val topicId: String,
    val wrong: Int,
    val blank: Int,
    val errorType: com.yks2027.tracker.core.model.ErrorType?,
)

data class EntryUiState(
    val examId: Long? = null,
    val kind: ExamKind = ExamKind.TYT_FULL,
    val date: LocalDate = LocalDate.now(),
    val name: String = "",
    val publisher: String = "",
    val sections: List<SectionInput> = emptyList(),
    /** Branş only: editable question count (publisher booklet sizes vary — PRD §4.1). */
    val bransCountInput: String = "",
    val topicMarks: List<TopicMarkInput> = emptyList(),
    val duplicateWarning: Boolean = false,
    val saved: Boolean = false,
    /** v1.2 AI extraction: non-null = form was pre-filled by Claude; items are warnings. */
    val aiPrefillWarnings: List<String>? = null,
)

/** Parses a count field: blank counts as 0; non-numeric is invalid (null). */
fun parseCount(raw: String): Int? = if (raw.isBlank()) 0 else raw.toIntOrNull()

fun SectionInput.isValidInput(): Boolean {
    val c = parseCount(correct) ?: return false
    val w = parseCount(wrong) ?: return false
    return NetCalculator.isValid(questionCount, c, w)
}

fun SectionInput.netQuartersOrNull(): Int? {
    if (!isValidInput()) return null
    return NetCalculator.netQuarters(parseCount(correct)!!, parseCount(wrong)!!)
}

class ExamEntryViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val examDao: ExamDao,
    private val clock: IstanbulClock,
    prefillHolder: com.yks2027.tracker.feature.importexport.ExamPrefillHolder,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExamEntryRoute>()
    private val editingId: Long? = route.examId.takeIf { it > 0 }
    private var loadedCreatedAt: Long? = null

    private val _state = MutableStateFlow(
        EntryUiState(
            examId = editingId,
            date = clock.today(),
            sections = defaultSections(ExamKind.TYT_FULL),
        ),
    )
    val state = _state.asStateFlow()

    init {
        // v1.2: an AI-extracted result pre-fills a NEW entry; the human confirms/saves.
        if (route.prefill && editingId == null) {
            prefillHolder.consume()?.let { extracted ->
                _state.value = EntryUiState(
                    kind = extracted.kind,
                    date = extracted.date ?: clock.today(),
                    name = extracted.name.orEmpty(),
                    publisher = extracted.publisher.orEmpty(),
                    sections = extracted.sections.map { s ->
                        SectionInput(
                            subject = s.subject,
                            questionCount = s.questionCount,
                            correct = s.correct?.toString() ?: "",
                            wrong = s.wrong?.toString() ?: "",
                        )
                    },
                    bransCountInput = if (extracted.kind.isFull) {
                        ""
                    } else {
                        extracted.sections.first().questionCount.toString()
                    },
                    aiPrefillWarnings = extracted.warnings,
                )
            }
        }
        editingId?.let { id ->
            viewModelScope.launch {
                val existing = examDao.getById(id) ?: return@launch
                loadedCreatedAt = existing.exam.createdAt
                val loadedSections = existing.sections.sortedBy { it.orderIndex }.map {
                    SectionInput(
                        subject = it.subject,
                        questionCount = it.questionCount,
                        correct = it.correctCount.toString(),
                        wrong = it.wrongCount.toString(),
                    )
                }
                _state.value = EntryUiState(
                    examId = id,
                    kind = existing.exam.examKind,
                    date = LocalDate.ofEpochDay(existing.exam.takenAtDay),
                    name = existing.exam.name.orEmpty(),
                    publisher = existing.exam.publisher.orEmpty(),
                    sections = loadedSections,
                    bransCountInput = if (existing.exam.examKind.isFull) {
                        ""
                    } else {
                        loadedSections.firstOrNull()?.questionCount?.toString().orEmpty()
                    },
                    topicMarks = examDao.topicMarksForExam(id).map {
                        TopicMarkInput(
                            subject = it.subject,
                            topicId = it.topicId,
                            wrong = it.wrongCount,
                            blank = it.blankCount,
                            errorType = it.errorType?.let { name ->
                                runCatching { com.yks2027.tracker.core.model.ErrorType.valueOf(name) }.getOrNull()
                            },
                        )
                    },
                )
            }
        }
    }

    fun setKind(kind: ExamKind) {
        if (editingId != null) return // kind is fixed while editing
        val sections = defaultSections(kind)
        _state.value = _state.value.copy(
            kind = kind,
            sections = sections,
            bransCountInput = if (kind.isFull) "" else sections.first().questionCount.toString(),
            duplicateWarning = false,
        )
    }

    fun setBransSubject(subject: Subject) {
        val s = _state.value
        if (s.kind.isFull || editingId != null) return
        _state.value = s.copy(
            sections = listOf(SectionInput(subject = subject, questionCount = subject.defaultQuestionCount)),
            bransCountInput = subject.defaultQuestionCount.toString(),
        )
    }

    fun setBransCount(raw: String) {
        val s = _state.value
        if (s.kind.isFull) return
        val digits = raw.filter { it.isDigit() }.take(3)
        _state.value = s.copy(
            bransCountInput = digits,
            sections = s.sections.map { it.copy(questionCount = digits.toIntOrNull() ?: 0) },
        )
    }

    fun setDate(date: LocalDate) = update { copy(date = date, duplicateWarning = false) }
    fun setName(v: String) = update { copy(name = v) }
    fun setPublisher(v: String) = update { copy(publisher = v) }

    fun setCorrect(subject: Subject, raw: String) = updateSection(subject) { copy(correct = raw.digitsOnly()) }
    fun setWrong(subject: Subject, raw: String) = updateSection(subject) { copy(wrong = raw.digitsOnly()) }

    fun addTopicMark(mark: TopicMarkInput) {
        if (mark.wrong <= 0 && mark.blank <= 0) return
        update { copy(topicMarks = topicMarks + mark) }
    }

    fun removeTopicMark(index: Int) = update {
        copy(topicMarks = topicMarks.filterIndexed { i, _ -> i != index })
    }

    fun save() {
        val s = _state.value
        if (s.sections.any { !it.isValidInput() }) return
        viewModelScope.launch {
            val day = s.date.toEpochDay()
            if (!s.duplicateWarning) {
                val duplicates = examDao.countByKindAndDay(s.kind, day, excludeId = s.examId ?: -1)
                if (duplicates > 0) {
                    // PRD §4.2: warn, don't block — a second save proceeds.
                    _state.value = s.copy(duplicateWarning = true)
                    return@launch
                }
            }
            val now = clock.now().toEpochMilli()
            val exam = ExamEntity(
                id = s.examId ?: 0,
                examKind = s.kind,
                takenAtDay = day,
                name = s.name.trim().ifBlank { null },
                publisher = s.publisher.trim().ifBlank { null },
                durationMin = null,
                notes = null,
                createdAt = loadedCreatedAt ?: now,
                updatedAt = now,
            )
            val sections = s.sections.mapIndexed { index, sec ->
                ExamSectionEntity(
                    examId = exam.id,
                    subject = sec.subject,
                    questionCount = sec.questionCount,
                    correctCount = parseCount(sec.correct)!!,
                    wrongCount = parseCount(sec.wrong)!!,
                    orderIndex = index,
                )
            }
            val savedId = examDao.upsertExamWithSections(exam, sections)
            // M4: structured marks replace free-text notes; legacy note rows are left as-is.
            examDao.deleteTopicMarksForExam(savedId)
            if (s.topicMarks.isNotEmpty()) {
                examDao.insertTopicMarks(
                    s.topicMarks.map { m ->
                        com.yks2027.tracker.core.database.ExamTopicMarkEntity(
                            examId = savedId, subject = m.subject, topicId = m.topicId,
                            wrongCount = m.wrong, blankCount = m.blank,
                            errorType = m.errorType?.name, createdAt = now,
                        )
                    },
                )
            }
            _state.value = _state.value.copy(saved = true)
        }
    }

    private fun update(block: EntryUiState.() -> EntryUiState) {
        _state.value = _state.value.block()
    }

    private fun updateSection(subject: Subject, block: SectionInput.() -> SectionInput) = update {
        copy(sections = sections.map { if (it.subject == subject) it.block() else it })
    }

    private fun defaultSections(kind: ExamKind): List<SectionInput> =
        if (kind.isFull) {
            kind.fullSections().map { SectionInput(subject = it, questionCount = it.defaultQuestionCount) }
        } else {
            // Branş = exactly one section (PRD §4.1); invariant enforced here, not in SQL.
            kind.subjectChoices().first().let {
                listOf(SectionInput(subject = it, questionCount = it.defaultQuestionCount))
            }
        }

    private fun String.digitsOnly(): String = filter { it.isDigit() }.take(3)
}

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEntryScreen(
    onDone: () -> Unit,
    viewModel: ExamEntryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showDatePicker by remember { mutableStateOf(false) }
    val expanded = isExpandedWidth()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.examId != null) "Denemeyi Düzenle" else "Deneme Ekle") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.aiPrefillWarnings?.let { warnings ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "AI'dan alındı — kaydetmeden önce değerleri karneyle karşılaştır.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        warnings.forEach { warning ->
                            Text(
                                "• $warning",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExamKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { viewModel.setKind(kind) },
                        label = { Text(kind.label) },
                        enabled = state.examId == null || state.kind == kind,
                    )
                }
            }

            if (!state.kind.isFull) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BransSubjectPicker(
                        current = state.sections.firstOrNull()?.subject,
                        choices = state.kind.subjectChoices(),
                        enabled = state.examId == null,
                        onSelect = viewModel::setBransSubject,
                    )
                    OutlinedTextField(
                        value = state.bransCountInput,
                        onValueChange = viewModel::setBransCount,
                        label = { Text("Soru sayısı") },
                        singleLine = true,
                        isError = (state.bransCountInput.toIntOrNull() ?: 0) < 1,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Deneme adı") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.publisher,
                    onValueChange = viewModel::setPublisher,
                    label = { Text("Yayınevi") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedButton(onClick = { showDatePicker = true }) {
                Text("Tarih: ${state.date.format(dateFormat)}")
            }

            if (expanded) {
                state.sections.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { section ->
                            SectionCard(
                                section = section,
                                onCorrect = { viewModel.setCorrect(section.subject, it) },
                                onWrong = { viewModel.setWrong(section.subject, it) },
                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Column(Modifier.weight(1f)) {}
                    }
                }
            } else {
                state.sections.forEach { section ->
                    SectionCard(
                        section = section,
                        onCorrect = { viewModel.setCorrect(section.subject, it) },
                        onWrong = { viewModel.setWrong(section.subject, it) },
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            TopicMarksSection(
                marks = state.topicMarks,
                subjectChoices = state.sections.map { it.subject },
                onAdd = viewModel::addTopicMark,
                onRemove = viewModel::removeTopicMark,
            )

            val totalQuarters = state.sections.mapNotNull { it.netQuartersOrNull() }
            val allValid = state.sections.isNotEmpty() &&
                state.sections.all { it.isValidInput() && it.questionCount > 0 }
            Text(
                "Toplam Net: ${if (allValid) NetCalculator.format(totalQuarters.sum()) else "—"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (allValid && totalQuarters.sum() < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            if (state.duplicateWarning) {
                Text(
                    "Aynı gün için ${state.kind.label} denemesi zaten var. Yine de kaydetmek için tekrar bas.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(onClick = viewModel::save, enabled = allValid, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.duplicateWarning) "Yine de Kaydet" else "Kaydet")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        viewModel.setDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Tamam") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Vazgeç") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TopicMarksSection(
    marks: List<TopicMarkInput>,
    subjectChoices: List<Subject>,
    onAdd: (TopicMarkInput) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var markSubject by remember(subjectChoices) {
        mutableStateOf(subjectChoices.firstOrNull() ?: Subject.TYT_MATEMATIK)
    }
    var selectedTopicId by remember { mutableStateOf<String?>(null) }
    var topicMenuOpen by remember { mutableStateOf(false) }
    var wrongInput by remember { mutableStateOf("1") }
    var blankInput by remember { mutableStateOf("0") }
    var errorType by remember { mutableStateOf<com.yks2027.tracker.core.model.ErrorType?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Konu işaretleri (hangi konudan kaç yanlış/boş?)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            marks.forEachIndexed { index, mark ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(com.yks2027.tracker.core.model.TopicCatalog.labelOf(mark.topicId))
                            append(" — ${mark.wrong}Y")
                            if (mark.blank > 0) append(" ${mark.blank}B")
                            mark.errorType?.let { append(" · ${it.label}") }
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { onRemove(index) }) { Text("Sil") }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                BransSubjectPicker(
                    current = markSubject,
                    choices = subjectChoices,
                    enabled = true,
                    onSelect = {
                        markSubject = it
                        selectedTopicId = null
                    },
                )
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { topicMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            selectedTopicId?.let(com.yks2027.tracker.core.model.TopicCatalog::labelOf)
                                ?: "Konu seç",
                            maxLines = 1,
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = topicMenuOpen,
                        onDismissRequest = { topicMenuOpen = false },
                    ) {
                        var lastGroup: String? = " "
                        com.yks2027.tracker.core.model.TopicCatalog.topicsFor(markSubject).forEach { topic ->
                            if (topic.group != lastGroup) {
                                lastGroup = topic.group
                                topic.group?.let { group ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Text(
                                                group,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        },
                                        onClick = {},
                                        enabled = false,
                                    )
                                }
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(topic.label) },
                                onClick = {
                                    selectedTopicId = topic.id
                                    topicMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = wrongInput,
                    onValueChange = { raw -> wrongInput = raw.filter { it.isDigit() }.take(2) },
                    label = { Text("Y") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.35f),
                )
                OutlinedTextField(
                    value = blankInput,
                    onValueChange = { raw -> blankInput = raw.filter { it.isDigit() }.take(2) },
                    label = { Text("B") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.35f),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Hata türü:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.yks2027.tracker.core.model.ErrorType.entries.forEach { type ->
                    FilterChip(
                        selected = errorType == type,
                        onClick = { errorType = if (errorType == type) null else type },
                        label = { Text(type.label) },
                    )
                }
                TextButton(
                    onClick = {
                        selectedTopicId?.let { topicId ->
                            onAdd(
                                TopicMarkInput(
                                    subject = markSubject,
                                    topicId = topicId,
                                    wrong = wrongInput.toIntOrNull() ?: 0,
                                    blank = blankInput.toIntOrNull() ?: 0,
                                    errorType = errorType,
                                ),
                            )
                            selectedTopicId = null
                            wrongInput = "1"
                            blankInput = "0"
                            errorType = null
                        }
                    },
                    enabled = selectedTopicId != null &&
                        ((wrongInput.toIntOrNull() ?: 0) + (blankInput.toIntOrNull() ?: 0)) > 0,
                ) { Text("Ekle") }
            }
        }
    }
}

@Composable
private fun BransSubjectPicker(
    current: Subject?,
    choices: List<Subject>,
    enabled: Boolean,
    onSelect: (Subject) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) {
            Text(current?.label ?: "Ders seç")
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { s ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(s.label) },
                    onClick = {
                        onSelect(s)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: SectionInput,
    onCorrect: (String) -> Unit,
    onWrong: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val correct = parseCount(section.correct)
    val wrong = parseCount(section.wrong)
    val valid = section.isValidInput()
    val netQuarters = section.netQuartersOrNull()
    val blank = if (correct != null && wrong != null) {
        NetCalculator.blank(section.questionCount, correct, wrong)
    } else {
        null
    }

    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${section.subject.label} (${section.questionCount})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = section.correct,
                    onValueChange = onCorrect,
                    label = { Text("Doğru") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { onNext() }),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = section.wrong,
                    onValueChange = onWrong,
                    label = { Text("Yanlış") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { onNext() }),
                    modifier = Modifier.weight(1f),
                )
            }
            if (!valid) {
                Text(
                    "Doğru + yanlış en fazla ${section.questionCount} olabilir",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                Text(
                    "Boş: ${blank ?: "—"}   Net: ${netQuarters?.let(NetCalculator::format) ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((netQuarters ?: 0) < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
