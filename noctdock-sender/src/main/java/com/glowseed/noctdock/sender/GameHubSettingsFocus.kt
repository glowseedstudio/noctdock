package com.glowseed.noctdock.sender

import android.content.Context
import com.glowseed.noctdock.core.AccentTheme
import com.glowseed.noctdock.core.AppearanceDefaults
import com.glowseed.noctdock.core.BackgroundMotionMode
import com.glowseed.noctdock.core.ConsoleModeState
import com.glowseed.noctdock.core.GameHubControllerLayout
import com.glowseed.noctdock.core.GameHubLauncherLayout
import com.glowseed.noctdock.core.NebulaTheme
import com.glowseed.noctdock.core.ScreenCloakMode
import com.glowseed.noctdock.core.SoundMode
import com.glowseed.noctdock.core.UiDensity

internal sealed class GameHubSettingsFocusItem {
    data class Toggle(val label: String, val supportingText: String, val read: () -> Boolean, val write: (Boolean) -> Unit) : GameHubSettingsFocusItem()

    data class Option(val label: String, val options: List<String>, val read: () -> String, val write: (String) -> Unit) : GameHubSettingsFocusItem()

    data class Slider(val label: String, val supportingText: String, val range: IntRange, val read: () -> Int, val write: (Int) -> Unit) : GameHubSettingsFocusItem()

    data class ScreenCloak(val read: () -> ScreenCloakMode, val write: (ScreenCloakMode) -> Unit) : GameHubSettingsFocusItem()

    data class Sound(val read: () -> SoundMode, val write: (SoundMode) -> Unit) : GameHubSettingsFocusItem()

    data class Button(val label: String, val onClick: () -> Unit) : GameHubSettingsFocusItem()
}

internal sealed class GameHubSettingsRow {
    data class Section(val title: String, val subtitle: String) : GameHubSettingsRow()

    data class Focus(val item: GameHubSettingsFocusItem) : GameHubSettingsRow()

    data class Note(val text: String) : GameHubSettingsRow()

    data object Privacy : GameHubSettingsRow()
}

internal data class GameHubSettingsSectionBlock(val header: GameHubSettingsRow.Section, val children: List<GameHubSettingsRow>)

internal fun groupGameHubSettingsSections(rows: List<GameHubSettingsRow>): List<GameHubSettingsSectionBlock> {
    val blocks = mutableListOf<GameHubSettingsSectionBlock>()
    var header: GameHubSettingsRow.Section? = null
    val children = mutableListOf<GameHubSettingsRow>()
    rows.forEach { row ->
        when (row) {
            is GameHubSettingsRow.Section -> {
                if (header != null) {
                    blocks.add(GameHubSettingsSectionBlock(header, children.toList()))
                    children.clear()
                }
                header = row
            }

            else -> {
                children.add(row)
            }
        }
    }
    if (header != null) {
        blocks.add(GameHubSettingsSectionBlock(header, children))
    }
    return blocks
}

internal fun gameHubSettingsSectionForFocusIndex(rows: List<GameHubSettingsRow>, focusIndex: Int): String? {
    var currentSection: String? = null
    var cursor = 0
    rows.forEach { row ->
        when (row) {
            is GameHubSettingsRow.Section -> currentSection = row.title
            is GameHubSettingsRow.Focus -> {
                if (cursor == focusIndex) return currentSection
                cursor++
            }

            else -> Unit
        }
    }
    return null
}

internal fun gameHubSettingsSectionDefaultExpanded(title: String): Boolean = title != "Advanced"

internal fun gameHubSettingsFocusItemCount(rows: List<GameHubSettingsRow>): Int = rows.count { it is GameHubSettingsRow.Focus }.coerceAtLeast(1)

internal fun gameHubSettingsMoveDown(index: Int, count: Int): Int {
    if (count <= 0) return 0
    return (index + 1).coerceAtMost(count - 1)
}

internal fun gameHubSettingsMoveUp(index: Int, count: Int): Int {
    if (count <= 0) return 0
    return (index - 1).coerceAtLeast(0)
}

internal fun gameHubSettingsFocusItemAt(rows: List<GameHubSettingsRow>, index: Int): GameHubSettingsFocusItem? = rows.filterIsInstance<GameHubSettingsRow.Focus>().getOrNull(index)?.item

internal fun gameHubSettingsFocusIndexForLabel(rows: List<GameHubSettingsRow>, label: String): Int? {
    var cursor = 0
    rows.forEach { row ->
        if (row is GameHubSettingsRow.Focus) {
            val item = row.item
            if (item is GameHubSettingsFocusItem.Button && item.label == label) {
                return cursor
            }
            cursor++
        }
    }
    return null
}

internal fun buildGameHubSettingsRows(
    uiState: SenderUiState,
    viewModel: SenderViewModel,
    overlayAllowed: Boolean,
    systemWriteAllowed: Boolean,
    onScreenCloakTest: () -> Unit,
): List<GameHubSettingsRow> = buildList {
    add(GameHubSettingsRow.Section("Experience", "Feel immediate and calm."))
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Toggle(
                label = "Reduced motion",
                supportingText = "Keep background movement subtle.",
                read = { uiState.appearanceSettings.reducedMotion },
                write = viewModel::updateReducedMotion,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Toggle(
                label = "Auto return to screen",
                supportingText = "Jump back when your screen is available again.",
                read = { uiState.appearanceSettings.autoReconnect },
                write = viewModel::updateAutoReconnect,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Toggle(
                label = "Remember this screen",
                supportingText = "Keep your last trusted screen ready.",
                read = { uiState.appearanceSettings.rememberLastReceiver },
                write = viewModel::updateRememberLastReceiver,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Toggle(
                label = "Haptic feedback",
                supportingText = "Small vibration cues on confirm.",
                read = { uiState.appearanceSettings.hapticsEnabled },
                write = viewModel::updateHaptics,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.ScreenCloak(
                read = { uiState.appearanceSettings.screenCloakMode },
                write = viewModel::updateScreenCloakMode,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Note(
            gameHubScreenCloakDescription(uiState.appearanceSettings.screenCloakMode),
        ),
    )
    if (uiState.appearanceSettings.screenCloakMode != ScreenCloakMode.OFF) {
        if (!uiState.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue && !overlayAllowed) {
            add(
                GameHubSettingsRow.Focus(
                    GameHubSettingsFocusItem.Button(
                        label = "Allow display over other apps",
                        onClick = { /* started from stage via context */ },
                    ),
                ),
            )
        }
        if (uiState.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue && !systemWriteAllowed) {
            add(
                GameHubSettingsRow.Focus(
                    GameHubSettingsFocusItem.Button(
                        label = "Allow backup brightness control",
                        onClick = { },
                    ),
                ),
            )
        }
        if (uiState.consoleModeState == ConsoleModeState.Streaming) {
            add(
                GameHubSettingsRow.Focus(
                    GameHubSettingsFocusItem.Button(
                        label = "Test Screen Cloak",
                        onClick = onScreenCloakTest,
                    ),
                ),
            )
        }
    }
    add(GameHubSettingsRow.Section("Appearance", "Background, glow, and spacing."))
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Option(
                label = "Background",
                options = NebulaTheme.entries.map { AppearanceDefaults.backgroundLabel(it) },
                read = { AppearanceDefaults.backgroundLabel(uiState.appearanceSettings.backgroundTheme) },
                write = { label ->
                    NebulaTheme.entries
                        .firstOrNull { AppearanceDefaults.backgroundLabel(it) == label }
                        ?.let(viewModel::updateBackgroundTheme)
                },
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Option(
                label = "Background motion",
                options = BackgroundMotionMode.entries.map { AppearanceDefaults.backgroundMotionLabel(it) },
                read = {
                    AppearanceDefaults.backgroundMotionLabel(
                        uiState.appearanceSettings.backgroundMotionMode,
                    )
                },
                write = { label ->
                    BackgroundMotionMode.entries
                        .firstOrNull { AppearanceDefaults.backgroundMotionLabel(it) == label }
                        ?.let(viewModel::updateBackgroundMotionMode)
                },
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Option(
                label = "Accent",
                options = AccentTheme.entries.map { it.name },
                read = { uiState.appearanceSettings.accentTheme.name },
                write = { viewModel.updateAccentTheme(AccentTheme.valueOf(it)) },
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Option(
                label = "Controller layout",
                options = GameHubControllerLayout.entries.map { AppearanceDefaults.controllerLayoutLabel(it) },
                read = { AppearanceDefaults.controllerLayoutLabel(uiState.appearanceSettings.controllerLayout) },
                write = { label ->
                    GameHubControllerLayout.entries
                        .firstOrNull { AppearanceDefaults.controllerLayoutLabel(it) == label }
                        ?.let(viewModel::updateControllerLayout)
                },
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Option(
                label = "Home layout",
                options = GameHubLauncherLayout.entries.map { AppearanceDefaults.launcherLayoutLabel(it) },
                read = { AppearanceDefaults.launcherLayoutLabel(uiState.appearanceSettings.launcherLayout) },
                write = { label ->
                    GameHubLauncherLayout.entries
                        .firstOrNull { AppearanceDefaults.launcherLayoutLabel(it) == label }
                        ?.let(viewModel::updateLauncherLayout)
                },
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Option(
                label = "Layout spacing",
                options = UiDensity.entries.map { it.name },
                read = { uiState.appearanceSettings.uiDensity.name },
                write = { viewModel.updateDensity(UiDensity.valueOf(it)) },
            ),
        ),
    )
    add(GameHubSettingsRow.Section("Sound", "Where game audio plays."))
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Sound(
                read = { uiState.performanceSettings.soundMode },
                write = viewModel::updateSoundMode,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Note(
            gameHubSoundModeDescription(uiState.performanceSettings.soundMode),
        ),
    )
    add(GameHubSettingsRow.Section("Advanced", "Detailed controls for testing."))
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Toggle(
                label = "Lower handheld in TV Sound",
                supportingText = "Reduce handheld volume while the TV plays sound.",
                read = { uiState.performanceSettings.lowerHandheldInTvSound },
                write = viewModel::updateLowerHandheldInTvSound,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Toggle(
                label = "Show play overlay",
                supportingText = "Status layer while Console Mode is active.",
                read = { uiState.performanceSettings.showStreamOverlay },
                write = viewModel::updateOverlay,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Toggle(
                label = "Adaptive picture",
                supportingText = "React to changing Wi-Fi automatically.",
                read = { uiState.performanceSettings.adaptiveBitrateEnabled },
                write = viewModel::updateAdaptiveBitrate,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Slider(
                label = "Response window",
                supportingText = "Lower recovers faster; higher can look steadier.",
                range = 1..5,
                read = { uiState.performanceSettings.keyframeIntervalSeconds },
                write = viewModel::updateKeyframeInterval,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Slider(
                label = "Motion buffer",
                supportingText = "Smaller reduces delay; larger smooths rough Wi-Fi.",
                range = 1..8,
                read = { uiState.performanceSettings.maxQueueSize },
                write = viewModel::updateQueueSize,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Toggle(
                label = "Prefer newest frame",
                supportingText = "Drop older frames to keep controls responsive.",
                read = { uiState.performanceSettings.allowFrameDropping },
                write = viewModel::updateFrameDropping,
            ),
        ),
    )
    add(
        GameHubSettingsRow.Focus(
            GameHubSettingsFocusItem.Slider(
                label = "Picture limit",
                supportingText = "Manual picture ceiling for network testing.",
                range = 4..55,
                read = {
                    uiState.performanceSettings.manualBitrateMbps
                        ?: uiState.performanceSettings.selectedProfile.bitrateMbps
                },
                write = viewModel::updateManualBitrate,
            ),
        ),
    )
    if (uiState.performanceSettings.manualBitrateMbps != null) {
        add(
            GameHubSettingsRow.Focus(
                GameHubSettingsFocusItem.Button(
                    label = "Use mode default",
                    onClick = { viewModel.updateManualBitrate(null) },
                ),
            ),
        )
    }
    add(GameHubSettingsRow.Section("About", "Privacy, version, and tools."))
    add(GameHubSettingsRow.Privacy)
    add(GameHubSettingsRow.Note("NoctDock version 0.1.0"))
    add(GameHubSettingsRow.Note("Built by Glowseed Studio"))
}

internal fun gameHubSettingsPerformAccept(item: GameHubSettingsFocusItem, context: Context, overlayAllowed: Boolean, onScreenCloakModeSelected: (ScreenCloakMode) -> Unit) {
    when (item) {
        is GameHubSettingsFocusItem.Toggle -> {
            item.write(!item.read())
        }

        is GameHubSettingsFocusItem.Option -> {
            val next = gameHubCycleOption(item.options, item.read(), forward = true)
            item.write(next)
        }

        is GameHubSettingsFocusItem.Slider -> {
            Unit
        }

        is GameHubSettingsFocusItem.ScreenCloak -> {
            val modes = ScreenCloakMode.entries
            val next = gameHubCycleEnum(modes, item.read(), forward = true)
            item.write(next)
            onScreenCloakModeSelected(next)
        }

        is GameHubSettingsFocusItem.Sound -> {
            val next = gameHubCycleEnum(SoundMode.entries, item.read(), forward = true)
            item.write(next)
        }

        is GameHubSettingsFocusItem.Button -> {
            when (item.label) {
                "Allow display over other apps" -> {
                    context.startActivity(ScreenCloakPermissionHelper.overlayPermissionIntent(context))
                }

                "Allow backup brightness control" -> {
                    context.startActivity(ScreenCloakPermissionHelper.writeSettingsIntent(context))
                }

                else -> {
                    item.onClick()
                }
            }
        }
    }
}

internal fun gameHubSettingsPerformHorizontal(item: GameHubSettingsFocusItem, forward: Boolean, context: Context, onScreenCloakModeSelected: (ScreenCloakMode) -> Unit) {
    when (item) {
        is GameHubSettingsFocusItem.Toggle -> {
            item.write(!item.read())
        }

        is GameHubSettingsFocusItem.Option -> {
            item.write(gameHubCycleOption(item.options, item.read(), forward))
        }

        is GameHubSettingsFocusItem.Slider -> {
            val value = item.read().coerceIn(item.range)
            val next = if (forward) value + 1 else value - 1
            item.write(next.coerceIn(item.range))
        }

        is GameHubSettingsFocusItem.ScreenCloak -> {
            val next = gameHubCycleEnum(ScreenCloakMode.entries, item.read(), forward)
            item.write(next)
            onScreenCloakModeSelected(next)
        }

        is GameHubSettingsFocusItem.Sound -> {
            item.write(gameHubCycleEnum(SoundMode.entries, item.read(), forward))
        }

        is GameHubSettingsFocusItem.Button -> {
            Unit
        }
    }
}

private fun gameHubCycleOption(options: List<String>, selected: String, forward: Boolean): String {
    if (options.isEmpty()) return selected
    val index = options.indexOf(selected).coerceAtLeast(0)
    val next =
        if (forward) {
            (index + 1) % options.size
        } else {
            (index - 1 + options.size) % options.size
        }
    return options[next]
}

private fun <T> gameHubCycleEnum(entries: List<T>, current: T, forward: Boolean): T {
    if (entries.isEmpty()) return current
    val index = entries.indexOf(current).coerceAtLeast(0)
    val next =
        if (forward) {
            (index + 1) % entries.size
        } else {
            (index - 1 + entries.size) % entries.size
        }
    return entries[next]
}

private fun gameHubScreenCloakDescription(mode: ScreenCloakMode): String = mode.description

private fun gameHubSoundModeDescription(mode: SoundMode): String = when (mode) {
    SoundMode.RETROID -> "Sound on handheld."
    SoundMode.TV -> "Sound on screen."
    SoundMode.BOTH -> "Both devices; may echo."
    SoundMode.QUIET -> "Quieter handheld while docked."
}
