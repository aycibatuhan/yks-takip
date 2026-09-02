package com.yks2027.tracker.core.ai

import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.Subject
import com.yks2027.tracker.core.model.fullSections
import java.time.LocalDate
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * v1.2 — AI extraction of a deneme result sheet (image/PDF/pasted text) into a
 * pre-filled ExamEntry form. IMPORTANT CONTRACT: nothing extracted is ever written to
 * the database here — the result only pre-fills the entry form, and the human confirms.
 */

/** What the model must return (strict JSON, no prose). */
@Serializable
data class ExtractedSection(
    val subject: String,
    val correct: Int? = null,
    val wrong: Int? = null,
    @SerialName("question_count") val questionCount: Int? = null,
)

@Serializable
data class ExtractedExamJson(
    val kind: String? = null,
    val date: String? = null,
    val name: String? = null,
    val publisher: String? = null,
    val sections: List<ExtractedSection> = emptyList(),
)

/** Validated, enum-typed result handed to the entry form. */
data class ExtractedExam(
    val kind: ExamKind,
    val date: LocalDate?,
    val name: String?,
    val publisher: String?,
    /** subject → (correct, wrong, questionCount); unreadable values arrive as null. */
    val sections: List<ExtractedSectionValues>,
    val warnings: List<String>,
)

data class ExtractedSectionValues(
    val subject: Subject,
    val correct: Int?,
    val wrong: Int?,
    val questionCount: Int,
)

/**
 * Pure parser for the model output — tolerant of code fences and stray prose around
 * the JSON, strict about the contract itself. Unit-tested (v1.2 verification list).
 */
object ExtractedExamParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): ExtractedExam {
        val payload = extractJsonObject(raw)
            ?: throw AiException("Modelin yanıtında JSON bulunamadı — tekrar dene ya da elle gir.")
        val doc = runCatching { json.decodeFromString(ExtractedExamJson.serializer(), payload) }
            .getOrElse { throw AiException("Model çıktısı çözümlenemedi: ${it.message?.take(120)}") }

        val warnings = mutableListOf<String>()
        val kind = doc.kind?.let { runCatching { ExamKind.valueOf(it.trim()) }.getOrNull() }
            ?: run {
                warnings += "Deneme türü okunamadı — TYT varsayıldı."
                ExamKind.TYT_FULL
            }
        val date = doc.date?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
        if (doc.date != null && date == null) warnings += "Tarih okunamadı (${doc.date})."

        val parsedSections = doc.sections.mapNotNull { section ->
            val subject = runCatching { Subject.valueOf(section.subject.trim()) }.getOrNull()
            if (subject == null) {
                warnings += "Bilinmeyen ders atlandı: ${section.subject}"
                return@mapNotNull null
            }
            val count = section.questionCount?.takeIf { it in 1..999 } ?: subject.defaultQuestionCount
            ExtractedSectionValues(
                subject = subject,
                correct = section.correct?.takeIf { it in 0..count },
                wrong = section.wrong?.takeIf { it in 0..count },
                questionCount = count,
            )
        }

        val sections = if (kind.isFull) {
            // Canonical order; a section the model missed still renders (empty fields).
            val bySubject = parsedSections.associateBy { it.subject }
            kind.fullSections().map { subject ->
                bySubject[subject] ?: ExtractedSectionValues(subject, null, null, subject.defaultQuestionCount)
                    .also { warnings += "${subject.label} bölümü okunamadı." }
            }
        } else {
            val first = parsedSections.firstOrNull()
                ?: throw AiException("Branş denemesi için ders okunamadı — elle gir.")
            if (parsedSections.size > 1) warnings += "Branş denemesinde tek ders beklenir; ilki alındı."
            listOf(first)
        }

        return ExtractedExam(
            kind = kind,
            date = date,
            name = doc.name?.trim()?.takeIf { it.isNotBlank() },
            publisher = doc.publisher?.trim()?.takeIf { it.isNotBlank() },
            sections = sections,
            warnings = warnings,
        )
    }

    /** First balanced {...} block, tolerating ```json fences and surrounding prose. */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

sealed interface ExtractSource {
    data class Image(val bytes: ByteArray, val isPng: Boolean) : ExtractSource
    data class Pdf(val bytes: ByteArray) : ExtractSource
    data class Text(val raw: String) : ExtractSource
}

class ExamExtractor constructor(
    private val anthropicProvider: AnthropicProvider,
    private val profilesRepository: AiProfilesRepository,
    private val secrets: AiSecretsRepository,
) {

    /** Anthropic-protocol profiles only for now (document/image blocks via the SDK). */
    suspend fun extract(source: ExtractSource): ExtractedExam {
        val profile = profilesRepository.activeProfileOnce()
            ?: throw AiException("AI profili yok — Ayarlar → AI Koç bölümünden ekle.")
        if (profilesRepository.protocolOf(profile) != AiProtocol.ANTHROPIC) {
            throw AiException(
                "Görsel/PDF aktarma şimdilik yalnız Claude (Anthropic) profilleriyle çalışır — " +
                    "aktif profili bir Anthropic profiline çevir.",
            )
        }
        val apiKey = secrets.getKey(profile.id).orEmpty()
        val blocks = buildList {
            when (source) {
                is ExtractSource.Image -> add(
                    ContentBlockParam.ofImage(
                        ImageBlockParam.builder()
                            .source(
                                Base64ImageSource.builder()
                                    .data(Base64.getEncoder().encodeToString(source.bytes))
                                    .mediaType(
                                        if (source.isPng) {
                                            Base64ImageSource.MediaType.IMAGE_PNG
                                        } else {
                                            Base64ImageSource.MediaType.IMAGE_JPEG
                                        },
                                    )
                                    .build(),
                            )
                            .build(),
                    ),
                )
                is ExtractSource.Pdf -> add(
                    ContentBlockParam.ofDocument(
                        DocumentBlockParam.builder()
                            .source(
                                Base64PdfSource.builder()
                                    .data(Base64.getEncoder().encodeToString(source.bytes))
                                    .build(),
                            )
                            .build(),
                    ),
                )
                is ExtractSource.Text -> add(ContentBlockParam.ofText("SONUÇ BELGESİ METNİ:\n" + source.raw))
            }
            add(ContentBlockParam.ofText(USER_INSTRUCTION))
        }
        val rawReply = anthropicProvider.createOnce(
            apiKey = apiKey,
            baseUrl = profile.baseUrl,
            model = profile.model,
            system = SYSTEM_PROMPT,
            blocks = blocks,
        )
        return ExtractedExamParser.parse(rawReply)
    }

    companion object {
        val SYSTEM_PROMPT = """
            Sen bir YKS deneme sonuç belgesi okuyucususun. Sana verilen görsel/PDF/metinden
            öğrencinin SAYISAL deneme sonucunu çıkarır ve YALNIZCA geçerli bir JSON nesnesi
            döndürürsün — açıklama, selamlama veya kod bloğu işareti olmadan.
        """.trimIndent()

        val USER_INSTRUCTION = """
            Bu deneme sonuç belgesinden verileri çıkar ve şu şemaya birebir uyan JSON döndür:
            {"kind":"TYT_FULL|AYT_SAY_FULL|BRANS_TYT|BRANS_AYT","date":"YYYY-MM-DD veya null",
             "name":"deneme adı veya null","publisher":"yayınevi veya null",
             "sections":[{"subject":"TYT_TURKCE|TYT_SOSYAL|TYT_MATEMATIK|TYT_FEN|AYT_MATEMATIK|AYT_FIZIK|AYT_KIMYA|AYT_BIYOLOJI",
                          "correct":tam sayı veya null,"wrong":tam sayı veya null,"question_count":tam sayı veya null}]}
            Kurallar: correct = doğru sayısı, wrong = yanlış sayısı (net DEĞİL). Okunamayan
            değere null yaz, asla tahmin etme. AYT için yalnız sayısal dersler (Mat/Fizik/
            Kimya/Biyoloji). Tek derslik belge ise kind BRANS_TYT veya BRANS_AYT olsun.
        """.trimIndent()
    }
}
