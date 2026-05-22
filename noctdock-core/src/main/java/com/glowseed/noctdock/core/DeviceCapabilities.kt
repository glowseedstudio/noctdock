package com.glowseed.noctdock.core

import kotlinx.serialization.Serializable

@Serializable
enum class DevicePerformanceTier {
    LOW,
    MID,
    HIGH,
    FLAGSHIP,
    UNKNOWN,
}

@Serializable
enum class HandheldPerformanceTier {
    FLAGSHIP,
    HIGH,
    MID,
    LIGHT,
    UNKNOWN,
}

@Serializable
enum class DeviceSupportLevel {
    FULL,
    CONSERVATIVE,
    RECEIVER_OR_LIGHT_ONLY,
    UNKNOWN_SAFE_DEFAULT,
}

@Serializable
enum class HandheldBrand {
    RETROID,
    AYN,
    AYANEO,
    KONKR,
    ANBERNIC,
    RAZER,
    LOGITECH,
    ABXYLUTE,
    MANGMI,
    UNKNOWN,
}

@Serializable
enum class DeviceProfileId {
    RETROID_POCKET_6_12GB,
    RETROID_POCKET_6_8GB,
    RETROID_POCKET_5,
    RETROID_POCKET_G2,
    RETROID_POCKET_MINI_V2,
    RETROID_POCKET_FLIP_2_SD865,
    RETROID_POCKET_4_PRO,
    RETROID_POCKET_3_PLUS,
    AYN_ODIN_2,
    AYN_ODIN_2_PORTAL,
    AYN_ODIN_3,
    AYN_THOR_SNAPDRAGON_8_GEN_2,
    AYN_THOR_LITE_SD865,
    AYANEO_POCKET_S,
    AYANEO_POCKET_S2,
    AYANEO_POCKET_EVO,
    AYANEO_POCKET_DS,
    AYANEO_POCKET_AIR,
    KONKR_POCKET_FIT,
    ANBERNIC_RG557,
    ANBERNIC_RG477M_RG477V,
    ANBERNIC_RG556,
    RAZER_EDGE,
    LOGITECH_G_CLOUD,
    ABXYLUTE_ONE,
    MANGMI_AIR_X,
    UNKNOWN_ANDROID_HANDHELD,
}

@Serializable
enum class RecommendedNoctDockProfile(val streamProfileId: String, val friendlyLabel: String) {
    PERFORMANCE(StreamProfiles.Performance.id, "Performance"),
    BALANCED(StreamProfiles.Balanced.id, "Balanced"),
    QUALITY(StreamProfiles.Quality.id, "Quality"),
    SHARP(StreamProfiles.Sharp.id, "Sharp"),
    CINEMA(StreamProfiles.Cinema.id, "Cinema"),
}

@Serializable
data class EncoderCapabilitySummary(
    val encoderName: String = "Unknown",
    val hardwareAccelerated: Boolean = false,
    val supportsAvc: Boolean = false,
    val supportsHevc: Boolean = false,
    val hevcEncoderName: String = "Unknown",
    val supports1080p60: Boolean = false,
    val supportsLowLatency: Boolean = false,
    val maxWidth: Int = 1280,
    val maxHeight: Int = 720,
    val maxFps: Int = 60,
    val maxBitrateMbps: Int = 8,
)

@Serializable
data class RecommendedStreamSettings(
    val profileId: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateMbps: Int,
    val adaptiveBitrateEnabled: Boolean = true,
    val maxQueueSize: Int = 2,
    val allowFrameDropping: Boolean = true,
)

@Serializable
data class DeviceCapabilityProfile(
    val id: DeviceProfileId,
    val displayName: String,
    val tier: DevicePerformanceTier,
    val handheldTier: HandheldPerformanceTier,
    val supportLevel: DeviceSupportLevel,
    val brand: HandheldBrand,
    val defaultProfileId: String,
    val recommendedNoctDockProfile: RecommendedNoctDockProfile,
    val recommended: RecommendedStreamSettings,
    val qualityAllowed: Boolean,
    val sharpAllowedByDefault: Boolean = false,
    val cinemaAllowedByDefault: Boolean = false,
    val qualityWarning: String? = null,
    val settingsRecommendation: String = "Optimised for this handheld",
)

@Serializable
data class DeviceBuildInfo(
    val manufacturer: String = "",
    val model: String = "",
    val device: String = "",
    val product: String = "",
    val hardware: String = "",
    val socManufacturer: String = "",
    val socModel: String = "",
    val fingerprint: String = "",
)

@Serializable
data class DeviceCapabilityCache(val appVersion: String, val deviceFingerprint: String, val profile: DeviceCapabilityProfile, val encoderSummary: EncoderCapabilitySummary) {
    fun isValid(appVersion: String, deviceFingerprint: String): Boolean = this.appVersion == appVersion && this.deviceFingerprint == deviceFingerprint
}

object DeviceCapabilityProfiles {
    private fun profile(
        id: DeviceProfileId,
        displayName: String,
        tier: DevicePerformanceTier,
        handheldTier: HandheldPerformanceTier,
        supportLevel: DeviceSupportLevel,
        brand: HandheldBrand,
        defaultProfileId: String,
        recommendedNoctDockProfile: RecommendedNoctDockProfile,
        bitrateMbps: Int,
        qualityAllowed: Boolean,
        sharpAllowedByDefault: Boolean = false,
        cinemaAllowedByDefault: Boolean = false,
        qualityWarning: String? = null,
        settingsRecommendation: String = "Optimised for this handheld",
        maxQueueSize: Int = 2,
        allowFrameDropping: Boolean = true,
        adaptiveBitrateEnabled: Boolean = true,
    ) = DeviceCapabilityProfile(
        id = id,
        displayName = displayName,
        tier = tier,
        handheldTier = handheldTier,
        supportLevel = supportLevel,
        brand = brand,
        defaultProfileId = defaultProfileId,
        recommendedNoctDockProfile = recommendedNoctDockProfile,
        recommended =
        RecommendedStreamSettings(
            profileId = defaultProfileId,
            width = 1280,
            height = 720,
            fps = 60,
            bitrateMbps = bitrateMbps,
            adaptiveBitrateEnabled = adaptiveBitrateEnabled,
            maxQueueSize = maxQueueSize,
            allowFrameDropping = allowFrameDropping,
        ),
        qualityAllowed = qualityAllowed,
        sharpAllowedByDefault = sharpAllowedByDefault,
        cinemaAllowedByDefault = cinemaAllowedByDefault,
        qualityWarning = qualityWarning,
        settingsRecommendation = settingsRecommendation,
    )

    val RetroidPocket6_12Gb =
        profile(
            id = DeviceProfileId.RETROID_POCKET_6_12GB,
            displayName = "Retroid Pocket 6",
            tier = DevicePerformanceTier.FLAGSHIP,
            handheldTier = HandheldPerformanceTier.FLAGSHIP,
            supportLevel = DeviceSupportLevel.FULL,
            brand = HandheldBrand.RETROID,
            defaultProfileId = StreamProfiles.Balanced.id,
            recommendedNoctDockProfile = RecommendedNoctDockProfile.BALANCED,
            bitrateMbps = 18,
            qualityAllowed = true,
            settingsRecommendation = "Full HD available after connection test",
        )

    val RetroidPocket6_8Gb =
        profile(
            id = DeviceProfileId.RETROID_POCKET_6_8GB,
            displayName = "Retroid Pocket 6",
            tier = DevicePerformanceTier.HIGH,
            handheldTier = HandheldPerformanceTier.HIGH,
            supportLevel = DeviceSupportLevel.FULL,
            brand = HandheldBrand.RETROID,
            defaultProfileId = StreamProfiles.Balanced.id,
            recommendedNoctDockProfile = RecommendedNoctDockProfile.BALANCED,
            bitrateMbps = 18,
            qualityAllowed = true,
            qualityWarning = "Balanced Mode is recommended for longer play sessions.",
            settingsRecommendation = "Full HD available after connection test",
        )

    val RetroidPocket5 =
        profile(
            id = DeviceProfileId.RETROID_POCKET_5,
            displayName = "Retroid Pocket 5",
            tier = DevicePerformanceTier.HIGH,
            handheldTier = HandheldPerformanceTier.HIGH,
            supportLevel = DeviceSupportLevel.CONSERVATIVE,
            brand = HandheldBrand.RETROID,
            defaultProfileId = StreamProfiles.Performance.id,
            recommendedNoctDockProfile = RecommendedNoctDockProfile.PERFORMANCE,
            bitrateMbps = 12,
            qualityAllowed = false,
            qualityWarning = "Performance Mode is recommended for this handheld.",
            settingsRecommendation = "Performance Mode recommended",
        )

    val RetroidPocketG2 =
        profile(
            DeviceProfileId.RETROID_POCKET_G2,
            "Retroid Pocket G2",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.RETROID,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
            settingsRecommendation = "Full HD available after connection test",
        )
    val RetroidPocketMiniV2 =
        profile(
            DeviceProfileId.RETROID_POCKET_MINI_V2,
            "Retroid Pocket Mini V2",
            DevicePerformanceTier.HIGH,
            HandheldPerformanceTier.HIGH,
            DeviceSupportLevel.CONSERVATIVE,
            HandheldBrand.RETROID,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = true,
            qualityWarning = "Performance Mode recommended",
            settingsRecommendation = "Performance Mode recommended",
        )
    val RetroidPocketFlip2Sd865 =
        profile(
            DeviceProfileId.RETROID_POCKET_FLIP_2_SD865,
            "Retroid Pocket Flip 2",
            DevicePerformanceTier.HIGH,
            HandheldPerformanceTier.HIGH,
            DeviceSupportLevel.CONSERVATIVE,
            HandheldBrand.RETROID,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = true,
            qualityWarning = "Performance Mode recommended",
            settingsRecommendation = "Performance Mode recommended",
        )
    val RetroidPocket4Pro =
        profile(
            DeviceProfileId.RETROID_POCKET_4_PRO,
            "Retroid Pocket 4 Pro",
            DevicePerformanceTier.HIGH,
            HandheldPerformanceTier.HIGH,
            DeviceSupportLevel.CONSERVATIVE,
            HandheldBrand.RETROID,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
            qualityWarning = "Balanced Mode recommended",
        )
    val RetroidPocket3Plus =
        profile(
            DeviceProfileId.RETROID_POCKET_3_PLUS,
            "Retroid Pocket 3+",
            DevicePerformanceTier.LOW,
            HandheldPerformanceTier.LIGHT,
            DeviceSupportLevel.RECEIVER_OR_LIGHT_ONLY,
            HandheldBrand.RETROID,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = false,
            settingsRecommendation = "This device is best used as a receiver or for lighter games",
        )

    val AynOdin2 =
        profile(
            DeviceProfileId.AYN_ODIN_2,
            "AYN Odin 2",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.AYN,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
            settingsRecommendation = "Full HD available after connection test",
        )
    val AynOdin2Portal =
        profile(
            DeviceProfileId.AYN_ODIN_2_PORTAL,
            "AYN Odin 2 Portal",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.AYN,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
            settingsRecommendation = "Full HD available after connection test",
        )
    val AynOdin3 =
        profile(
            DeviceProfileId.AYN_ODIN_3,
            "AYN Odin 3",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.AYN,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
            settingsRecommendation = "Full HD available after connection test",
        )
    val AynThorSnapdragon8Gen2 =
        profile(
            DeviceProfileId.AYN_THOR_SNAPDRAGON_8_GEN_2,
            "AYN Thor",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.AYN,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
        )
    val AynThorLiteSd865 =
        profile(
            DeviceProfileId.AYN_THOR_LITE_SD865,
            "AYN Thor Lite",
            DevicePerformanceTier.HIGH,
            HandheldPerformanceTier.HIGH,
            DeviceSupportLevel.CONSERVATIVE,
            HandheldBrand.AYN,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = true,
            qualityWarning = "Performance Mode recommended",
            settingsRecommendation = "Performance Mode recommended",
        )

    val AyaneoPocketS =
        profile(
            DeviceProfileId.AYANEO_POCKET_S,
            "AYANEO Pocket S",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.AYANEO,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
        )
    val AyaneoPocketS2 =
        profile(
            DeviceProfileId.AYANEO_POCKET_S2,
            "AYANEO Pocket S2",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.AYANEO,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
        )
    val AyaneoPocketEvo =
        profile(
            DeviceProfileId.AYANEO_POCKET_EVO,
            "AYANEO Pocket EVO",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.AYANEO,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
        )
    val AyaneoPocketDs =
        profile(
            DeviceProfileId.AYANEO_POCKET_DS,
            "AYANEO Pocket DS",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.AYANEO,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
        )
    val AyaneoPocketAir =
        profile(
            DeviceProfileId.AYANEO_POCKET_AIR,
            "AYANEO Pocket Air",
            DevicePerformanceTier.HIGH,
            HandheldPerformanceTier.HIGH,
            DeviceSupportLevel.CONSERVATIVE,
            HandheldBrand.AYANEO,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = true,
            qualityWarning = "Performance Mode recommended",
            settingsRecommendation = "Performance Mode recommended",
        )

    val KonkrPocketFit =
        profile(
            DeviceProfileId.KONKR_POCKET_FIT,
            "KONKR Pocket FIT",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.KONKR,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
        )

    val AnbernicRg557 =
        profile(
            DeviceProfileId.ANBERNIC_RG557,
            "Anbernic RG557",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.ANBERNIC,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
        )
    val AnbernicRg477 =
        profile(
            DeviceProfileId.ANBERNIC_RG477M_RG477V,
            "Anbernic RG477",
            DevicePerformanceTier.FLAGSHIP,
            HandheldPerformanceTier.FLAGSHIP,
            DeviceSupportLevel.FULL,
            HandheldBrand.ANBERNIC,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
        )
    val AnbernicRg556 =
        profile(
            DeviceProfileId.ANBERNIC_RG556,
            "Anbernic RG556",
            DevicePerformanceTier.HIGH,
            HandheldPerformanceTier.HIGH,
            DeviceSupportLevel.CONSERVATIVE,
            HandheldBrand.ANBERNIC,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = true,
            qualityWarning = "Performance Mode recommended",
            settingsRecommendation = "Performance Mode recommended",
        )

    val RazerEdge =
        profile(
            DeviceProfileId.RAZER_EDGE,
            "Razer Edge",
            DevicePerformanceTier.HIGH,
            HandheldPerformanceTier.HIGH,
            DeviceSupportLevel.CONSERVATIVE,
            HandheldBrand.RAZER,
            StreamProfiles.Balanced.id,
            RecommendedNoctDockProfile.BALANCED,
            18,
            qualityAllowed = true,
            qualityWarning = "Balanced Mode recommended",
        )

    val LogitechGCloud =
        profile(
            DeviceProfileId.LOGITECH_G_CLOUD,
            "Logitech G Cloud",
            DevicePerformanceTier.LOW,
            HandheldPerformanceTier.LIGHT,
            DeviceSupportLevel.RECEIVER_OR_LIGHT_ONLY,
            HandheldBrand.LOGITECH,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = false,
            settingsRecommendation = "This device is best used as a receiver or for lighter games",
        )
    val AbxyluteOne =
        profile(
            DeviceProfileId.ABXYLUTE_ONE,
            "Abxylute One",
            DevicePerformanceTier.LOW,
            HandheldPerformanceTier.LIGHT,
            DeviceSupportLevel.RECEIVER_OR_LIGHT_ONLY,
            HandheldBrand.ABXYLUTE,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = false,
            settingsRecommendation = "This device is best used as a receiver or for lighter games",
        )
    val MangmiAirX =
        profile(
            DeviceProfileId.MANGMI_AIR_X,
            "MANGMI Air X",
            DevicePerformanceTier.LOW,
            HandheldPerformanceTier.LIGHT,
            DeviceSupportLevel.RECEIVER_OR_LIGHT_ONLY,
            HandheldBrand.MANGMI,
            StreamProfiles.Performance.id,
            RecommendedNoctDockProfile.PERFORMANCE,
            12,
            qualityAllowed = false,
            settingsRecommendation = "This device is best used as a receiver or for lighter games",
        )

    val UnknownAndroidHandheld =
        profile(
            id = DeviceProfileId.UNKNOWN_ANDROID_HANDHELD,
            displayName = "Android handheld",
            tier = DevicePerformanceTier.UNKNOWN,
            handheldTier = HandheldPerformanceTier.UNKNOWN,
            supportLevel = DeviceSupportLevel.UNKNOWN_SAFE_DEFAULT,
            brand = HandheldBrand.UNKNOWN,
            defaultProfileId = StreamProfiles.Performance.id,
            recommendedNoctDockProfile = RecommendedNoctDockProfile.PERFORMANCE,
            bitrateMbps = 12,
            qualityAllowed = true,
            qualityWarning = "Start with Performance Mode, then increase quality after testing.",
            settingsRecommendation = "Performance Mode recommended",
        )
}

/**
 * Detects handheld brand/tier from build fingerprints and recommends a safe default stream profile.
 * Results are cached locally with the device fingerprint and app version.
 */
object DeviceCapabilityDetector {
    fun detect(buildInfo: DeviceBuildInfo, encoderSummary: EncoderCapabilitySummary = EncoderCapabilitySummary()): DeviceCapabilityProfile {
        val text =
            listOf(
                buildInfo.manufacturer,
                buildInfo.model,
                buildInfo.device,
                buildInfo.product,
                buildInfo.hardware,
                buildInfo.socManufacturer,
                buildInfo.socModel,
            ).joinToString(" ").normalisedDeviceText()

        return when {
            text.matchesAny("retroid pocket 3", "retroid pocket 3 plus", "retroid pocket3", "rp3", "rp3+") -> {
                DeviceCapabilityProfiles.RetroidPocket3Plus
            }

            text.isRetroidPocket5() -> {
                DeviceCapabilityProfiles.RetroidPocket5.copy(
                    tier =
                    if (encoderSummary.supports1080p60 &&
                        encoderSummary.hardwareAccelerated
                    ) {
                        DevicePerformanceTier.HIGH
                    } else {
                        DevicePerformanceTier.MID
                    },
                    handheldTier =
                    if (encoderSummary.supports1080p60 &&
                        encoderSummary.hardwareAccelerated
                    ) {
                        HandheldPerformanceTier.HIGH
                    } else {
                        HandheldPerformanceTier.MID
                    },
                )
            }

            text.matchesAny("retroid pocket mini v2", "pocket mini v2", "rpmini v2") -> {
                DeviceCapabilityProfiles.RetroidPocketMiniV2
            }

            text.matchesAny("retroid pocket flip 2", "flip 2", "sd865") && text.contains("retroid") -> {
                DeviceCapabilityProfiles.RetroidPocketFlip2Sd865
            }

            text.matchesAny("retroid pocket 4 pro", "rp4 pro", "pocket4 pro") -> {
                DeviceCapabilityProfiles.RetroidPocket4Pro
            }

            text.matchesAny("retroid pocket g2", "pocket g2", "rpg2") -> {
                DeviceCapabilityProfiles.RetroidPocketG2
            }

            text.isRetroidPocket6() -> {
                if (text.contains("8gb") || text.contains("8 gb")) {
                    DeviceCapabilityProfiles.RetroidPocket6_8Gb
                } else {
                    DeviceCapabilityProfiles.RetroidPocket6_12Gb
                }
            }

            text.matchesAny("ayn odin 2 portal", "odin2 portal") -> {
                DeviceCapabilityProfiles.AynOdin2Portal
            }

            text.matchesAny("ayn odin 2", "odin 2", "odin2") -> {
                DeviceCapabilityProfiles.AynOdin2
            }

            text.matchesAny("ayn odin 3", "odin 3", "odin3") -> {
                DeviceCapabilityProfiles.AynOdin3
            }

            text.matchesAny("ayn thor lite", "thor lite", "sd865") && text.contains("ayn") -> {
                DeviceCapabilityProfiles.AynThorLiteSd865
            }

            text.matchesAny("ayn thor", "snapdragon 8 gen 2") && text.contains("ayn") -> {
                DeviceCapabilityProfiles.AynThorSnapdragon8Gen2
            }

            text.matchesAny("ayaneo pocket s2", "pocket s2") -> {
                DeviceCapabilityProfiles.AyaneoPocketS2
            }

            text.matchesAny("ayaneo pocket s", "pocket s") && text.contains("ayaneo") -> {
                DeviceCapabilityProfiles.AyaneoPocketS
            }

            text.matchesAny("ayaneo pocket evo", "pocket evo") -> {
                DeviceCapabilityProfiles.AyaneoPocketEvo
            }

            text.matchesAny("ayaneo pocket ds", "pocket ds") -> {
                DeviceCapabilityProfiles.AyaneoPocketDs
            }

            text.matchesAny("ayaneo pocket air", "pocket air") -> {
                DeviceCapabilityProfiles.AyaneoPocketAir
            }

            text.matchesAny("konkr pocket fit", "pocket fit") && text.contains("konkr") -> {
                DeviceCapabilityProfiles.KonkrPocketFit
            }

            text.matchesAny("anbernic rg557", "rg557") -> {
                DeviceCapabilityProfiles.AnbernicRg557
            }

            text.matchesAny("anbernic rg477m", "anbernic rg477v", "rg477m", "rg477v") -> {
                DeviceCapabilityProfiles.AnbernicRg477
            }

            text.matchesAny("anbernic rg556", "rg556") -> {
                DeviceCapabilityProfiles.AnbernicRg556
            }

            text.matchesAny("razer edge", "edge 5g", "razeredge") -> {
                DeviceCapabilityProfiles.RazerEdge
            }

            text.matchesAny("logitech g cloud", "g cloud", "gcloud") -> {
                DeviceCapabilityProfiles.LogitechGCloud
            }

            text.matchesAny("abxylute one pro", "abxylute one", "abxylute") -> {
                DeviceCapabilityProfiles.AbxyluteOne
            }

            text.matchesAny("mangmi air x", "air x") && text.contains("mangmi") -> {
                DeviceCapabilityProfiles.MangmiAirX
            }

            else -> {
                DeviceCapabilityProfiles.UnknownAndroidHandheld
            }
        }
    }

    fun recommendedSettings(profile: DeviceCapabilityProfile): PerformanceSettings = PerformanceSettings(
        selectedProfileId = profile.defaultProfileId,
        maxQueueSize = profile.recommended.maxQueueSize,
        allowFrameDropping = profile.recommended.allowFrameDropping,
        adaptiveBitrateEnabled = profile.recommended.adaptiveBitrateEnabled,
    )

    fun allowsHevc(profile: DeviceCapabilityProfile, encoderSummary: EncoderCapabilitySummary): Boolean = profile.supportLevel != DeviceSupportLevel.RECEIVER_OR_LIGHT_ONLY &&
        encoderSummary.supportsHevc &&
        encoderSummary.hardwareAccelerated

    fun allowsFullHdAfterConnectionTest(profile: DeviceCapabilityProfile, encoderSummary: EncoderCapabilitySummary, connectionTestPassed: Boolean): Boolean = connectionTestPassed &&
        encoderSummary.supports1080p60 &&
        encoderSummary.hardwareAccelerated &&
        profile.handheldTier in listOf(HandheldPerformanceTier.FLAGSHIP, HandheldPerformanceTier.HIGH)

    fun recommendedLowerProfile(current: StreamProfile): StreamProfile = when (current.id) {
        StreamProfiles.Quality.id,
        StreamProfiles.Cinema.id,
        StreamProfiles.Sharp.id,
        StreamProfiles.Boost1080.id,
        -> StreamProfiles.Quality

        StreamProfiles.Balanced.id -> StreamProfiles.Performance

        else -> current
    }

    private fun String.normalisedDeviceText(): String = lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.matchesAny(vararg terms: String): Boolean = terms.any { contains(it) }

    private fun String.isRetroidPocket5(): Boolean = contains("retroid") &&
        (contains("pocket 5") || contains("rp5") || contains("retroid5") || contains("retroid pocket5"))

    private fun String.isRetroidPocket6(): Boolean = contains("retroid") &&
        (contains("pocket 6") || contains("rp6") || contains("retroid6") || contains("retroid pocket6"))
}
