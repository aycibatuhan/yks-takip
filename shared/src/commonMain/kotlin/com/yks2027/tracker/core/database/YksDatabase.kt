package com.yks2027.tracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema v2. v1 shipped with M1/M2; MIGRATION_1_2 adds the exam_topic_notes table
 * (additive only — PRD §12 M3). exportSchema stays on; schemas/ is committed so every
 * change remains a testable migration.
 */
@Database(
    entities = [
        ExamEntity::class,
        ExamSectionEntity::class,
        PlanWeekEntity::class,
        PlanTaskEntity::class,
        FocusSessionEntity::class,
        ExamTopicNoteEntity::class,
        ExamTopicMarkEntity::class,
        TopicStatusEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        ChatFolderEntity::class,
        AiProfileEntity::class,
        NoteEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class YksDatabase : RoomDatabase() {
    abstract fun examDao(): ExamDao
    abstract fun planDao(): PlanDao
    abstract fun focusDao(): FocusDao
    abstract fun topicDao(): TopicDao
    abstract fun chatDao(): ChatDao
    abstract fun aiProfileDao(): AiProfileDao
    abstract fun noteDao(): NoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exam_topic_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `exam_id` INTEGER NOT NULL,
                        `subject` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`exam_id`) REFERENCES `exams`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exam_topic_notes_exam_id` ON `exam_topic_notes` (`exam_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exam_topic_notes_subject` ON `exam_topic_notes` (`subject`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exam_topic_marks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `exam_id` INTEGER NOT NULL,
                        `subject` TEXT NOT NULL,
                        `topic_id` TEXT NOT NULL,
                        `wrong_count` INTEGER NOT NULL,
                        `blank_count` INTEGER NOT NULL,
                        `error_type` TEXT,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`exam_id`) REFERENCES `exams`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_topic_marks_exam_id` ON `exam_topic_marks` (`exam_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_topic_marks_topic_id` ON `exam_topic_marks` (`topic_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_topic_marks_subject` ON `exam_topic_marks` (`subject`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `topic_status` (
                        `topic_id` TEXT NOT NULL,
                        `studied` INTEGER NOT NULL,
                        `practiced` INTEGER NOT NULL,
                        `reviewed` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`topic_id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v1.2: ai_profiles + notes — purely additive, SQL matches schemas/4.json. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `protocol` TEXT NOT NULL,
                        `base_url` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v1.3: chat_folders + chat_threads.pinned/folder_id. SQLite can't ALTER a
         * foreign key onto an existing table, so chat_threads is recreated (Room runs
         * migrations before FK enforcement switches on, so the child chat_messages
         * rows are untouched and re-resolve by table name after the rename). Data-wise
         * still purely additive — every existing row/column value is preserved.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_folders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_threads_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `pinned` INTEGER NOT NULL,
                        `folder_id` INTEGER,
                        FOREIGN KEY(`folder_id`) REFERENCES `chat_folders`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO `chat_threads_new` (`id`, `title`, `created_at`, `pinned`, `folder_id`) " +
                        "SELECT `id`, `title`, `created_at`, 0, NULL FROM `chat_threads`",
                )
                db.execSQL("DROP TABLE `chat_threads`")
                db.execSQL("ALTER TABLE `chat_threads_new` RENAME TO `chat_threads`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_threads_folder_id` ON `chat_threads` (`folder_id`)")
            }
        }
    }
}
