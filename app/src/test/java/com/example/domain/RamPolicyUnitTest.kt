package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RamPolicyUnitTest {

    @Test
    fun ramClass_classifiedByTotalRam_notTransientAvailableRam() {
        val gb = 1024L * 1024L * 1024L
        assertEquals(RamClass.LOW, RamClass.fromTotalRamBytes((2.5 * gb).toLong()))
        assertEquals(RamClass.MID, RamClass.fromTotalRamBytes(4L * gb))
        assertEquals(RamClass.MID_PLUS, RamClass.fromTotalRamBytes(6L * gb))
        assertEquals(RamClass.HIGH, RamClass.fromTotalRamBytes(8L * gb))
        assertEquals(RamClass.HIGH_PLUS, RamClass.fromTotalRamBytes(12L * gb))
        assertEquals(RamClass.ULTRA, RamClass.fromTotalRamBytes(16L * gb))
    }

    @Test
    fun ramPolicy_budgetAndHardCap_scaleMonotonicallyWithTotalRam() {
        val gb = 1024L * 1024L * 1024L
        val total4Gb = 4L * gb
        val total8Gb = 8L * gb
        val total16Gb = 16L * gb

        val budget4 = RamPolicy.budgetBytes(total4Gb)
        val budget8 = RamPolicy.budgetBytes(total8Gb)
        val budget16 = RamPolicy.budgetBytes(total16Gb)

        assertTrue(budget4 < budget8)
        assertTrue(budget8 < budget16)

        // Hard cap is 70% of total RAM
        val cap4 = RamPolicy.hardCapBytes(total4Gb)
        val cap16 = RamPolicy.hardCapBytes(total16Gb)
        assertEquals(2.8, cap4.toDouble() / gb.toDouble(), 0.05)
        assertEquals(11.2, cap16.toDouble() / gb.toDouble(), 0.05)
    }

    @Test
    fun socAdjustment_stepUpAndStepDown_atMostOneRank() {
        val baseClass = RamClass.MID_PLUS
        val adjustedDown = baseClass.stepDown()
        assertEquals(RamClass.MID, adjustedDown)

        val adjustedUp = baseClass.stepUp()
        assertEquals(RamClass.HIGH, adjustedUp)

        // Ultra cannot step up beyond ULTRA
        val ultraAdjusted = RamClass.ULTRA.stepUp()
        assertEquals(RamClass.ULTRA, ultraAdjusted)
    }
}
