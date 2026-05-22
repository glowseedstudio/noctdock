package com.glowseed.noctdock.sender

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.glowseed.noctdock.core.NoctLog
import com.glowseed.noctdock.core.ScreenCloakMethod
import com.glowseed.noctdock.core.ScreenCloakMode
import com.glowseed.noctdock.core.ScreenCloakPolicy
import com.glowseed.noctdock.core.ScreenCloakSession
import com.glowseed.noctdock.core.ScreenCloakSessionTracker
import com.glowseed.noctdock.core.ScreenCloakState
import com.glowseed.noctdock.core.ScreenCloakStatus

data class ScreenCloakConfig(val mode: ScreenCloakMode, val overlayDisabledDueToTvPictureIssue: Boolean)

object ScreenCloakPermissionHelper {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun canWriteSystemSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun overlayPermissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun writeSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Applies Screen Cloak on the sender during Console Mode (overlay or system brightness).
 * [restore] must run when streaming stops so the handheld display returns to normal.
 */
class ScreenCloakController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var overlayAttached = false
    private var session = ScreenCloakSession()
    private var currentStatus = ScreenCloakStatus()

    fun preview(config: ScreenCloakConfig): ScreenCloakStatus = apply(config)

    fun apply(config: ScreenCloakConfig): ScreenCloakStatus {
        val overlayAllowed = ScreenCloakPermissionHelper.canDrawOverlays(context)
        val systemWriteAllowed = ScreenCloakPermissionHelper.canWriteSystemSettings(context)
        if (config.mode == ScreenCloakMode.OFF) {
            restore()
            currentStatus =
                ScreenCloakStatus(
                    mode = ScreenCloakMode.OFF,
                    method = ScreenCloakMethod.NONE,
                    state = ScreenCloakState.IDLE,
                    overlayPermissionGranted = overlayAllowed,
                    systemWritePermissionGranted = systemWriteAllowed,
                    disabledDueToTvPictureIssue = config.overlayDisabledDueToTvPictureIssue,
                    restoreSucceeded = currentStatus.restoreSucceeded,
                )
            return currentStatus
        }

        val preferredMethod =
            ScreenCloakPolicy.preferredMethod(
                mode = config.mode,
                overlayPermissionGranted = overlayAllowed,
                systemWritePermissionGranted = systemWriteAllowed,
                overlayDisabledDueToTvPictureIssue = config.overlayDisabledDueToTvPictureIssue,
            )

        val applied =
            when (preferredMethod) {
                ScreenCloakMethod.TRANSPARENT_OVERLAY -> applyTransparentOverlay(config.mode)
                ScreenCloakMethod.SYSTEM_BRIGHTNESS_FALLBACK -> applySystemBrightness(config.mode)
                ScreenCloakMethod.NONE -> false
            }

        currentStatus =
            if (applied) {
                ScreenCloakStatus(
                    mode = config.mode,
                    method = preferredMethod,
                    state = ScreenCloakState.ACTIVE,
                    active = true,
                    overlayPermissionGranted = overlayAllowed,
                    systemWritePermissionGranted = systemWriteAllowed,
                    disabledDueToTvPictureIssue = config.overlayDisabledDueToTvPictureIssue,
                )
            } else {
                ScreenCloakStatus(
                    mode = config.mode,
                    method = preferredMethod,
                    state =
                    ScreenCloakPolicy.stateFor(
                        mode = config.mode,
                        overlayPermissionGranted = overlayAllowed,
                        systemWritePermissionGranted = systemWriteAllowed,
                        overlayDisabledDueToTvPictureIssue = config.overlayDisabledDueToTvPictureIssue,
                        failed = preferredMethod != ScreenCloakMethod.NONE,
                    ),
                    overlayPermissionGranted = overlayAllowed,
                    systemWritePermissionGranted = systemWriteAllowed,
                    disabledDueToTvPictureIssue = config.overlayDisabledDueToTvPictureIssue,
                )
            }
        return currentStatus
    }

    fun restore(): ScreenCloakStatus {
        currentStatus = currentStatus.copy(state = ScreenCloakState.RESTORING, active = false)
        val overlayRestored =
            runCatching {
                removeOverlay()
                true
            }.getOrDefault(false)
        val brightnessRestored = runCatching { restoreSystemBrightness() }.getOrDefault(true)
        val restoreSucceeded = overlayRestored && brightnessRestored
        currentStatus =
            currentStatus.copy(
                method = ScreenCloakMethod.NONE,
                state = ScreenCloakState.IDLE,
                active = false,
                restoreSucceeded = restoreSucceeded,
            )
        return currentStatus
    }

    private fun applyTransparentOverlay(mode: ScreenCloakMode): Boolean = runCatching {
        val brightness = ScreenCloakPolicy.overlayBrightness(mode) ?: return true
        val view =
            overlayView
                ?: FrameLayout(context).apply { setBackgroundColor(android.graphics.Color.TRANSPARENT) }.also {
                    overlayView =
                        it
                }
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    screenBrightness = brightness
                    alpha = 0f
                    dimAmount = 0f
                }
        if (overlayAttached) {
            windowManager.updateViewLayout(view, params)
        } else {
            windowManager.addView(view, params)
            overlayAttached = true
        }
        session = ScreenCloakSessionTracker.markApplied(session, ScreenCloakMethod.TRANSPARENT_OVERLAY)
        true
    }.onFailure {
        NoctLog.warn("ScreenCloak", "Transparent overlay failed: ${it.message}")
    }.getOrDefault(false)

    private fun applySystemBrightness(mode: ScreenCloakMode): Boolean = runCatching {
        val brightness = ScreenCloakPolicy.fallbackBrightness(mode) ?: return true
        val resolver = context.contentResolver
        session =
            ScreenCloakSessionTracker.captureOriginal(
                session = session,
                brightness =
                runCatching {
                    Settings.System.getInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                    )
                }.getOrNull(),
                brightnessMode =
                runCatching {
                    Settings.System.getInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                    )
                }.getOrNull(),
            )
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
        session = ScreenCloakSessionTracker.markApplied(session, ScreenCloakMethod.SYSTEM_BRIGHTNESS_FALLBACK)
        true
    }.onFailure {
        NoctLog.warn("ScreenCloak", "System brightness fallback failed: ${it.message}")
    }.getOrDefault(false)

    private fun restoreSystemBrightness(): Boolean {
        val appliedMethod = session.appliedMethod
        if (appliedMethod != ScreenCloakMethod.SYSTEM_BRIGHTNESS_FALLBACK || !session.capturedOriginal) {
            session = ScreenCloakSessionTracker.markRestored(session)
            return true
        }
        val resolver = context.contentResolver
        session.originalBrightnessMode?.let {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, it)
        }
        session.originalBrightness?.let {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, it)
        }
        session = ScreenCloakSessionTracker.markRestored(session)
        return true
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        if (overlayAttached) {
            windowManager.removeViewImmediate(view)
            overlayAttached = false
        }
    }
}
