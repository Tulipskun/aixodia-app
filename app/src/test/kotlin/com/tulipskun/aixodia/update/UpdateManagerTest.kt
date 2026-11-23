package com.tulipskun.aixodia.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun aHigherRollingVersionCodeIsAnUpdate() {
        assertTrue(UpdateManager.rollingNeedsUpdate(1_700_000_000L, 1_699_999_999))
    }

    @Test
    fun theSameOrOlderRollingVersionCodeIsNotAnUpdate() {
        assertFalse(UpdateManager.rollingNeedsUpdate(1_700_000_000L, 1_700_000_000))
        assertFalse(UpdateManager.rollingNeedsUpdate(1_699_999_999L, 1_700_000_000))
    }

    @Test
    fun missingRollingMetadataDoesNotOfferAnUpdate() {
        assertFalse(UpdateManager.rollingNeedsUpdate(-1L, 1))
        assertFalse(UpdateManager.rollingNeedsUpdate(0L, 1))
    }

    @Test
    fun theUpdaterIsNotGatedOnSignInState() {
        // Sign-in lives in Cloudflare Access, in front of the engine. Replacing
        // the APK does not depend on it, so both states must offer the update.
        assertTrue(UpdateManager.canUpdate(signedIn = false))
        assertTrue(UpdateManager.canUpdate(signedIn = true))
    }

    @Test
    fun aFailedCheckSaysSoAndInstallsNothing() {
        // The bug this answers: GitHub rate-limited the address, the check could
        // not be made, and the row said "Up to date" — a claim about the
        // published build that was never checked.
        val (words, update) = UpdateManager.announce(
            UpdateManager.Check.Failed("GitHub said HTTP 403"),
        )
        assertEquals("Could not check: GitHub said HTTP 403", words)
        assertNull(update)
    }

    @Test
    fun aPublishedBuildIsNamedAndOffered() {
        val newest = UpdateManager.Update(tag = "latest", apkUrl = "u", versionName = "1.0.9")
        val (words, update) = UpdateManager.announce(UpdateManager.Check.Needed(newest))
        assertEquals("Found 1.0.9, downloading…", words)
        assertEquals(newest, update)
    }

    @Test
    fun nothingNewerIsUpToDateAndInstallsNothing() {
        val (words, update) = UpdateManager.announce(UpdateManager.Check.Fresh)
        assertTrue(words.startsWith("Up to date"))
        assertNull(update)
    }
}
