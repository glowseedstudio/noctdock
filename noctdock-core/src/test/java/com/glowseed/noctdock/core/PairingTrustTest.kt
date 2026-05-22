package com.glowseed.noctdock.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTrustTest {
    @Test
    fun skipChallengeRequiresMatchingStoredToken() {
        assertTrue(PairingTrust.canSkipChallenge("secret-token", "secret-token"))
        assertFalse(PairingTrust.canSkipChallenge("secret-token", "other-token"))
        assertFalse(PairingTrust.canSkipChallenge(null, "secret-token"))
        assertFalse(PairingTrust.canSkipChallenge("secret-token", null))
        assertFalse(PairingTrust.canSkipChallenge(null, "receiver-id-from-discovery"))
    }
}
