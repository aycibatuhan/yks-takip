package com.yks2027.tracker.core.backup

import com.yks2027.tracker.core.database.ChatDao
import com.yks2027.tracker.core.database.ChatMessageEntity
import com.yks2027.tracker.core.database.ChatThreadEntity
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.ExamEntity
import com.yks2027.tracker.core.database.ExamSectionEntity
import com.yks2027.tracker.core.database.ExamTopicMarkEntity
import com.yks2027.tracker.core.database.ExamTopicNoteEntity
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.FocusSessionEntity
import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.database.PlanTaskEntity
import com.yks2027.tracker.core.database.PlanWeekEntity
import com.yks2027.tracker.core.database.YksDatabase
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.datastore.ThemeMode
import com.yks2027.tracker.core.model.PlannerCategory
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.dateOf
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.yks2027.tracker.core.platform.PlatformFiles
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * PRD §9.4 — one JSON document, SAF export/import, import is full-replace only with an
 * automatic pre-import safety snapshot. The AI API key is deliberately NOT part of the
 * backup; backup_dir_uri is device-specific (URI permissions don't transfer) and is
 * also excluded.
 */

@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    @SerialName("schema_version") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("app_version") val appVersion: String,
    val settings: BackupSettings,
    val exams: List<BackupExam>,
    @SerialName("plan_weeks") val planWeeks: List<BackupWeek>,
    @SerialName("focus_sessions") val focusSessions: List<BackupFocusSession>,
    @SerialName("chat_threads") val chatThreads: List<BackupThread>,
    @SerialName("topic_statuses") val topicStatuses: List<BackupTopicStatus> = emptyList(),
    val notes: List<BackupNote> = emptyList(),
    /** Profile METADATA only — API keys are Keystore-bound and never leave the device. */
    @SerialName("ai_profiles") val aiProfiles: List<BackupAiProfile> = emptyList(),
    /** v5: folder ids are stable so thread→folder assignments survive a restore. */
    @SerialName("chat_folders") val chatFolders: List<BackupFolder> = emptyList(),
) {
    companion object {
        const val FORMAT = "yks-backup"
        /**
         * v2 (M3): exams gained nested topic_notes. v3 (M4): exams gained topic_marks;
         * top-level topic_statuses added. v4 (v1.2): notes + ai_profiles (metadata only —
         * keys must be re-entered after an import on a new device) + active profile id.
         * v5 (v1.3): chat_folders + per-thread pinned/folder_id.
         * Older files import fine (defaults).
         */
        const val SCHEMA_VERSION = 5
    }
}

@Serializable
data class BackupFolder(
    val id: Long,
    val name: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class BackupNote(
    val title: String,
    val body: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class BackupAiProfile(
    val id: Long,
    val name: String,
    val protocol: String,
    @SerialName("base_url") val baseUrl: String,
    val model: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class BackupTopicStatus(
    @SerialName("topic_id") val topicId: String,
    val studied: Boolean,
    val practiced: Boolean,
    val reviewed: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class BackupTopicMark(
    val subject: String,
    @SerialName("topic_id") val topicId: String,
    @SerialName("wrong_count") val wrongCount: Int,
    @SerialName("blank_count") val blankCount: Int,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class BackupSettings(
    @SerialName("tyt_exam_at") val tytExamAt: Long,
    @SerialName("ayt_exam_at") val aytExamAt: Long,
    @SerialName("exam_dates_confirmed") val datesConfirmed: Boolean,
    @SerialName("theme_mode") val themeMode: String,
    @SerialName("active_ai_profile_id") val activeAiProfileId: Long? = null,
)

@Serializable
data class BackupExam(
    val id: Long,
    @SerialName("exam_kind") val examKind: String,
    @SerialName("taken_at_day") val takenAtDay: Long,
    val name: String?,
    val publisher: String?,
    @SerialName("duration_min") val durationMin: Int?,
    val notes: String?,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val sections: List<BackupSection>,
    @SerialName("topic_notes") val topicNotes: List<BackupTopicNote> = emptyList(),
    @SerialName("topic_marks") val topicMarks: List<BackupTopicMark> = emptyList(),
)

@Serializable
data class BackupTopicNote(
    val subject: String,
    val note: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class BackupSection(
    val subject: String,
    @SerialName("question_count") val questionCount: Int,
    @SerialName("correct_count") val correctCount: Int,
    @SerialName("wrong_count") val wrongCount: Int,
    @SerialName("order_index") val orderIndex: Int,
)

@Serializable
data class BackupWeek(
    @SerialName("week_start_day") val weekStartDay: Long,
    val note: String?,
    @SerialName("created_at") val createdAt: Long,
    val tasks: List<BackupTask>,
)

@Serializable
data class BackupTask(
    val id: Long,
    @SerialName("day_of_week") val dayOfWeek: Int,
    val category: String,
    val topic: String,
    @SerialName("target_questions") val targetQuestions: Int?,
    @SerialName("solved_questions") val solvedQuestions: Int?,
    @SerialName("is_done") val isDone: Boolean,
    @SerialName("completed_at") val completedAt: Long?,
    @SerialName("order_index") val orderIndex: Int,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class BackupFocusSession(
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long,
    @SerialName("active_ms") val activeMs: Long,
    @SerialName("planned_min") val plannedMin: Int,
    val completed: Boolean,
    val category: String?,
    @SerialName("task_id") val taskId: Long?,
    val note: String?,
)

@Serializable
data class BackupThread(
    val title: String?,
    @SerialName("created_at") val createdAt: Long,
    val messages: List<BackupMessage>,
    val pinned: Boolean = false,
    @SerialName("folder_id") val folderId: Long? = null,
)

@Serializable
data class BackupMessage(
    val role: String,
    val content: String,
    val model: String?,
    @SerialName("created_at") val createdAt: Long,
)

class BackupManager(
    private val files: PlatformFiles,
    private val db: YksDatabase,
    private val examDao: ExamDao,
    private val planDao: PlanDao,
    private val focusDao: FocusDao,
    private val chatDao: ChatDao,
    private val topicDao: com.yks2027.tracker.core.database.TopicDao,
    private val noteDao: com.yks2027.tracker.core.database.NoteDao,
    private val aiProfileDao: com.yks2027.tracker.core.database.AiProfileDao,
    private val settingsRepository: SettingsRepository,
    private val clock: IstanbulClock,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun suggestedFileName(): String = "yks_backup_${dateOf(clock.now())}.json"

    suspend fun exportJson(): String {
        val settings = settingsRepository.settings.first()
        val doc = BackupDocument(
            exportedAt = clock.now().toString(),
            appVersion = APP_VERSION,
            settings = BackupSettings(
                tytExamAt = settings.tytExamAt,
                aytExamAt = settings.aytExamAt,
                datesConfirmed = settings.datesConfirmed,
                themeMode = settings.themeMode.name,
                activeAiProfileId = settings.activeAiProfileId,
            ),
            topicStatuses = topicDao.statusesOnce().map { s ->
                BackupTopicStatus(s.topicId, s.studied, s.practiced, s.reviewed, s.updatedAt)
            },
            notes = noteDao.allOnce().map { n ->
                BackupNote(n.title, n.body, n.createdAt, n.updatedAt)
            },
            aiProfiles = aiProfileDao.allOnce().map { p ->
                BackupAiProfile(p.id, p.name, p.protocol, p.baseUrl, p.model, p.createdAt)
            },
            exams = run {
                val notesByExam = examDao.allTopicNotesOnce().groupBy { it.examId }
                val marksByExam = examDao.allTopicMarksOnce().groupBy { it.examId }
                examDao.allOnce().map { ews ->
                    BackupExam(
                        id = ews.exam.id,
                        examKind = ews.exam.examKind.name,
                        takenAtDay = ews.exam.takenAtDay,
                        name = ews.exam.name,
                        publisher = ews.exam.publisher,
                        durationMin = ews.exam.durationMin,
                        notes = ews.exam.notes,
                        createdAt = ews.exam.createdAt,
                        updatedAt = ews.exam.updatedAt,
                        sections = ews.sections.sortedBy { it.orderIndex }.map { s ->
                            BackupSection(s.subject.name, s.questionCount, s.correctCount, s.wrongCount, s.orderIndex)
                        },
                        topicNotes = (notesByExam[ews.exam.id] ?: emptyList()).map { n ->
                            BackupTopicNote(n.subject.name, n.note, n.createdAt)
                        },
                        topicMarks = (marksByExam[ews.exam.id] ?: emptyList()).map { m ->
                            BackupTopicMark(
                                m.subject.name, m.topicId, m.wrongCount, m.blankCount,
                                m.errorType, m.createdAt,
                            )
                        },
                    )
                }
            },
            planWeeks = run {
                val tasksByWeek = planDao.allTasksOnce().groupBy { it.weekStartDay }
                planDao.weeksOnce().map { w ->
                    BackupWeek(
                        weekStartDay = w.weekStartDay,
                        note = w.note,
                        createdAt = w.createdAt,
                        tasks = (tasksByWeek[w.weekStartDay] ?: emptyList()).map { t ->
                            BackupTask(
                                id = t.id, dayOfWeek = t.dayOfWeek, category = t.category.name,
                                topic = t.topic, targetQuestions = t.targetQuestions,
                                solvedQuestions = t.solvedQuestions, isDone = t.isDone,
                                completedAt = t.completedAt, orderIndex = t.orderIndex,
                                createdAt = t.createdAt,
                            )
                        },
                    )
                }
            },
            focusSessions = focusDao.allOnce().map { f ->
                BackupFocusSession(
                    startedAt = f.startedAt, endedAt = f.endedAt, activeMs = f.activeMs,
                    plannedMin = f.plannedMin, completed = f.completed,
                    category = f.category?.name, taskId = f.taskId, note = f.note,
                )
            },
            chatFolders = chatDao.foldersOnce().map { f ->
                BackupFolder(f.id, f.name, f.createdAt)
            },
            chatThreads = run {
                val messagesByThread = chatDao.messagesOnce().groupBy { it.threadId }
                chatDao.threadsOnce().map { th ->
                    BackupThread(
                        title = th.title,
                        createdAt = th.createdAt,
                        messages = (messagesByThread[th.id] ?: emptyList()).map { m ->
                            BackupMessage(m.role, m.content, m.model, m.createdAt)
                        },
                        pinned = th.pinned,
                        folderId = th.folderId,
                    )
                }
            },
        )
        return json.encodeToString(BackupDocument.serializer(), doc)
    }

    /** Save-dialog export. Returns the destination description, or null when the user cancelled. */
    suspend fun exportViaDialog(): String? {
        val payload = exportJson()
        val where = files.saveFile(suggestedFileName(), "json", "application/json", payload.encodeToByteArray())
            ?: return null
        settingsRepository.setLastBackupAt(clock.now().toEpochMilli())
        return where
    }

    /**
     * PRD §9.4 auto-backup: runs on app open; if a backup folder is configured and the
     * last backup is older than 7 days, writes yks_backup_YYYY-MM-DD.json into it and
     * keeps only the newest 8 backups. Failures are swallowed by the caller — a missed
     * auto-backup must never break app start.
     */
    suspend fun autoBackupIfDue() {
        val settings = settingsRepository.settings.first()
        val dirUri = settings.backupDirUri ?: return
        val nowMs = clock.now().toEpochMilli()
        val last = settings.lastBackupAt
        if (last != null && nowMs - last < AUTO_BACKUP_INTERVAL_MS) return

        val name = suggestedFileName()
        val payload = exportJson()
        // writeToDirectory replaces a same-named file (same-day rerun) on every platform.
        if (!files.writeToDirectory(dirUri, name, "application/json", payload.encodeToByteArray())) return
        settingsRepository.setLastBackupAt(nowMs)

        val entries = files.listDirectory(dirUri)
        val stale = BackupRotation.staleBackupNames(entries.map { it.name }, KEEP_BACKUPS).toSet()
        entries.filter { it.name in stale }.forEach { files.deleteFromDirectory(dirUri, it) }
    }

    /** Full replace (PRD §9.4). Writes a pre-import safety snapshot to app-private files first. */
    suspend fun importReplace(payload: String) {
        val doc = json.decodeFromString(BackupDocument.serializer(), payload)
        require(doc.format == BackupDocument.FORMAT) { "Bu dosya bir YKS yedeği değil" }
        require(doc.schemaVersion in 1..BackupDocument.SCHEMA_VERSION) {
            "Desteklenmeyen yedek sürümü: ${doc.schemaVersion} (bu uygulama en çok v${BackupDocument.SCHEMA_VERSION} okur)"
        }

        runCatching { files.writePrivateFile("pre_import_snapshot.json", exportJson()) }

        // Room KMP: one immediate write transaction; DAO calls inside join it via the coroutine context.
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
            chatDao.clearAll()
            focusDao.clearAll()
            examDao.clearAll()
            planDao.clearWeeks()
            topicDao.clearAll()
            noteDao.clearAll()
            aiProfileDao.clearAll()

            doc.notes.forEach { n ->
                noteDao.upsert(
                    com.yks2027.tracker.core.database.NoteEntity(
                        title = n.title, body = n.body,
                        createdAt = n.createdAt, updatedAt = n.updatedAt,
                    ),
                )
            }
            // Profiles restore with their original ids so the active pointer (and any
            // still-present Keystore entries on the SAME device) re-attach; on a new
            // device the keys are simply absent and must be re-entered.
            doc.aiProfiles.forEach { p ->
                aiProfileDao.upsert(
                    com.yks2027.tracker.core.database.AiProfileEntity(
                        id = p.id, name = p.name, protocol = p.protocol,
                        baseUrl = p.baseUrl, model = p.model, createdAt = p.createdAt,
                    ),
                )
            }

            doc.planWeeks.forEach { w ->
                planDao.insertWeek(PlanWeekEntity(w.weekStartDay, w.note, w.createdAt))
                planDao.insertTasks(
                    w.tasks.map { t ->
                        PlanTaskEntity(
                            id = t.id, weekStartDay = w.weekStartDay, dayOfWeek = t.dayOfWeek,
                            category = enumValueOf(t.category), topic = t.topic,
                            targetQuestions = t.targetQuestions, solvedQuestions = t.solvedQuestions,
                            isDone = t.isDone, completedAt = t.completedAt,
                            orderIndex = t.orderIndex, createdAt = t.createdAt,
                        )
                    },
                )
            }
            doc.exams.forEach { e ->
                examDao.insertExam(
                    ExamEntity(
                        id = e.id, examKind = enumValueOf(e.examKind), takenAtDay = e.takenAtDay,
                        name = e.name, publisher = e.publisher, durationMin = e.durationMin,
                        notes = e.notes, createdAt = e.createdAt, updatedAt = e.updatedAt,
                    ),
                )
                examDao.insertSections(
                    e.sections.map { s ->
                        ExamSectionEntity(
                            examId = e.id, subject = enumValueOf(s.subject),
                            questionCount = s.questionCount, correctCount = s.correctCount,
                            wrongCount = s.wrongCount, orderIndex = s.orderIndex,
                        )
                    },
                )
                if (e.topicNotes.isNotEmpty()) {
                    examDao.insertTopicNotes(
                        e.topicNotes.map { n ->
                            ExamTopicNoteEntity(
                                examId = e.id, subject = enumValueOf(n.subject),
                                note = n.note, createdAt = n.createdAt,
                            )
                        },
                    )
                }
                if (e.topicMarks.isNotEmpty()) {
                    examDao.insertTopicMarks(
                        e.topicMarks.map { m ->
                            ExamTopicMarkEntity(
                                examId = e.id, subject = enumValueOf(m.subject),
                                topicId = m.topicId, wrongCount = m.wrongCount,
                                blankCount = m.blankCount, errorType = m.errorType,
                                createdAt = m.createdAt,
                            )
                        },
                    )
                }
            }
            doc.topicStatuses.forEach { s ->
                topicDao.upsertStatus(
                    com.yks2027.tracker.core.database.TopicStatusEntity(
                        topicId = s.topicId, studied = s.studied,
                        practiced = s.practiced, reviewed = s.reviewed, updatedAt = s.updatedAt,
                    ),
                )
            }
            doc.focusSessions.forEach { f ->
                focusDao.insert(
                    FocusSessionEntity(
                        startedAt = f.startedAt, endedAt = f.endedAt, activeMs = f.activeMs,
                        plannedMin = f.plannedMin, completed = f.completed,
                        category = f.category?.let { enumValueOf<PlannerCategory>(it) },
                        taskId = f.taskId, note = f.note,
                    ),
                )
            }
            // Folders first (stable ids) so thread folder_id references resolve.
            chatDao.clearFolders()
            doc.chatFolders.forEach { f ->
                chatDao.insertFolder(
                    com.yks2027.tracker.core.database.ChatFolderEntity(
                        id = f.id, name = f.name, createdAt = f.createdAt,
                    ),
                )
            }
            val folderIds = doc.chatFolders.map { it.id }.toSet()
            doc.chatThreads.forEach { th ->
                val threadId = chatDao.insertThread(
                    ChatThreadEntity(
                        title = th.title, createdAt = th.createdAt, pinned = th.pinned,
                        folderId = resolveRestoredFolderId(th.folderId, folderIds),
                    ),
                )
                th.messages.forEach { m ->
                    chatDao.insertMessage(
                        ChatMessageEntity(
                            threadId = threadId, role = m.role, content = m.content,
                            model = m.model, createdAt = m.createdAt,
                        ),
                    )
                }
            }
            }
        }

        settingsRepository.setTytExamAt(doc.settings.tytExamAt)
        settingsRepository.setAytExamAt(doc.settings.aytExamAt)
        settingsRepository.setDatesConfirmed(doc.settings.datesConfirmed)
        settingsRepository.setThemeMode(
            runCatching { ThemeMode.valueOf(doc.settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
        )
        settingsRepository.setActiveAiProfileId(
            doc.settings.activeAiProfileId?.takeIf { id -> doc.aiProfiles.any { it.id == id } }
                ?: doc.aiProfiles.firstOrNull()?.id,
        )
    }

    companion object {
        /**
         * v1.3 orphan semantics, mirrored from the live FK (SET NULL): a thread whose
         * folder is missing from the backup falls back to "Klasörsüz" — never dropped.
         * Pure and unit-tested.
         */
        fun resolveRestoredFolderId(folderId: Long?, existingFolderIds: Set<Long>): Long? =
            folderId?.takeIf { it in existingFolderIds }

        const val APP_VERSION = "2.0.0"
        /** Exposed for the cross-platform round-trip test. */
        const val FORMAT_FOR_TESTS = BackupDocument.FORMAT
        const val AUTO_BACKUP_INTERVAL_MS = 7L * 86_400_000L
        const val KEEP_BACKUPS = 8
    }
}

/**
 * v2.0 — pure auto-backup rotation shared by SAF (Android) and plain directories (desktop):
 * keep the newest [keep] `yks_backup_YYYY-MM-DD.json` files (ISO dates sort chronologically
 * by name) and report the rest for deletion. Unit-tested.
 */
object BackupRotation {
    private val backupName = Regex("""yks_backup_\d{4}-\d{2}-\d{2}\.json""")

    fun isBackupName(name: String): Boolean = backupName.matches(name)

    fun staleBackupNames(names: List<String>, keep: Int): List<String> =
        names.filter(::isBackupName).sortedDescending().drop(keep)
}
