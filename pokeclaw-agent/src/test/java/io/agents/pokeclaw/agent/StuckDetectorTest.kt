package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StuckDetectorTest {
    @Test
    fun autoKillUsesConfiguredTolerance() {
        val detector = StuckDetector(autoKillAfterDetections = 3)

        assertNull(detector.record("tap:1", 1, 0, null))
        assertNull(detector.record("tap:1", 1, 0, null))
        assertEquals(
            StuckDetector.RecoveryLevel.HINT,
            detector.record("tap:1", 1, 0, null)?.level
        )
        assertEquals(
            StuckDetector.RecoveryLevel.HINT,
            detector.record("tap:1", 1, 0, null)?.level
        )
        assertEquals(
            StuckDetector.RecoveryLevel.AUTO_KILL,
            detector.record("tap:1", 1, 0, null)?.level
        )
    }
}
