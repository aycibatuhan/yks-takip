package com.yks2027.tracker.core.backup

import com.yks2027.tracker.core.database.ExamEntity
import com.yks2027.tracker.core.database.ExamSectionEntity
import com.yks2027.tracker.core.database.ExamWithSections
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.NetCalculator
import com.yks2027.tracker.core.model.Subject
import java.time.LocalDate

/**
 * v1.2 — deneme CSV format (documented in README §CSV):
 *
 *   tarih;tur;ad;yayinevi;ders;soru;dogru;yanlis;bos;net
 *
 * - One row per exam SECTION (a TYT full mock = 4 rows sharing tarih+tur+ad).
 * - Separator is ';' (Turkish-locale Excel default; nets carry a decimal COMMA).
 * - tarih ISO (YYYY-MM-DD); tur/ders are the stable enum names (TYT_FULL, TYT_MATEMATIK…).
 * - bos and net are derived columns for humans; the importer recomputes and IGNORES them.
 * - Export is UTF-8 with BOM so Excel renders Turkish characters out of the box.
 *
 * Pure string-in/string-out so round-trips are unit-testable.
 */
object CsvCodec {

    const val HEADER = "tarih;tur;ad;yayinevi;ders;soru;dogru;yanlis;bos;net"
    const val BOM = "﻿"

    // --- export ---

    fun export(exams: List<ExamWithSections>): String = buildString {
        append(BOM)
        appendLine(HEADER)
        exams.sortedWith(compareBy({ it.exam.takenAtDay }, { it.exam.id })).forEach { ews ->
            ews.sections.sortedBy { it.orderIndex }.forEach { section ->
                val net = NetCalculator.netQuarters(section.correctCount, section.wrongCount)
                val blank = NetCalculator.blank(section.questionCount, section.correctCount, section.wrongCount)
                appendLine(
                    listOf(
                        LocalDate.ofEpochDay(ews.exam.takenAtDay).toString(),
                        ews.exam.examKind.name,
                        escape(ews.exam.name.orEmpty()),
                        escape(ews.exam.publisher.orEmpty()),
                        section.subject.name,
                        section.questionCount.toString(),
                        section.correctCount.toString(),
                        section.wrongCount.toString(),
                        blank.toString(),
                        NetCalculator.format(net),
                    ).joinToString(";"),
                )
            }
        }
    }

    private fun escape(field: String): String =
        if (field.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    // --- import ---

    data class ParsedExam(
        val kind: ExamKind,
        val takenAtDay: Long,
        val name: String?,
        val publisher: String?,
        /** (subject, questionCount, correct, wrong) in file order. */
        val sections: List<ParsedSection>,
    )

    data class ParsedSection(
        val subject: Subject,
        val questionCount: Int,
        val correct: Int,
        val wrong: Int,
    )

    data class ParseResult(
        val exams: List<ParsedExam>,
        /** Per-line problems; the affected lines are skipped, the rest still import. */
        val errors: List<String>,
    )

    fun parse(text: String): ParseResult {
        val errors = mutableListOf<String>()
        val lines = text.removePrefix(BOM).split('\n').map { it.trimEnd('\r') }
        if (lines.isEmpty() || !lines.first().trim().equals(HEADER, ignoreCase = true)) {
            errors += "Başlık satırı beklenen biçimde değil (beklenen: $HEADER)"
            return ParseResult(emptyList(), errors)
        }

        data class Key(val day: Long, val kind: ExamKind, val name: String?)

        val grouped = LinkedHashMap<Key, MutableList<Pair<ParsedSection, Pair<String?, String?>>>>()
        lines.drop(1).forEachIndexed { index, line ->
            val lineNo = index + 2
            if (line.isBlank()) return@forEachIndexed
            val fields = splitCsvLine(line)
            if (fields.size < 8) {
                errors += "Satır $lineNo: eksik sütun (${fields.size}/10)"
                return@forEachIndexed
            }
            val day = runCatching { LocalDate.parse(fields[0].trim()).toEpochDay() }.getOrNull()
            val kind = runCatching { ExamKind.valueOf(fields[1].trim()) }.getOrNull()
            val subject = runCatching { Subject.valueOf(fields[4].trim()) }.getOrNull()
            val count = fields[5].trim().toIntOrNull()
            val correct = fields[6].trim().toIntOrNull()
            val wrong = fields[7].trim().toIntOrNull()
            when {
                day == null -> errors += "Satır $lineNo: tarih okunamadı (${fields[0]})"
                kind == null -> errors += "Satır $lineNo: tur geçersiz (${fields[1]})"
                subject == null -> errors += "Satır $lineNo: ders geçersiz (${fields[4]})"
                count == null || count < 1 -> errors += "Satır $lineNo: soru sayısı geçersiz"
                correct == null || wrong == null || correct < 0 || wrong < 0 ->
                    errors += "Satır $lineNo: doğru/yanlış geçersiz"
                !NetCalculator.isValid(count, correct, wrong) ->
                    errors += "Satır $lineNo: doğru+yanlış ($correct+$wrong) soru sayısını ($count) aşıyor"
                else -> {
                    val name = fields[2].trim().ifBlank { null }
                    val publisher = fields[3].trim().ifBlank { null }
                    grouped.getOrPut(Key(day, kind, name)) { mutableListOf() }
                        .add(ParsedSection(subject, count, correct, wrong) to (name to publisher))
                }
            }
        }

        val exams = grouped.mapNotNull { (key, rows) ->
            val sections = rows.map { it.first }
            if (sections.map { it.subject }.toSet().size != sections.size) {
                errors += "${LocalDate.ofEpochDay(key.day)} ${key.kind.name}: aynı ders iki kez geçiyor — deneme atlandı"
                return@mapNotNull null
            }
            if (!key.kind.isFull && sections.size != 1) {
                errors += "${LocalDate.ofEpochDay(key.day)} ${key.kind.name}: branş denemesi tek ders olmalı — deneme atlandı"
                return@mapNotNull null
            }
            ParsedExam(
                kind = key.kind,
                takenAtDay = key.day,
                name = key.name,
                publisher = rows.firstNotNullOfOrNull { it.second.second },
                sections = sections,
            )
        }
        return ParseResult(exams, errors)
    }

    /** Split one line on ';' honoring double-quote escaping. */
    fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ';' && !inQuotes -> {
                    out += current.toString(); current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }

    /** Converts a parsed exam to entities for the additive insert (id 0 = new rows). */
    fun toEntities(parsed: ParsedExam, nowMs: Long): Pair<ExamEntity, List<ExamSectionEntity>> {
        val exam = ExamEntity(
            id = 0,
            examKind = parsed.kind,
            takenAtDay = parsed.takenAtDay,
            name = parsed.name,
            publisher = parsed.publisher,
            durationMin = null,
            notes = null,
            createdAt = nowMs,
            updatedAt = nowMs,
        )
        val sections = parsed.sections.mapIndexed { index, s ->
            ExamSectionEntity(
                examId = 0,
                subject = s.subject,
                questionCount = s.questionCount,
                correctCount = s.correct,
                wrongCount = s.wrong,
                orderIndex = index,
            )
        }
        return exam to sections
    }
}
