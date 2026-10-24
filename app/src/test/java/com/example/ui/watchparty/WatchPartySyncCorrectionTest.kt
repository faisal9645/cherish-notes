package com.example.ui.watchparty

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPartySyncCorrectionTest {
    @Test fun transientDriftDoesNotInterruptPlayback() {
        val sync = WatchPartySyncCorrection()
        assertFalse(sync.shouldSeek(1.1f, true, false, true, false, 0))
        assertFalse(sync.shouldSeek(0.2f, true, false, true, false, 750))
        assertFalse(sync.shouldSeek(-1.1f, true, false, true, false, 1500))
    }

    @Test fun sustainedDriftIsCorrected() {
        val sync = WatchPartySyncCorrection()
        assertFalse(sync.shouldSeek(-1.2f, true, false, true, false, 0))
        assertFalse(sync.shouldSeek(-1.2f, true, false, true, false, 750))
        assertTrue(sync.shouldSeek(-1.2f, true, false, true, false, 1500))
    }

    @Test fun bufferingDoesNotResetSeekCooldown() {
        val sync = WatchPartySyncCorrection()
        assertTrue(sync.shouldSeek(-4f, true, false, true, false, 0))
        assertFalse(sync.shouldSeek(-4f, false, true, true, false, 500))
        assertFalse(sync.shouldSeek(-4f, true, false, true, false, 1000))
        assertTrue(sync.shouldSeek(-4f, true, false, true, false, 3000))
    }

    @Test fun explicitSeekWaitsForFreshSampleAndOverridesCooldown() {
        val sync = WatchPartySyncCorrection()
        assertTrue(sync.shouldSeek(4f, true, false, true, false, 0))
        assertFalse(sync.shouldSeek(10f, true, false, false, true, 500))
        assertTrue(sync.shouldSeek(10f, true, false, true, false, 1000))
    }

    @Test fun adsAndStaleSamplesNeverTriggerSeek() {
        val sync = WatchPartySyncCorrection()
        assertFalse(sync.shouldSeek(30f, false, true, true, true, 0))
        assertFalse(sync.shouldSeek(30f, true, false, false, false, 750))
        assertTrue(sync.shouldSeek(30f, true, false, true, false, 1500))
    }

    @Test fun pausedPositionIsCorrectedWithoutWaitingForRepeatedSamples() {
        val sync = WatchPartySyncCorrection()
        assertTrue(sync.shouldSeek(0.5f, false, false, true, false, 0))
        assertFalse(sync.shouldSeek(0.1f, false, false, true, false, 4000))
    }
}
