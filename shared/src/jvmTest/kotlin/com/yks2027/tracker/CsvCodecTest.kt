package com.yks2027.tracker

import com.yks2027.tracker.core.backup.CsvCodec
import com.yks2027.tracker.core.database.ExamEntity
import com.yks2027.tracker.core.database.ExamSectionEntity
import com.yks2027.tracker.core.database.ExamWithSections
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.Subject
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.2 — deneme CSV: documented format, quoting, and a full export→parse round-trip. */
class CsvCodecTest {

    private fun exam(
        kind: ExamKind,
        day: String,
        name: String?,
        publisher: String?,
        sections: List<Triple<Subject, Int, Pair<Int, Int>>>,
    ) = ExamWithSections(
        exam = ExamEntity(
            id = 1, examKind = kind, takenAtDay = LocalDate.parse(day).toEpochDay(),
            name = name, publisher = publisher, durationMin = null, notes = null,
            createdAt = 0, updatedAt = 0,
        ),
        sections = sections.mapIndexed { i, (subject, count, dy) ->
            ExamSectionEntity(
                id = i.toLong(), examId = 1, subject = subject, questionCount = count,
                correctCount = dy.first, wrongCount = dy.second, orderIndex = i,
            )
        },
    )

    private val tytFull = exam(
        ExamKind.TYT_FULL, "2026-08-22", "TYT Deneme 8", "Limit",
        listOf(
            Triple(Subject.TYT_TURKCE, 40, 30 to 6),
            Triple(Subject.TYT_SOSYAL, 20, 12 to 5),
            Triple(Subject.TYT_MATEMATIK, 40, 28 to 8),
            Triple(Subject.TYT_FEN, 20, 11 to 4),
        ),
    )

    @Test
    fun exportHasHeaderBomAndTurkishCommaNet() {
        val csv = CsvCodec.export(listOf(tytFull))
        assertTrue(csv.startsWith(CsvCodec.BOM + CsvCodec.HEADER))
        // 28D/8Y → net 26,00 with a decimal comma (that's WHY the separator is ';').
        assertTrue(csv.contains("TYT_MATEMATIK;40;28;8;4;26,00"))
    }

    @Test
    fun roundTripPreservesEverything() {
        val brans = exam(
            ExamKind.BRANS_AYT, "2026-08-20", "Fizik Branş", null,
            listOf(Triple(Subject.AYT_FIZIK, 30, 21 to 6)),
        )
        val result = CsvCodec.parse(CsvCodec.export(listOf(tytFull, brans)))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(2, result.exams.size)

        val parsedTyt = result.exams.first { it.kind == ExamKind.TYT_FULL }
        assertEquals(LocalDate.parse("2026-08-22").toEpochDay(), parsedTyt.takenAtDay)
        assertEquals("TYT Deneme 8", parsedTyt.name)
        assertEquals("Limit", parsedTyt.publisher)
        assertEquals(4, parsedTyt.sections.size)
        val mat = parsedTyt.sections.first { it.subject == Subject.TYT_MATEMATIK }
        assertEquals(40, mat.questionCount)
        assertEquals(28, mat.correct)
        assertEquals(8, mat.wrong)

        val parsedBrans = result.exams.first { it.kind == ExamKind.BRANS_AYT }
        assertEquals(1, parsedBrans.sections.size)
        assertEquals(Subject.AYT_FIZIK, parsedBrans.sections.single().subject)
    }

    @Test
    fun quotedFieldsWithSeparatorAndQuotesSurvive() {
        val tricky = exam(
            ExamKind.BRANS_TYT, "2026-08-01", "Ad; \"tırnaklı\" deneme", "Yayın;evi",
            listOf(Triple(Subject.TYT_MATEMATIK, 40, 20 to 10)),
        )
        val result = CsvCodec.parse(CsvCodec.export(listOf(tricky)))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals("Ad; \"tırnaklı\" deneme", result.exams.single().name)
        assertEquals("Yayın;evi", result.exams.single().publisher)
    }

    @Test
    fun invalidRowsAreSkippedWithLineNumbersAndRestImports() {
        val csv = CsvCodec.HEADER + "\n" +
            "2026-08-22;TYT_FULL;;;TYT_MATEMATIK;40;28;8;4;26,00\n" +
            "not-a-date;TYT_FULL;;;TYT_TURKCE;40;30;5;5;28,75\n" + // bad date
            "2026-08-22;TYT_FULL;;;TYT_FEN;20;15;9;0;12,75\n" // D+Y > count
        val result = CsvCodec.parse(csv)
        assertEquals(1, result.exams.size)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors[0].contains("Satır 3"))
        assertTrue(result.errors[1].contains("Satır 4"))
    }

    @Test
    fun duplicateSubjectWithinOneExamRejectsTheExam() {
        val csv = CsvCodec.HEADER + "\n" +
            "2026-08-22;TYT_FULL;X;;TYT_MATEMATIK;40;28;8;4;26,00\n" +
            "2026-08-22;TYT_FULL;X;;TYT_MATEMATIK;40;30;5;5;28,75\n"
        val result = CsvCodec.parse(csv)
        assertEquals(0, result.exams.size)
        assertTrue(result.errors.any { it.contains("aynı ders") })
    }

    @Test
    fun missingHeaderFailsFast() {
        val result = CsvCodec.parse("tarih,tur\n2026-01-01,TYT_FULL")
        assertTrue(result.exams.isEmpty())
        assertTrue(result.errors.single().contains("Başlık"))
    }

    @Test
    fun derivedColumnsAreIgnoredOnImport() {
        // bos/net columns lie on purpose; the parser must not care.
        val csv = CsvCodec.HEADER + "\n" +
            "2026-08-22;BRANS_TYT;;;TYT_MATEMATIK;40;28;8;99;-99,75\n"
        val result = CsvCodec.parse(csv)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(28, result.exams.single().sections.single().correct)
    }
}
