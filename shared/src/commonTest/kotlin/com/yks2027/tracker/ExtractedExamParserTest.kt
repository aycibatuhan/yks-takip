package com.yks2027.tracker

import com.yks2027.tracker.core.ai.AiException
import com.yks2027.tracker.core.ai.ExtractedExamParser
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.Subject
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.2 — the strict-JSON contract parser behind AI extraction. */
class ExtractedExamParserTest {

    @Test
    fun cleanJsonParses() {
        val result = ExtractedExamParser.parse(
            """
            {"kind":"AYT_SAY_FULL","date":"2026-08-16","name":"AYT Deneme 5","publisher":"3D",
             "sections":[
               {"subject":"AYT_MATEMATIK","correct":25,"wrong":9,"question_count":40},
               {"subject":"AYT_FIZIK","correct":8,"wrong":4,"question_count":14},
               {"subject":"AYT_KIMYA","correct":9,"wrong":2,"question_count":13},
               {"subject":"AYT_BIYOLOJI","correct":10,"wrong":1,"question_count":13}]}
            """.trimIndent(),
        )
        assertEquals(ExamKind.AYT_SAY_FULL, result.kind)
        assertEquals(LocalDate.parse("2026-08-16"), result.date)
        assertEquals("AYT Deneme 5", result.name)
        assertEquals(4, result.sections.size)
        assertEquals(25, result.sections.first { it.subject == Subject.AYT_MATEMATIK }.correct)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun codeFencesAndProseAroundJsonAreTolerated() {
        val result = ExtractedExamParser.parse(
            "İşte sonuç:\n```json\n" +
                """{"kind":"BRANS_TYT","date":null,"sections":[{"subject":"TYT_MATEMATIK","correct":30,"wrong":5}]}""" +
                "\n```\nUmarım yardımcı olur!",
        )
        assertEquals(ExamKind.BRANS_TYT, result.kind)
        assertNull(result.date)
        // question_count missing → subject default (40 for TYT Mat).
        assertEquals(40, result.sections.single().questionCount)
    }

    @Test
    fun unreadableValuesStayNullNeverGuessed() {
        val result = ExtractedExamParser.parse(
            """{"kind":"BRANS_AYT","sections":[{"subject":"AYT_FIZIK","correct":null,"wrong":3,"question_count":14}]}""",
        )
        assertNull(result.sections.single().correct)
        assertEquals(3, result.sections.single().wrong)
    }

    @Test
    fun fullMockMissingSectionIsFilledEmptyWithWarning() {
        val result = ExtractedExamParser.parse(
            """{"kind":"TYT_FULL","sections":[{"subject":"TYT_MATEMATIK","correct":28,"wrong":8,"question_count":40}]}""",
        )
        assertEquals(4, result.sections.size) // canonical TYT order, gaps filled empty
        assertEquals(Subject.TYT_TURKCE, result.sections[0].subject)
        assertNull(result.sections[0].correct)
        assertTrue(result.warnings.any { it.contains("Türkçe") })
    }

    @Test
    fun unknownSubjectSkippedWithWarning_unknownKindDefaultsToTyt() {
        val result = ExtractedExamParser.parse(
            """{"kind":"LGS","sections":[
                {"subject":"EDEBIYAT","correct":10,"wrong":2},
                {"subject":"TYT_FEN","correct":12,"wrong":3,"question_count":20}]}""",
        )
        assertEquals(ExamKind.TYT_FULL, result.kind)
        assertTrue(result.warnings.any { it.contains("EDEBIYAT") })
        assertTrue(result.warnings.any { it.contains("TYT varsayıldı") })
        assertEquals(12, result.sections.first { it.subject == Subject.TYT_FEN }.correct)
    }

    @Test
    fun outOfRangeCountsAreDropped() {
        // correct > question_count would fail entry validation anyway; parser nulls it.
        val result = ExtractedExamParser.parse(
            """{"kind":"BRANS_TYT","sections":[{"subject":"TYT_MATEMATIK","correct":55,"wrong":2,"question_count":40}]}""",
        )
        assertNull(result.sections.single().correct)
        assertEquals(2, result.sections.single().wrong)
    }

    @Test(expected = AiException::class)
    fun noJsonAtAllThrows() {
        ExtractedExamParser.parse("Üzgünüm, bu görselde bir deneme sonucu göremiyorum.")
    }

    @Test
    fun balancedBraceExtractionHandlesNestedAndStrings() {
        assertEquals(
            """{"a":{"b":"x}y"},"c":1}""",
            ExtractedExamParser.extractJsonObject("""noise {"a":{"b":"x}y"},"c":1} trailing"""),
        )
    }
}
