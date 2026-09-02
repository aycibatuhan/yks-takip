package com.yks2027.tracker.feature.importexport

import com.yks2027.tracker.core.ai.ExtractedExam

/**
 * v1.2 — in-memory hand-off from AI extraction to the ExamEntry form. The extracted
 * values NEVER touch the database directly; the form is pre-filled and the human
 * confirms (or fixes) before saving, exactly like a manual entry.
 */
class ExamPrefillHolder {

    private var pending: ExtractedExam? = null

    fun set(exam: ExtractedExam) {
        pending = exam
    }

    /** One-shot: the form consumes the prefill so stale data can't resurface later. */
    fun consume(): ExtractedExam? = pending.also { pending = null }
}
