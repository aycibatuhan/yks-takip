package com.yks2027.tracker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.PlannerCategory
import com.yks2027.tracker.core.model.Subject
import kotlinx.coroutines.flow.Flow

/** One point of the total-net trend, in quarter-units (PRD §9.2 chart feed query). */
data class TrendPoint(
    val id: Long,
    val takenAtDay: Long,
    val name: String?,
    val totalNetQuarters: Int,
)

/** One section result across time — full mocks AND branş denemes (PRD §4.4 M2). */
data class SubjectPoint(
    val takenAtDay: Long,
    val name: String?,
    val examKind: ExamKind,
    val questionCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
)

/** Per-week task rollup for the Geçmiş Haftalar screen. */
data class WeekAggregate(
    val weekStartDay: Long,
    val total: Int,
    val done: Int,
    val target: Int,
    val solved: Int,
)

/** Weekly study time per planner category, from focus sessions. */
data class CategoryActiveMs(
    val category: PlannerCategory?,
    val totalMs: Long,
)

/** v1.2 — minimal session projection for per-day bucketing (Geçmiş Haftalar sparkline). */
data class SessionSlice(
    val startedAt: Long,
    val activeMs: Long,
)

/** An eksik-konu tag joined with its exam's date, for the weakness list (M3). */
data class SubjectNote(
    val id: Long,
    val note: String,
    val takenAtDay: Long,
)

/**
 * M4 — one aggregation row of the weak-topic ranking: totals per (topic, errorType).
 * The ViewModel folds errorType rows into per-topic summaries.
 */
data class WeakTopicRow(
    val topicId: String,
    val subject: Subject,
    val errorType: String?,
    val wrongSum: Int,
    val blankSum: Int,
    val examCount: Int,
    val lastDay: Long,
)

@Dao
interface ExamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: ExamEntity): Long

    @Insert
    suspend fun insertSections(sections: List<ExamSectionEntity>)

    @Query("DELETE FROM exam_sections WHERE exam_id = :examId")
    suspend fun deleteSectionsFor(examId: Long)

    /**
     * Create or update in one transaction. REPLACE on the exam row cascades the old
     * sections away; we re-insert the new set with the resolved exam id.
     */
    @Transaction
    suspend fun upsertExamWithSections(exam: ExamEntity, sections: List<ExamSectionEntity>): Long {
        val rowId = insertExam(exam)
        val examId = if (exam.id != 0L) exam.id else rowId
        deleteSectionsFor(examId)
        insertSections(sections.map { it.copy(id = 0, examId = examId) })
        return examId
    }

    @Transaction
    @Query("SELECT * FROM exams ORDER BY taken_at_day DESC, id DESC")
    fun observeAll(): Flow<List<ExamWithSections>>

    @Transaction
    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getById(id: Long): ExamWithSections?

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM exams WHERE exam_kind = :kind AND taken_at_day = :day AND id != :excludeId")
    suspend fun countByKindAndDay(kind: ExamKind, day: Long, excludeId: Long): Int

    @Query(
        """
        SELECT e.id AS id, e.taken_at_day AS takenAtDay, e.name AS name,
               SUM(s.correct_count * 4 - s.wrong_count) AS totalNetQuarters
        FROM exams e JOIN exam_sections s ON s.exam_id = e.id
        WHERE e.exam_kind = :kind
        GROUP BY e.id
        ORDER BY e.taken_at_day, e.id
        """
    )
    fun observeTrend(kind: ExamKind): Flow<List<TrendPoint>>

    @Query(
        """
        SELECT e.taken_at_day AS takenAtDay, e.name AS name, e.exam_kind AS examKind,
               s.question_count AS questionCount, s.correct_count AS correctCount,
               s.wrong_count AS wrongCount
        FROM exam_sections s JOIN exams e ON e.id = s.exam_id
        WHERE s.subject = :subject
        ORDER BY e.taken_at_day, e.id
        """
    )
    fun observeSubjectHistory(subject: Subject): Flow<List<SubjectPoint>>

    @Query("SELECT COUNT(*) FROM exams")
    fun observeCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM exams ORDER BY taken_at_day, id")
    suspend fun allOnce(): List<ExamWithSections>

    @Query("DELETE FROM exams")
    suspend fun clearAll()

    // --- Coaching report (M3): full-mock totals inside a week's day range. ---
    @Query(
        """
        SELECT e.id AS id, e.taken_at_day AS takenAtDay, e.name AS name,
               SUM(s.correct_count * 4 - s.wrong_count) AS totalNetQuarters
        FROM exams e JOIN exam_sections s ON s.exam_id = e.id
        WHERE e.exam_kind = :kind AND e.taken_at_day BETWEEN :fromDay AND :toDay
        GROUP BY e.id ORDER BY e.taken_at_day, e.id
        """
    )
    suspend fun trendBetween(kind: ExamKind, fromDay: Long, toDay: Long): List<TrendPoint>

    /** Streak source: days on which an exam was logged. */
    @Query("SELECT DISTINCT created_at FROM exams")
    fun observeCreatedTimes(): Flow<List<Long>>

    // --- Eksik-konu tags (schema v2, M3). ---
    @Insert
    suspend fun insertTopicNotes(notes: List<ExamTopicNoteEntity>)

    @Query("DELETE FROM exam_topic_notes WHERE exam_id = :examId")
    suspend fun deleteTopicNotesForExam(examId: Long)

    @Query("SELECT * FROM exam_topic_notes WHERE exam_id = :examId ORDER BY id")
    suspend fun topicNotesForExam(examId: Long): List<ExamTopicNoteEntity>

    @Query(
        """
        SELECT n.id AS id, n.note AS note, e.taken_at_day AS takenAtDay
        FROM exam_topic_notes n JOIN exams e ON e.id = n.exam_id
        WHERE n.subject = :subject
        ORDER BY e.taken_at_day DESC, n.id DESC LIMIT 20
        """
    )
    fun observeNotesBySubject(subject: Subject): Flow<List<SubjectNote>>

    @Query("SELECT * FROM exam_topic_notes ORDER BY exam_id, id")
    suspend fun allTopicNotesOnce(): List<ExamTopicNoteEntity>

    // --- Structured topic marks (schema v3, M4). ---
    @Insert
    suspend fun insertTopicMarks(marks: List<ExamTopicMarkEntity>)

    @Query("DELETE FROM exam_topic_marks WHERE exam_id = :examId")
    suspend fun deleteTopicMarksForExam(examId: Long)

    @Query("SELECT * FROM exam_topic_marks WHERE exam_id = :examId ORDER BY id")
    suspend fun topicMarksForExam(examId: Long): List<ExamTopicMarkEntity>

    @Query(
        """
        SELECT m.topic_id AS topicId, m.subject AS subject, m.error_type AS errorType,
               SUM(m.wrong_count) AS wrongSum, SUM(m.blank_count) AS blankSum,
               COUNT(DISTINCT m.exam_id) AS examCount, MAX(e.taken_at_day) AS lastDay
        FROM exam_topic_marks m JOIN exams e ON e.id = m.exam_id
        GROUP BY m.topic_id, m.error_type
        """
    )
    fun observeWeakTopicRows(): Flow<List<WeakTopicRow>>

    @Query(
        """
        SELECT m.topic_id AS topicId, m.subject AS subject, m.error_type AS errorType,
               SUM(m.wrong_count) AS wrongSum, SUM(m.blank_count) AS blankSum,
               COUNT(DISTINCT m.exam_id) AS examCount, MAX(e.taken_at_day) AS lastDay
        FROM exam_topic_marks m JOIN exams e ON e.id = m.exam_id
        GROUP BY m.topic_id, m.error_type
        """
    )
    suspend fun weakTopicRowsOnce(): List<WeakTopicRow>

    @Query("SELECT * FROM exam_topic_marks ORDER BY exam_id, id")
    suspend fun allTopicMarksOnce(): List<ExamTopicMarkEntity>
}

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatus(status: TopicStatusEntity)

    @Query("SELECT * FROM topic_status")
    fun observeStatuses(): Flow<List<TopicStatusEntity>>

    @Query("SELECT * FROM topic_status ORDER BY topic_id")
    suspend fun statusesOnce(): List<TopicStatusEntity>

    @Query("DELETE FROM topic_status")
    suspend fun clearAll()
}

@Dao
interface PlanDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWeek(week: PlanWeekEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM plan_weeks WHERE week_start_day = :week)")
    suspend fun weekExists(week: Long): Boolean

    @Query("SELECT MAX(week_start_day) FROM plan_weeks WHERE week_start_day < :week")
    suspend fun latestWeekBefore(week: Long): Long?

    @Query("SELECT COUNT(*) FROM plan_tasks WHERE week_start_day = :week")
    suspend fun taskCount(week: Long): Int

    @Query("SELECT * FROM plan_tasks WHERE week_start_day = :week ORDER BY day_of_week, order_index")
    fun observeTasks(week: Long): Flow<List<PlanTaskEntity>>

    @Query("SELECT * FROM plan_tasks WHERE week_start_day = :week ORDER BY day_of_week, order_index")
    suspend fun tasksOnce(week: Long): List<PlanTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: PlanTaskEntity): Long

    @Insert
    suspend fun insertTasks(tasks: List<PlanTaskEntity>)

    @Query("UPDATE plan_tasks SET is_done = 0, completed_at = NULL, solved_questions = NULL WHERE week_start_day = :week")
    suspend fun resetTicks(week: Long)

    @Query("DELETE FROM plan_tasks WHERE week_start_day = :week")
    suspend fun deleteTasksOfWeek(week: Long)

    @Query("DELETE FROM plan_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("UPDATE plan_tasks SET is_done = :done, completed_at = :completedAt, solved_questions = :solved WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?, solved: Int?)

    @Query("SELECT MAX(order_index) FROM plan_tasks WHERE week_start_day = :week AND day_of_week = :day")
    suspend fun maxOrder(week: Long, day: Int): Int?

    @Query("SELECT * FROM plan_weeks ORDER BY week_start_day DESC")
    fun observeWeeks(): Flow<List<PlanWeekEntity>>

    @Query(
        """
        SELECT week_start_day AS weekStartDay, COUNT(*) AS total,
               SUM(is_done) AS done,
               COALESCE(SUM(target_questions), 0) AS target,
               COALESCE(SUM(solved_questions), 0) AS solved
        FROM plan_tasks GROUP BY week_start_day
        """
    )
    fun observeWeekAggregates(): Flow<List<WeekAggregate>>

    /** Streak source: days on which a task was completed. */
    @Query("SELECT DISTINCT completed_at FROM plan_tasks WHERE completed_at IS NOT NULL")
    fun observeCompletionTimes(): Flow<List<Long>>

    @Query("SELECT * FROM plan_weeks ORDER BY week_start_day")
    suspend fun weeksOnce(): List<PlanWeekEntity>

    @Query("SELECT * FROM plan_tasks ORDER BY week_start_day, day_of_week, order_index")
    suspend fun allTasksOnce(): List<PlanTaskEntity>

    @Query("DELETE FROM plan_weeks")
    suspend fun clearWeeks()
}

@Dao
interface FocusDao {

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<FocusSessionEntity>>

    @Query(
        """
        SELECT category AS category, SUM(active_ms) AS totalMs
        FROM focus_sessions WHERE started_at >= :fromMs AND started_at < :toMs
        GROUP BY category ORDER BY totalMs DESC
        """
    )
    fun observeActiveMsByCategory(fromMs: Long, toMs: Long): Flow<List<CategoryActiveMs>>

    @Query("SELECT COALESCE(SUM(active_ms), 0) FROM focus_sessions WHERE started_at >= :fromMs AND started_at < :toMs")
    suspend fun totalActiveMs(fromMs: Long, toMs: Long): Long

    /** v1.2 — raw (started_at, active_ms) pairs in a range; day-bucketing happens in Kotlin. */
    @Query("SELECT started_at AS startedAt, active_ms AS activeMs FROM focus_sessions WHERE started_at >= :fromMs AND started_at < :toMs")
    suspend fun sessionSlicesBetween(fromMs: Long, toMs: Long): List<SessionSlice>

    /** Streak source: days with a logged focus session. */
    @Query("SELECT DISTINCT started_at FROM focus_sessions")
    fun observeStartTimes(): Flow<List<Long>>

    @Query("SELECT * FROM focus_sessions ORDER BY started_at")
    suspend fun allOnce(): List<FocusSessionEntity>

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAll()
}

@Dao
interface AiProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AiProfileEntity): Long

    @Query("SELECT * FROM ai_profiles ORDER BY created_at, id")
    fun observeAll(): Flow<List<AiProfileEntity>>

    @Query("SELECT * FROM ai_profiles ORDER BY created_at, id")
    suspend fun allOnce(): List<AiProfileEntity>

    @Query("SELECT * FROM ai_profiles WHERE id = :id")
    suspend fun byId(id: Long): AiProfileEntity?

    @Query("DELETE FROM ai_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM ai_profiles")
    suspend fun count(): Int

    @Query("DELETE FROM ai_profiles")
    suspend fun clearAll()
}

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Query("SELECT * FROM notes ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: Long): NoteEntity?

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM notes ORDER BY created_at, id")
    suspend fun allOnce(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun clearAll()
}

/**
 * v1.3 — one sessions-pane row: thread + aggregates. Ordering (pinned first, then last
 * MESSAGE activity) happens in SessionListLogic (pure Kotlin, unit-tested), not in SQL.
 */
data class ThreadOverview(
    val id: Long,
    val title: String?,
    val createdAt: Long,
    val pinned: Boolean,
    val folderId: Long?,
    val messageCount: Int,
    /** Last message time; threads with no messages fall back to creation time. */
    val lastActivity: Long,
    val lastSnippet: String?,
)

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThreadEntity): Long

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_threads ORDER BY created_at DESC")
    fun observeThreads(): Flow<List<ChatThreadEntity>>

    // --- v1.3 sessions pane ---

    @Query(
        """
        SELECT t.id AS id, t.title AS title, t.created_at AS createdAt,
               t.pinned AS pinned, t.folder_id AS folderId,
               COUNT(m.id) AS messageCount,
               COALESCE(MAX(m.created_at), t.created_at) AS lastActivity,
               (SELECT content FROM chat_messages
                WHERE thread_id = t.id ORDER BY created_at DESC, id DESC LIMIT 1) AS lastSnippet
        FROM chat_threads t LEFT JOIN chat_messages m ON m.thread_id = t.id
        GROUP BY t.id
        """
    )
    fun observeThreadOverviews(): Flow<List<ThreadOverview>>

    /**
     * Content search: ASCII-case-insensitive LIKE (spec'd trade-off — no FTS at this
     * scale). Turkish-aware title matching happens Kotlin-side in SessionListLogic.
     */
    @Query(
        """
        SELECT DISTINCT thread_id FROM chat_messages
        WHERE LOWER(content) LIKE '%' || LOWER(:query) || '%'
        """
    )
    fun observeContentMatchThreadIds(query: String): Flow<List<Long>>

    @Query("UPDATE chat_threads SET title = :title WHERE id = :threadId")
    suspend fun renameThread(threadId: Long, title: String)

    @Query("UPDATE chat_threads SET pinned = :pinned WHERE id = :threadId")
    suspend fun setThreadPinned(threadId: Long, pinned: Boolean)

    @Query("UPDATE chat_threads SET folder_id = :folderId WHERE id = :threadId")
    suspend fun setThreadFolder(threadId: Long, folderId: Long?)

    @Query("SELECT * FROM chat_threads WHERE id = :threadId")
    suspend fun threadById(threadId: Long): ChatThreadEntity?

    // --- v1.3 folders (FK SET NULL: deleting a folder never touches threads) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: ChatFolderEntity): Long

    @Query("SELECT * FROM chat_folders ORDER BY created_at, id")
    fun observeFolders(): Flow<List<ChatFolderEntity>>

    @Query("SELECT * FROM chat_folders ORDER BY created_at, id")
    suspend fun foldersOnce(): List<ChatFolderEntity>

    @Query("UPDATE chat_folders SET name = :name WHERE id = :folderId")
    suspend fun renameFolder(folderId: Long, name: String)

    @Query("DELETE FROM chat_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    @Query("DELETE FROM chat_folders")
    suspend fun clearFolders()

    @Query("SELECT * FROM chat_messages WHERE thread_id = :threadId ORDER BY created_at, id")
    fun observeMessages(threadId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE thread_id = :threadId ORDER BY created_at, id")
    suspend fun messagesForThreadOnce(threadId: Long): List<ChatMessageEntity>

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("SELECT * FROM chat_threads ORDER BY created_at")
    suspend fun threadsOnce(): List<ChatThreadEntity>

    @Query("SELECT * FROM chat_messages ORDER BY thread_id, created_at")
    suspend fun messagesOnce(): List<ChatMessageEntity>

    @Query("DELETE FROM chat_threads")
    suspend fun clearAll()
}
