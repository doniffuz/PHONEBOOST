package uz.phoneboost.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test fun belowMinimumIsForced() {
        val d = UpdateLogic.decide(current = 3, latest = 5, minSupported = 4, forceFlag = false)
        assertTrue(d.updateAvailable); assertTrue(d.force); assertFalse(d.optional); assertTrue(d.unsupported)
    }

    @Test fun atMinimumIsOptional() {
        val d = UpdateLogic.decide(current = 4, latest = 5, minSupported = 4, forceFlag = false)
        assertTrue(d.updateAvailable); assertFalse(d.force); assertTrue(d.optional); assertFalse(d.unsupported)
    }

    @Test fun forceFlagForcesEvenWhenSupported() {
        assertTrue(UpdateLogic.decide(4, 5, 0, true).force)
    }

    @Test fun upToDateNeverForces() {
        val d = UpdateLogic.decide(current = 5, latest = 5, minSupported = 0, forceFlag = true)
        assertFalse(d.updateAvailable); assertFalse(d.force); assertFalse(d.optional)
    }

    @Test fun newerThanServerIsNotAnUpdate() {
        assertFalse(UpdateLogic.decide(6, 5, 0, false).updateAvailable)
    }

    @Test fun noMinimumMeansOptional() {
        assertEquals(true, UpdateLogic.decide(1, 2, 0, false).optional)
    }
}
