package com.yks2027.tracker.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.PlannerCategory
import com.yks2027.tracker.core.model.Subject

/**
 * Schema v1 per PRD §9.2. Stores only non-recomputable inputs — nets, boş, totals,
 * accuracy are all derived at read time (PRD §9.1). Enums persist as TEXT via Room's
 * built-in enum support. Input invariants (D+Y ≤ max, branş = exactly one section)
 * are enforced in the entry use case — Room has no CHECK constraints.
 */

@Entity(
    tableName = "exams",
    indices = [Index(value = ["exam_kind", "taken_at_day"])],
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exam_kind") val examKind: ExamKind,
    @ColumnInfo(name = "taken_at_day") val takenAtDay: Long,
    val name: String?,
    val publisher: String?,
    @ColumnInfo(name = "duration_min") val durationMin: Int?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "exam_sections",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["exam_id", "subject"], unique = true),
        Index(value = ["subject"]),
    ],
)
data class ExamSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exam_id") val examId: Long,
    val subject: Subject,
    @ColumnInfo(name = "question_count") val questionCount: Int,
    @ColumnInfo(name = "correct_count") val correctCount: Int,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
)

data class ExamWithSections(
    @Embedded val exam: ExamEntity,
    @Relation(parentColumn = "id", entityColumn = "exam_id")
    val sections: List<ExamSectionEntity>,
)

@Entity(tableName = "plan_weeks")
data class PlanWeekEntity(
    /** Natural key: epochDay of the week's Monday, Istanbul (PRD §6.1). */
    @PrimaryKey @ColumnInfo(name = "week_start_day") val weekStartDay: Long,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "plan_tasks",
    foreignKeys = [
        ForeignKey(
            entity = PlanWeekEntity::class,
            parentColumns = ["week_start_day"],
            childColumns = ["week_start_day"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["week_start_day", "day_of_week", "order_index"])],
)
data class PlanTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "week_start_day") val weekStartDay: Long,
    /** ISO: 1 = Pazartesi .. 7 = Pazar. */
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    val category: PlannerCategory,
    val topic: String,
    @ColumnInfo(name = "target_questions") val targetQuestions: Int?,
    @ColumnInfo(name = "solved_questions") val solvedQuestions: Int?,
    @ColumnInfo(name = "is_done") val isDone: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = PlanTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["started_at"]), Index(value = ["task_id"])],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    /** Net of pauses — the one stored "derived" value (PRD §9.1). */
    @ColumnInfo(name = "active_ms") val activeMs: Long,
    @ColumnInfo(name = "planned_min") val plannedMin: Int,
    /** true = ran to zero, false = abandoned early. */
    val completed: Boolean,
    val category: PlannerCategory?,
    @ColumnInfo(name = "task_id") val taskId: Long?,
    val note: String?,
)

/**
 * Schema v2 (M3, PRD §12): per-exam "eksik konu" quick tags feeding the weakness list
 * in Ders Analizi. Added via MIGRATION_1_2 — purely additive.
 */
@Entity(
    tableName = "exam_topic_notes",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exam_id"]), Index(value = ["subject"])],
)
data class ExamTopicNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exam_id") val examId: Long,
    val subject: Subject,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Schema v3 (M4): structured per-exam topic marks — which catalog topics produced
 * wrongs/blanks, with an optional error-type diagnosis (bilgi/işlem/dikkat/süre).
 * Supersedes free-text exam_topic_notes for new entries (old rows remain readable).
 */
@Entity(
    tableName = "exam_topic_marks",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exam_id"]), Index(value = ["topic_id"]), Index(value = ["subject"])],
)
data class ExamTopicMarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exam_id") val examId: Long,
    val subject: Subject,
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "blank_count") val blankCount: Int,
    /** Nullable ErrorType name — the diagnosis chip is optional. */
    @ColumnInfo(name = "error_type") val errorType: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Schema v3 (M4): the konu takip çizelgesi — per-topic study states. */
@Entity(tableName = "topic_status")
data class TopicStatusEntity(
    @PrimaryKey @ColumnInfo(name = "topic_id") val topicId: String,
    val studied: Boolean = false,
    val practiced: Boolean = false,
    val reviewed: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    // v2.1 (schema v6, additive): "needs review" flag, self-assessed confidence (0 = not set,
    // 1 = zayıf, 2 = orta, 3 = iyi) and the last time "çalıştım" was ticked.
    @ColumnInfo(name = "needs_review", defaultValue = "0") val needsReview: Boolean = false,
    @ColumnInfo(name = "confidence", defaultValue = "0") val confidence: Int = 0,
    @ColumnInfo(name = "last_studied_at") val lastStudiedAt: Long? = null,
)

/**
 * Schema v4 (v1.2): AI provider profiles. The single-slot v1.0 config (one provider
 * enum + one key) misrouted keys when the provider didn't match; profiles bind a
 * name/protocol/baseUrl/model together and each profile's API key lives separately in
 * the Keystore-encrypted store, keyed by this row's id (never in the DB, never in backups).
 */
@Entity(tableName = "ai_profiles")
data class AiProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** AiProtocol name: ANTHROPIC | OPENAI_COMPAT. */
    val protocol: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    val model: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Schema v4 (v1.2): markdown notes (Notlar destination + "Nota kaydet" from AI Koç). */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Schema v5 (v1.3): chat folders — deleting one NEVER deletes threads (FK SET NULL). */
@Entity(tableName = "chat_folders")
data class ChatFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "chat_threads",
    foreignKeys = [
        ForeignKey(
            entity = ChatFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["folder_id"])],
)
data class ChatThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** v1.3: pinned threads sort first; ordering itself is a query concern. */
    val pinned: Boolean = false,
    /** v1.3: null = "Klasörsüz". */
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["thread_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["thread_id", "created_at"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    /** "user" | "assistant" */
    val role: String,
    val content: String,
    val model: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
