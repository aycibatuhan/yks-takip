package com.yks2027.tracker

import com.yks2027.tracker.core.model.NetCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRD §13 test target 1 — the §3.2 worked examples, including negative nets. */
class NetCalculatorTest {

    @Test
    fun wholeCancellation() {
        assertEquals(136, NetCalculator.netQuarters(35, 4)) // 34.00
        assertEquals("34,00", NetCalculator.format(136))
    }

    @Test
    fun fractionalQuarter() {
        assertEquals(114, NetCalculator.netQuarters(30, 6)) // 28.50
        assertEquals("28,50", NetCalculator.format(114))
    }

    @Test
    fun negativeFractional() {
        assertEquals(-3, NetCalculator.netQuarters(0, 3)) // −0.75
        assertEquals("-0,75", NetCalculator.format(-3))
    }

    @Test
    fun negativeWhole() {
        assertEquals(-12, NetCalculator.netQuarters(2, 20)) // −3.00
        assertEquals("-3,00", NetCalculator.format(-12))
    }

    @Test
    fun osymEdgeCase_fourWrongIsMinusOne() {
        assertEquals(-4, NetCalculator.netQuarters(0, 4)) // −1.00
        assertEquals("-1,00", NetCalculator.format(-4))
    }

    @Test
    fun formatQuarterSteps() {
        assertEquals("0,00", NetCalculator.format(0))
        assertEquals("0,25", NetCalculator.format(1))
        assertEquals("0,50", NetCalculator.format(2))
        assertEquals("0,75", NetCalculator.format(3))
        assertEquals("-0,25", NetCalculator.format(-1))
    }

    @Test
    fun blankIsDerived() {
        assertEquals(6, NetCalculator.blank(40, 30, 4))
        assertEquals(0, NetCalculator.blank(20, 15, 5))
    }

    @Test
    fun averageQuartersFormatting() {
        // Mean of last-5 nets: 91.6 quarters = 22.90 net.
        assertEquals("22,90", NetCalculator.formatAverageQuarters(91.6))
        assertEquals("-0,75", NetCalculator.formatAverageQuarters(-3.0))
        assertEquals("0,00", NetCalculator.formatAverageQuarters(0.0))
        assertEquals("34,00", NetCalculator.formatAverageQuarters(136.0))
    }

    @Test
    fun accuracyPercent() {
        assertEquals(90, NetCalculator.accuracyPercent(36, 4))
        assertEquals(50, NetCalculator.accuracyPercent(5, 5))
        assertEquals(null, NetCalculator.accuracyPercent(0, 0)) // all blank
        assertEquals(0, NetCalculator.accuracyPercent(0, 10))
    }

    @Test
    fun validationBoundary() {
        assertTrue(NetCalculator.isValid(40, 36, 4))
        assertFalse(NetCalculator.isValid(40, 37, 4)) // D+Y > max
        assertFalse(NetCalculator.isValid(40, -1, 0))
        assertFalse(NetCalculator.isValid(40, 0, -1))
        assertTrue(NetCalculator.isValid(14, 0, 0)) // all blank is valid
    }
}
