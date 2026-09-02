package com.yks2027.tracker

import com.yks2027.tracker.core.model.Subject
import com.yks2027.tracker.core.model.TopicCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4 — catalog integrity: ids are the persistence contract for topic marks/statuses. */
class TopicCatalogTest {

    @Test
    fun idsAreUnique() {
        val ids = TopicCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun idsAreStableSlugs() {
        // ASCII lowercase + digits + dashes only — safe as TEXT keys forever.
        val slug = Regex("^[a-z0-9-]+$")
        TopicCatalog.all.forEach { topic ->
            assertTrue("bad id: ${topic.id}", slug.matches(topic.id))
        }
    }

    @Test
    fun everySubjectHasTopics() {
        Subject.entries.forEach { subject ->
            assertTrue("no topics for $subject", TopicCatalog.topicsFor(subject).isNotEmpty())
        }
    }

    @Test
    fun groupedSubjectsKeepGroupsContiguous() {
        // The pickers render group headers on group change; interleaved groups would
        // repeat headers. Verify each subject lists its groups contiguously.
        Subject.entries.forEach { subject ->
            val groups = TopicCatalog.topicsFor(subject).map { it.group }
            val collapsed = groups.filterIndexed { i, g -> i == 0 || groups[i - 1] != g }
            assertEquals("interleaved groups in $subject", collapsed.toSet().size, collapsed.size)
        }
    }

    @Test
    fun lookupIsConsistent() {
        TopicCatalog.all.forEach { topic ->
            assertEquals(topic, TopicCatalog.byId(topic.id))
            assertEquals(topic.label, TopicCatalog.labelOf(topic.id))
        }
        assertEquals("unknown-id", TopicCatalog.labelOf("unknown-id"))
    }
}
