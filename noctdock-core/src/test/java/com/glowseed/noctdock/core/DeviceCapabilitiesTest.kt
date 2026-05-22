package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun retroidPocket5DetectionUsesSafePerformanceDefault() {
        val profile =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(
                    manufacturer = "Retroid",
                    model = "Retroid Pocket 5",
                    device = "rp5",
                    product = "retroid_pocket_5",
                ),
                EncoderCapabilitySummary(
                    encoderName = "c2.qti.avc.encoder",
                    hardwareAccelerated = true,
                    supportsAvc = true,
                    supports1080p60 = true,
                    maxBitrateMbps = 60,
                ),
            )

        assertEquals(DeviceProfileId.RETROID_POCKET_5, profile.id)
        assertEquals(DevicePerformanceTier.HIGH, profile.tier)
        assertEquals(StreamProfiles.Performance.id, profile.defaultProfileId)
        assertEquals(12, profile.recommended.bitrateMbps)
        assertFalse(profile.qualityAllowed)
    }

    @Test
    fun retroidPocket5FallsBackToMidTierWhenEncoderCannotConfirm1080p60() {
        val profile =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Retroid", model = "RP5"),
                EncoderCapabilitySummary(hardwareAccelerated = true, supportsAvc = true, supports1080p60 = false),
            )

        assertEquals(DeviceProfileId.RETROID_POCKET_5, profile.id)
        assertEquals(DevicePerformanceTier.MID, profile.tier)
    }

    @Test
    fun retroidPocket6DetectionKeepsBalancedDefaultAndQualityAvailable() {
        val profile =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Retroid", model = "Retroid Pocket 6 12GB"),
            )

        assertEquals(DeviceProfileId.RETROID_POCKET_6_12GB, profile.id)
        assertEquals(DevicePerformanceTier.FLAGSHIP, profile.tier)
        assertEquals(StreamProfiles.Balanced.id, profile.defaultProfileId)
        assertTrue(profile.qualityAllowed)
    }

    @Test
    fun retroidPocket6EightGbUsesHighTierProfile() {
        val profile =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Retroid", model = "RP6 8GB"),
            )

        assertEquals(DeviceProfileId.RETROID_POCKET_6_8GB, profile.id)
        assertEquals(DevicePerformanceTier.HIGH, profile.tier)
        assertEquals(StreamProfiles.Balanced.id, profile.defaultProfileId)
    }

    @Test
    fun unknownAndroidHandheldGetsSafePerformanceDefault() {
        val profile =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Unknown", model = "Android Gaming Handheld"),
            )
        val settings = DeviceCapabilityDetector.recommendedSettings(profile)

        assertEquals(DeviceProfileId.UNKNOWN_ANDROID_HANDHELD, profile.id)
        assertEquals(StreamProfiles.Performance.id, settings.selectedProfileId)
        assertTrue(settings.adaptiveBitrateEnabled)
        assertTrue(settings.allowFrameDropping)
    }

    @Test
    fun retroidPocket4ProDetectionUsesHighConservativeProfile() {
        val profile =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Retroid", model = "Retroid Pocket 4 Pro", device = "rp4pro"),
            )

        assertEquals(DeviceProfileId.RETROID_POCKET_4_PRO, profile.id)
        assertEquals(HandheldPerformanceTier.HIGH, profile.handheldTier)
        assertEquals(DeviceSupportLevel.CONSERVATIVE, profile.supportLevel)
        assertEquals(StreamProfiles.Balanced.id, profile.defaultProfileId)
    }

    @Test
    fun retroidPocket3PlusFallsBackToLightTier() {
        val profile =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Retroid", model = "Retroid Pocket 3+", device = "rp3plus"),
            )

        assertEquals(DeviceProfileId.RETROID_POCKET_3_PLUS, profile.id)
        assertEquals(HandheldPerformanceTier.LIGHT, profile.handheldTier)
        assertEquals(DeviceSupportLevel.RECEIVER_OR_LIGHT_ONLY, profile.supportLevel)
        assertFalse(profile.qualityAllowed)
    }

    @Test
    fun aynOdinAndThorDevicesMapToExpectedTiers() {
        val odin =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "AYN", model = "Odin 2 Portal", device = "odin2portal"),
            )
        val thorLite =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "AYN", model = "Thor Lite Snapdragon 865", device = "thorlite"),
            )

        assertEquals(DeviceProfileId.AYN_ODIN_2_PORTAL, odin.id)
        assertEquals(HandheldPerformanceTier.FLAGSHIP, odin.handheldTier)
        assertEquals(DeviceProfileId.AYN_THOR_LITE_SD865, thorLite.id)
        assertEquals(HandheldPerformanceTier.HIGH, thorLite.handheldTier)
    }

    @Test
    fun ayaneoAndKonkrDevicesMapToFullSupportProfiles() {
        val ayaneo =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "AYANEO", model = "Pocket S2", device = "pockets2"),
            )
        val konkurr =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "KONKR", model = "Pocket FIT", device = "pocketfit"),
            )

        assertEquals(DeviceProfileId.AYANEO_POCKET_S2, ayaneo.id)
        assertEquals(DeviceSupportLevel.FULL, ayaneo.supportLevel)
        assertEquals(DeviceProfileId.KONKR_POCKET_FIT, konkurr.id)
        assertEquals(DeviceSupportLevel.FULL, konkurr.supportLevel)
    }

    @Test
    fun anbernicDevicesMapToExpectedProfiles() {
        val rg557 =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Anbernic", model = "RG557"),
            )
        val rg556 =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Anbernic", model = "RG556"),
            )

        assertEquals(DeviceProfileId.ANBERNIC_RG557, rg557.id)
        assertEquals(HandheldPerformanceTier.FLAGSHIP, rg557.handheldTier)
        assertEquals(DeviceProfileId.ANBERNIC_RG556, rg556.id)
        assertEquals(DeviceSupportLevel.CONSERVATIVE, rg556.supportLevel)
    }

    @Test
    fun logitechGCloudUsesReceiverOrLightOnlyFallback() {
        val profile =
            DeviceCapabilityDetector.detect(
                DeviceBuildInfo(manufacturer = "Logitech", model = "G Cloud"),
            )

        assertEquals(DeviceProfileId.LOGITECH_G_CLOUD, profile.id)
        assertEquals(HandheldPerformanceTier.LIGHT, profile.handheldTier)
        assertEquals(DeviceSupportLevel.RECEIVER_OR_LIGHT_ONLY, profile.supportLevel)
    }

    @Test
    fun hevcUnlockRequiresCapability() {
        assertTrue(
            DeviceCapabilityDetector.allowsHevc(
                DeviceCapabilityProfiles.AynOdin2,
                EncoderCapabilitySummary(supportsHevc = true, hardwareAccelerated = true),
            ),
        )
        assertFalse(
            DeviceCapabilityDetector.allowsHevc(
                DeviceCapabilityProfiles.LogitechGCloud,
                EncoderCapabilitySummary(supportsHevc = true, hardwareAccelerated = true),
            ),
        )
        assertFalse(
            DeviceCapabilityDetector.allowsHevc(
                DeviceCapabilityProfiles.AynOdin2,
                EncoderCapabilitySummary(supportsHevc = false, hardwareAccelerated = true),
            ),
        )
    }

    @Test
    fun fullHdModesRequireCapabilityAndConnectionTest() {
        assertTrue(
            DeviceCapabilityDetector.allowsFullHdAfterConnectionTest(
                profile = DeviceCapabilityProfiles.AynOdin2,
                encoderSummary = EncoderCapabilitySummary(supports1080p60 = true, hardwareAccelerated = true),
                connectionTestPassed = true,
            ),
        )
        assertFalse(
            DeviceCapabilityDetector.allowsFullHdAfterConnectionTest(
                profile = DeviceCapabilityProfiles.RetroidPocket3Plus,
                encoderSummary = EncoderCapabilitySummary(supports1080p60 = true, hardwareAccelerated = true),
                connectionTestPassed = true,
            ),
        )
        assertFalse(
            DeviceCapabilityDetector.allowsFullHdAfterConnectionTest(
                profile = DeviceCapabilityProfiles.AynOdin2,
                encoderSummary = EncoderCapabilitySummary(supports1080p60 = true, hardwareAccelerated = true),
                connectionTestPassed = false,
            ),
        )
    }

    @Test
    fun capabilityCacheValidityUsesAppVersionAndFingerprint() {
        val cache =
            DeviceCapabilityCache(
                appVersion = "1.0",
                deviceFingerprint = "fingerprint-a",
                profile = DeviceCapabilityProfiles.RetroidPocket5,
                encoderSummary = EncoderCapabilitySummary(encoderName = "encoder"),
            )

        assertTrue(cache.isValid("1.0", "fingerprint-a"))
        assertFalse(cache.isValid("1.1", "fingerprint-a"))
        assertFalse(cache.isValid("1.0", "fingerprint-b"))
    }

    @Test
    fun unstableStreamRecommendationStepsDownToPerformance() {
        assertEquals(StreamProfiles.Quality, DeviceCapabilityDetector.recommendedLowerProfile(StreamProfiles.Cinema))
        assertEquals(StreamProfiles.Quality, DeviceCapabilityDetector.recommendedLowerProfile(StreamProfiles.Sharp))
        assertEquals(
            StreamProfiles.Balanced,
            ThermalResponsePolicy.recommendedProfile(StreamProfiles.Quality, StreamHealth(0, 0, 0, 0, 0, true, false)),
        )
        assertEquals(
            StreamProfiles.Performance,
            DeviceCapabilityDetector.recommendedLowerProfile(StreamProfiles.Balanced),
        )
    }
}
