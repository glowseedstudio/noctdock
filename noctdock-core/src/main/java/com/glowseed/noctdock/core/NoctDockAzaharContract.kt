package com.glowseed.noctdock.core

/**
 * Stable intent extras and package names for the external **NoctDock Azahar** GPLv2 fork.
 *
 * **Launch in 3DS Mode** uses [ACTION_THREE_DS_MODE] and the extras below; Azahar runs top-screen export.
 * **Normal Launch** from the sender opens the Azahar package via standard launch + sender Console Mode
 * (full-screen mirror); it does not use this action. See [NOCTDOCK_AZAHAR_INTEGRATION.md].
 */
object NoctDockAzaharContract {
    const val PACKAGE_NAME = "com.glowseed.noctdock.azahar"
    const val DEBUG_PACKAGE_NAME = "com.glowseed.noctdock.azahar.debug"
    val PACKAGE_CANDIDATES = listOf(PACKAGE_NAME, DEBUG_PACKAGE_NAME)
    const val ACTION_THREE_DS_MODE = "com.glowseed.noctdock.azahar.action.THREE_DS_MODE"
    const val MODE_THREE_DS_TOP_SCREEN = "THREE_DS_TOP_SCREEN"

    const val EXTRA_MODE = "noctdock_mode"
    const val EXTRA_RECEIVER_NAME = "noctdock_receiver_name"
    const val EXTRA_RECEIVER_ADDRESS = "noctdock_receiver_address"
    const val EXTRA_RECEIVER_PORT = "noctdock_receiver_port"
    const val EXTRA_PREFERRED_CODEC = "noctdock_preferred_codec"
    const val EXTRA_SOUND_MODE = "noctdock_sound_mode"
    const val EXTRA_PROMPT_USER = "noctdock_prompt_user"

    fun preferredCodecValue(codec: VideoCodec): String = when (codec) {
        VideoCodec.AVC -> "avc"
        VideoCodec.HEVC -> "hevc"
    }

    fun soundModeValue(mode: SoundMode): String = when (mode) {
        SoundMode.RETROID -> "retroid"
        SoundMode.TV -> "tv"
        SoundMode.BOTH -> "both"
        SoundMode.QUIET -> "quiet"
    }

    /**
     * Matches [StreamNegotiator] used by Console Mode, with an extra device-policy gate so 3DS launch
     * does not request HEVC when the sender UI would hide HEVC-first profiles.
     */
    fun resolvePreferredCodec(profile: StreamProfile, sender: SenderVideoCapabilities, receiver: ReceiverVideoCapabilities, hevcAllowedByDevice: Boolean): VideoCodec {
        val negotiated = StreamNegotiator.negotiate(profile, sender, receiver)
        return if (negotiated.codec == VideoCodec.HEVC && hevcAllowedByDevice) {
            VideoCodec.HEVC
        } else {
            VideoCodec.AVC
        }
    }

    fun preflight(azaharInstalled: Boolean, receiverSelected: Boolean, receiverOnline: Boolean, receiverTrusted: Boolean): NoctDockAzaharPreflightResult = when {
        !azaharInstalled -> NoctDockAzaharPreflightResult.AZAHAR_MISSING
        !receiverSelected -> NoctDockAzaharPreflightResult.RECEIVER_REQUIRED
        !receiverOnline -> NoctDockAzaharPreflightResult.RECEIVER_NOT_READY
        !receiverTrusted -> NoctDockAzaharPreflightResult.RECEIVER_NOT_TRUSTED
        else -> NoctDockAzaharPreflightResult.READY
    }
}

enum class NoctDockAzaharPreflightResult {
    READY,
    AZAHAR_MISSING,
    RECEIVER_REQUIRED,
    RECEIVER_NOT_READY,
    RECEIVER_NOT_TRUSTED,
}

data class NoctDockAzaharLaunchDetails(
    val receiverName: String,
    val receiverAddress: String,
    val receiverPort: Int,
    val preferredCodec: VideoCodec,
    val soundMode: SoundMode,
    val promptUser: Boolean = true,
) {
    val mode: String = NoctDockAzaharContract.MODE_THREE_DS_TOP_SCREEN
    val preferredCodecValue: String = NoctDockAzaharContract.preferredCodecValue(preferredCodec)
    val soundModeValue: String = NoctDockAzaharContract.soundModeValue(soundMode)
}
