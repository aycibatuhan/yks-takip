package com.yks2027.tracker

import com.yks2027.tracker.core.backup.BackupManager
import com.yks2027.tracker.core.database.ChatDao
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.NoteDao
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

/**
 * Cross-platform backup (spec): a backup exported by the ANDROID app (v1.3/v2.0 JSON at
 * `YKS_ANDROID_BACKUP`) imports on the desktop Room database with identical counts, and the
 * desktop export (`YKS_DESKTOP_EXPORT`, later imported on the emulator) carries the same
 * format v5 structure. Keys never appear in either file.
 */
class DesktopBackupRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun androidBackupImportsWithIdenticalCounts() {
        val path = System.getenv("YKS_ANDROID_BACKUP"); assumeTrue("set YKS_ANDROID_BACKUP", path != null && File(path).exists())
        DesktopTestApp.ensureStarted()
        val koin = GlobalContext.get()
        val payload = File(path!!).readText()
        val doc = json.parseToJsonElement(payload).jsonObject
        runBlocking { koin.get<BackupManager>().importReplace(payload) }
        val exams = runBlocking { koin.get<ExamDao>().allOnce() }
        val threads = runBlocking { koin.get<ChatDao>().threadsOnce() }
        val messages = runBlocking { koin.get<ChatDao>().messagesOnce() }
        val notes = runBlocking { koin.get<NoteDao>().allOnce() }
        assertEquals(doc["exams"]!!.jsonArray.size, exams.size)
        assertEquals(doc["exams"]!!.jsonArray.sumOf { it.jsonObject["sections"]!!.jsonArray.size }, exams.sumOf { it.sections.size })
        assertEquals((doc["chat_threads"] ?: doc["chatThreads"])!!.jsonArray.size, threads.size)
        assertEquals((doc["chat_threads"] ?: doc["chatThreads"])!!.jsonArray.sumOf { it.jsonObject["messages"]!!.jsonArray.size }, messages.size)
        assertEquals(doc["notes"]!!.jsonArray.size, notes.size)
        println("[backup] android→desktop: exams=${exams.size} threads=${threads.size} messages=${messages.size} notes=${notes.size}")
    }

    @Test
    fun desktopExportHasFormatV5AndNoKeys() {
        DesktopTestApp.ensureStarted()
        val koin = GlobalContext.get()
        val payload = runBlocking { koin.get<BackupManager>().exportJson() }
        val doc = json.parseToJsonElement(payload).jsonObject
        assertEquals(BackupManager.FORMAT_FOR_TESTS, doc["format"]!!.jsonPrimitive.content)
        assertEquals(5, doc["schema_version"]!!.jsonPrimitive.content.toInt())
        assertFalse(payload.contains("api_key", ignoreCase = true))
        assertFalse(payload.contains("sk-", ignoreCase = false))
        System.getenv("YKS_DESKTOP_EXPORT")?.let { File(it).writeText(payload); println("[backup] desktop export → $it (${payload.length} chars)") }
    }
}
