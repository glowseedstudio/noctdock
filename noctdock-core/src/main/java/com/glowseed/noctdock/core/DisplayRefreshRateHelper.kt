package com.glowseed.noctdock.core

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display
import android.view.Window
import android.view.WindowManager
import kotlinx.serialization.Serializable

@Serializable
enum class RefreshRateHelperResult {
    NotRequested,
    Applied60Hz,
    AlreadyAtTarget,
    Unsupported,
    ManualGuidanceRequired,
    Failed,
}

@Serializable
data class RefreshRateHelperStatus(
    val requested60Hz: Boolean = false,
    val activeRefreshRateHz: Float? = null,
    val result: RefreshRateHelperResult = RefreshRateHelperResult.NotRequested,
    val guidanceMessage: String? = null,
) {
    fun resultLabel(): String = when (result) {
        RefreshRateHelperResult.NotRequested -> "Not requested"
        RefreshRateHelperResult.Applied60Hz -> "Applied 60 Hz where supported"
        RefreshRateHelperResult.AlreadyAtTarget -> "Already at 60 Hz or lower"
        RefreshRateHelperResult.Unsupported -> "Unsupported on this device"
        RefreshRateHelperResult.ManualGuidanceRequired -> "Manual display settings may be required"
        RefreshRateHelperResult.Failed -> "Request failed safely"
    }
}

object DisplayRefreshRateHelper {
    private const val TARGET_HZ = 60f
    private const val TARGET_TOLERANCE_HZ = 1.5f

    fun shouldRequestOnConsoleStart(mode: Smooth60HzMode): Boolean = mode == Smooth60HzMode.Always

    fun applyToActivity(activity: Activity, mode: Smooth60HzMode): RefreshRateHelperStatus {
        if (mode == Smooth60HzMode.Off) {
            return RefreshRateHelperStatus()
        }
        return applyToWindow(activity.window, activity)
    }

    fun applyToWindow(window: Window, context: Context): RefreshRateHelperStatus {
        val display =
            window.decorView.display
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
                }
        val currentHz = currentRefreshRateHz(display)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return unsupported(currentHz, "This Android version does not expose display mode APIs.")
        }
        val modes = runCatching { display.supportedModes }.getOrNull().orEmpty()
        if (modes.isEmpty()) {
            return RefreshRateHelperStatus(
                requested60Hz = true,
                activeRefreshRateHz = currentHz,
                result = RefreshRateHelperResult.ManualGuidanceRequired,
                guidanceMessage = "This handheld may need Display settings changed manually.",
            )
        }
        if (currentHz != null && currentHz <= TARGET_HZ + TARGET_TOLERANCE_HZ) {
            return RefreshRateHelperStatus(
                requested60Hz = true,
                activeRefreshRateHz = currentHz,
                result = RefreshRateHelperResult.AlreadyAtTarget,
            )
        }
        val preferred =
            modes
                .filter { it.refreshRate in (TARGET_HZ - TARGET_TOLERANCE_HZ)..(TARGET_HZ + TARGET_TOLERANCE_HZ) }
                .minByOrNull { kotlin.math.abs(it.refreshRate - TARGET_HZ) }
                ?: modes.filter { it.refreshRate <= TARGET_HZ + TARGET_TOLERANCE_HZ }.maxByOrNull { it.refreshRate }
        if (preferred == null) {
            return RefreshRateHelperStatus(
                requested60Hz = true,
                activeRefreshRateHz = currentHz,
                result = RefreshRateHelperResult.ManualGuidanceRequired,
                guidanceMessage = "This handheld may need Display settings changed manually.",
            )
        }
        return runCatching {
            val params = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                params.preferredDisplayModeId = preferred.modeId
                window.attributes = params
            }
            val appliedHz = currentRefreshRateHz(window.decorView.display ?: display) ?: preferred.refreshRate
            RefreshRateHelperStatus(
                requested60Hz = true,
                activeRefreshRateHz = appliedHz,
                result =
                if (appliedHz <= TARGET_HZ + TARGET_TOLERANCE_HZ) {
                    RefreshRateHelperResult.Applied60Hz
                } else {
                    RefreshRateHelperResult.ManualGuidanceRequired
                },
                guidanceMessage =
                if (appliedHz > TARGET_HZ + TARGET_TOLERANCE_HZ) {
                    "This handheld may need Display settings changed manually."
                } else {
                    null
                },
            )
        }.getOrElse {
            RefreshRateHelperStatus(
                requested60Hz = true,
                activeRefreshRateHz = currentHz,
                result = RefreshRateHelperResult.Failed,
            )
        }
    }

    fun clearWindow(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val params = window.attributes
                params.preferredDisplayModeId = 0
                window.attributes = params
            }
        }
    }

    fun currentRefreshRateHz(display: Display?): Float? {
        display ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                display.mode.refreshRate
            } else {
                null
            }
        }.getOrNull()
    }

    private fun unsupported(currentHz: Float?, guidance: String): RefreshRateHelperStatus = RefreshRateHelperStatus(
        requested60Hz = true,
        activeRefreshRateHz = currentHz,
        result = RefreshRateHelperResult.Unsupported,
        guidanceMessage = guidance,
    )
}
