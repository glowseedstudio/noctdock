package com.glowseed.noctdock.sender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.LocalNoctAccent
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctGlassCard
import com.glowseed.noctdock.core.NoctSecondaryButton
import com.glowseed.noctdock.core.NoctSelectableCard
import com.glowseed.noctdock.core.NoctSpacing
import com.glowseed.noctdock.core.NoctStatusPill
import com.glowseed.noctdock.core.Smooth60HzMode
import com.glowseed.noctdock.core.SoundMode
import com.glowseed.noctdock.core.StreamProfile
import com.glowseed.noctdock.core.StreamProfiles
import com.glowseed.noctdock.core.noctSpace

private const val GAME_HUB_CONSOLE_MODES_PREFERENCE_TOGGLE_COUNT = 4
private val GAME_HUB_CONSOLE_MODES_SMOOTH60HZ_COUNT = Smooth60HzMode.entries.size

internal sealed class GameHubConsoleModesFocusKind {
    data class Profile(val index: Int) : GameHubConsoleModesFocusKind()

    data class PreferenceToggle(val slot: Int) : GameHubConsoleModesFocusKind()

    data class Smooth60Hz(val index: Int) : GameHubConsoleModesFocusKind()

    data object TestConnection : GameHubConsoleModesFocusKind()
}

internal fun gameHubConsoleModesItemCount(profileCount: Int): Int {
    val profiles = profileCount.coerceAtLeast(1)
    return profiles + GAME_HUB_CONSOLE_MODES_PREFERENCE_TOGGLE_COUNT + GAME_HUB_CONSOLE_MODES_SMOOTH60HZ_COUNT + 1
}

/** First Picture-and-play toggle row (Fast response) — default focus when entering the panel. */
internal fun gameHubConsoleModesPreferenceStartIndex(profileCount: Int): Int = profileCount.coerceAtLeast(1)

internal fun gameHubConsoleModesFocusKind(index: Int, profileCount: Int): GameHubConsoleModesFocusKind? {
    val profiles = profileCount.coerceAtLeast(1)
    val total = gameHubConsoleModesItemCount(profileCount)
    if (index !in 0 until total) return null
    return when {
        index < profiles -> GameHubConsoleModesFocusKind.Profile(index)

        index < profiles + GAME_HUB_CONSOLE_MODES_PREFERENCE_TOGGLE_COUNT ->
            GameHubConsoleModesFocusKind.PreferenceToggle(index - profiles)

        index < profiles + GAME_HUB_CONSOLE_MODES_PREFERENCE_TOGGLE_COUNT + GAME_HUB_CONSOLE_MODES_SMOOTH60HZ_COUNT ->
            GameHubConsoleModesFocusKind.Smooth60Hz(
                index - profiles - GAME_HUB_CONSOLE_MODES_PREFERENCE_TOGGLE_COUNT,
            )

        else -> GameHubConsoleModesFocusKind.TestConnection
    }
}

internal fun gameHubConsoleModesPerformAccept(kind: GameHubConsoleModesFocusKind, uiState: SenderUiState, viewModel: SenderViewModel, availableProfiles: List<StreamProfile>) {
    when (kind) {
        is GameHubConsoleModesFocusKind.Profile ->
            availableProfiles.getOrNull(kind.index)?.let { viewModel.selectProfile(it.id) }

        is GameHubConsoleModesFocusKind.PreferenceToggle ->
            when (kind.slot) {
                0 -> viewModel.updateLowLatency(!uiState.performanceSettings.preferLowLatencyCodec)
                1 -> viewModel.updateAdaptiveBitrate(!uiState.performanceSettings.adaptiveBitrateEnabled)
                2 -> viewModel.updateBatterySaver(!uiState.performanceSettings.batterySaverMode)
                3 -> viewModel.updateOverlay(!uiState.performanceSettings.showStreamOverlay)
            }

        is GameHubConsoleModesFocusKind.Smooth60Hz ->
            Smooth60HzMode.entries.getOrNull(kind.index)?.let(viewModel::updateSmooth60HzMode)

        GameHubConsoleModesFocusKind.TestConnection ->
            if (!uiState.connectionTestRunning) viewModel.testConnection()
    }
}

internal fun gameHubConsoleModesPerformHorizontal(kind: GameHubConsoleModesFocusKind, forward: Boolean, uiState: SenderUiState, viewModel: SenderViewModel) {
    when (kind) {
        is GameHubConsoleModesFocusKind.PreferenceToggle ->
            gameHubConsoleModesPerformAccept(kind, uiState, viewModel, emptyList())

        is GameHubConsoleModesFocusKind.Smooth60Hz -> {
            val modes = Smooth60HzMode.entries
            val current = uiState.performanceSettings.smooth60HzMode
            val currentIndex = modes.indexOf(current).coerceAtLeast(0)
            val nextIndex =
                if (forward) {
                    (currentIndex + 1) % modes.size
                } else {
                    (currentIndex - 1 + modes.size) % modes.size
                }
            viewModel.updateSmooth60HzMode(modes[nextIndex])
        }

        else -> Unit
    }
}

internal fun gameHubConsoleModesMoveDown(index: Int, count: Int): Int {
    if (count <= 0) return 0
    return (index + 1).coerceAtMost(count - 1)
}

internal fun gameHubConsoleModesMoveUp(index: Int, count: Int): Int {
    if (count <= 0) return 0
    return (index - 1).coerceAtLeast(0)
}

internal fun gameHubConsoleModesDeviceRecommendation(uiState: SenderUiState): String {
    val selectedProfile = uiState.performanceSettings.selectedProfile
    return if (selectedProfile.id == StreamProfiles.Quality.id && uiState.deviceProfile.qualityWarning != null) {
        uiState.deviceProfile.qualityWarning ?: uiState.deviceProfile.settingsRecommendation
    } else {
        uiState.deviceProfile.settingsRecommendation
    }
}

@Composable
internal fun GameHubConsoleModesStage(
    uiState: SenderUiState,
    viewModel: SenderViewModel,
    availableProfiles: List<StreamProfile>,
    focusedIndex: Int,
    listInputActive: Boolean,
    reducedMotion: Boolean,
) {
    val selectedProfile = uiState.performanceSettings.selectedProfile
    val deviceRecommendation = gameHubConsoleModesDeviceRecommendation(uiState)
    val scrollState = rememberScrollState()
    val bringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(focusedIndex, listInputActive, availableProfiles.size) {
        if (!listInputActive) return@LaunchedEffect
        bringIntoView.bringIntoView()
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameHubConsoleModesFeaturedStrip(
            profile = selectedProfile,
            batterySaver = uiState.performanceSettings.batterySaverMode,
            soundMode = uiState.performanceSettings.soundMode,
            deviceRecommendation = deviceRecommendation,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Modes",
                color = NoctColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
            NoctStatusPill("${availableProfiles.size}", NoctColors.Cyan)
        }
        availableProfiles.forEachIndexed { index, profile ->
            GameHubConsoleModeProfileCard(
                profile = profile,
                selected = uiState.performanceSettings.selectedProfileId == profile.id,
                highlighted = listInputActive && focusedIndex == index,
                reducedMotion = reducedMotion,
                onSelect = { viewModel.selectProfile(profile.id) },
                modifier =
                if (listInputActive && focusedIndex == index) {
                    Modifier.bringIntoViewRequester(bringIntoView)
                } else {
                    Modifier
                },
            )
        }
        GameHubConsoleModesPreferencesCard(
            uiState = uiState,
            viewModel = viewModel,
            focusedIndex = focusedIndex,
            profileCount = availableProfiles.size,
            listInputActive = listInputActive,
            reducedMotion = reducedMotion,
        )
        GameHubConsoleModesConnectionRow(
            uiState = uiState,
            viewModel = viewModel,
            highlighted =
            listInputActive &&
                gameHubConsoleModesFocusKind(focusedIndex, availableProfiles.size) is GameHubConsoleModesFocusKind.TestConnection,
            reducedMotion = reducedMotion,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun GameHubConsoleModesFeaturedStrip(
    profile: StreamProfile,
    batterySaver: Boolean,
    soundMode: SoundMode,
    deviceRecommendation: String,
) {
    val accent = consoleModeAccent(profile)
    val shape = RoundedCornerShape(18.dp)
    val chips =
        buildList {
            addAll(consoleModeChips(profile))
            add(soundMode.label.replace(" Mode", ""))
            if (batterySaver) add("Battery Saver")
        }

    Box(modifier = Modifier.fillMaxWidth().clip(shape)) {
        NoctGlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(
                            brush =
                            Brush.radialGradient(
                                colors =
                                listOf(
                                    accent.copy(alpha = 0.48f),
                                    NoctColors.Magenta.copy(alpha = 0.28f),
                                    NoctColors.Violet.copy(alpha = 0.16f),
                                    Color(0x88101820),
                                ),
                                center = Offset(size.width * 0.24f, size.height * 0.32f),
                                radius = size.maxDimension * 0.98f,
                            ),
                            cornerRadius = CornerRadius(18.dp.toPx()),
                        )
                        drawRoundRect(
                            brush =
                            Brush.linearGradient(
                                colors =
                                listOf(
                                    Color(0x00000000),
                                    Color(0x88080C12),
                                ),
                                start = Offset(0f, size.height * 0.45f),
                                end = Offset(size.width, size.height),
                            ),
                            cornerRadius = CornerRadius(18.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Current settings",
                        color = NoctColors.TextSecondary.copy(alpha = 0.94f),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        deviceRecommendation,
                        color = accent.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConsoleModeOrbBadge(profile = profile, accent = accent, modifier = Modifier.size(40.dp), iconSize = 18.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            profile.title,
                            color = NoctColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            consoleModeDescription(profile),
                            color = NoctColors.TextSecondary.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { label ->
                        NoctStatusPill(label, accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHubConsoleModeProfileCard(profile: StreamProfile, selected: Boolean, highlighted: Boolean, reducedMotion: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val accent = consoleModeAccent(profile)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(shape)
            .gameHubFocusRing(
                shape = shape,
                accent = accent,
                focused = highlighted,
                strokeDp = if (highlighted) 3.5f else 1f,
                reducedMotion = reducedMotion,
                cornerRadius = 16.dp,
            ),
    ) {
        NoctSelectableCard(
            selected = selected,
            onClick = onSelect,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConsoleModeOrbBadge(profile = profile, accent = accent, modifier = Modifier.size(38.dp), iconSize = 16.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        profile.title,
                        color = NoctColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        consoleModeDescription(profile),
                        color = NoctColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    NoctStatusPill("Active", accent)
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                consoleModeChips(profile).forEach { chip ->
                    NoctStatusPill(chip, accent)
                }
            }
        }
    }
}

@Composable
private fun GameHubConsoleModesPreferencesCard(uiState: SenderUiState, viewModel: SenderViewModel, focusedIndex: Int, profileCount: Int, listInputActive: Boolean, reducedMotion: Boolean) {
    fun toggleHighlighted(slot: Int): Boolean {
        val kind = gameHubConsoleModesFocusKind(focusedIndex, profileCount)
        return listInputActive && kind is GameHubConsoleModesFocusKind.PreferenceToggle && kind.slot == slot
    }

    NoctGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Picture and play",
                color = NoctColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
            GameHubCompactPreferenceToggle(
                label = "Fast response",
                checked = uiState.performanceSettings.preferLowLatencyCodec,
                onCheckedChange = viewModel::updateLowLatency,
                supportingText = "Prioritise lower delay over picture polish.",
                highlighted = toggleHighlighted(0),
                reducedMotion = reducedMotion,
            )
            GameHubCompactPreferenceToggle(
                label = "Adaptive picture",
                checked = uiState.performanceSettings.adaptiveBitrateEnabled,
                onCheckedChange = viewModel::updateAdaptiveBitrate,
                supportingText = "Adjust picture quality when Wi-Fi changes.",
                highlighted = toggleHighlighted(1),
                reducedMotion = reducedMotion,
            )
            GameHubCompactPreferenceToggle(
                label = "Battery Saver",
                checked = uiState.performanceSettings.batterySaverMode,
                onCheckedChange = viewModel::updateBatterySaver,
                supportingText = "Ease power and heat during longer sessions.",
                highlighted = toggleHighlighted(2),
                reducedMotion = reducedMotion,
            )
            GameHubCompactPreferenceToggle(
                label = "Play overlay",
                checked = uiState.performanceSettings.showStreamOverlay,
                onCheckedChange = viewModel::updateOverlay,
                supportingText = "Show lightweight status while Console Mode is active.",
                highlighted = toggleHighlighted(3),
                reducedMotion = reducedMotion,
            )
            GameHubSmooth60HzSection(
                uiState = uiState,
                viewModel = viewModel,
                focusedIndex = focusedIndex,
                profileCount = profileCount,
                listInputActive = listInputActive,
                reducedMotion = reducedMotion,
            )
        }
    }
}

@Composable
private fun GameHubCompactPreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, supportingText: String, highlighted: Boolean = false, reducedMotion: Boolean = false) {
    val accent = LocalNoctAccent.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .gameHubFocusRing(
                shape = shape,
                accent = accent,
                focused = highlighted,
                strokeDp = if (highlighted) 3.5f else 1f,
                reducedMotion = reducedMotion,
                cornerRadius = 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                SwitchDefaults.colors(
                    checkedThumbColor = NoctColors.TextPrimary,
                    checkedTrackColor = accent.copy(alpha = 0.72f),
                    checkedBorderColor = accent.copy(alpha = 0.82f),
                    uncheckedThumbColor = NoctColors.TextSecondary,
                    uncheckedTrackColor = NoctColors.Glass,
                    uncheckedBorderColor = NoctColors.GlassBorder,
                ),
            )
        }
        Text(supportingText, color = NoctColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GameHubSmooth60HzSection(uiState: SenderUiState, viewModel: SenderViewModel, focusedIndex: Int, profileCount: Int, listInputActive: Boolean, reducedMotion: Boolean) {
    val accent = LocalNoctAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Smooth 60 Hz",
            color = NoctColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Request 60 Hz where your handheld allows it.",
            color = NoctColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Smooth60HzMode.entries.forEachIndexed { modeIndex, mode ->
            val label =
                when (mode) {
                    Smooth60HzMode.Off -> "Off"
                    Smooth60HzMode.AskOnStart -> "Ask on start"
                    Smooth60HzMode.Always -> "Always"
                }
            val highlighted =
                listInputActive &&
                    gameHubConsoleModesFocusKind(focusedIndex, profileCount) is GameHubConsoleModesFocusKind.Smooth60Hz &&
                    (gameHubConsoleModesFocusKind(focusedIndex, profileCount) as GameHubConsoleModesFocusKind.Smooth60Hz).index == modeIndex
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .gameHubFocusRing(
                        shape = shape,
                        accent = accent,
                        focused = highlighted,
                        strokeDp = if (highlighted) 3.5f else 1f,
                        reducedMotion = reducedMotion,
                        cornerRadius = 12.dp,
                    ),
            ) {
                NoctSelectableCard(
                    selected = uiState.performanceSettings.smooth60HzMode == mode,
                    onClick = { viewModel.updateSmooth60HzMode(mode) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = noctSpace(NoctSpacing.sm), vertical = noctSpace(NoctSpacing.xs)),
                ) {
                    Text(
                        label,
                        color = NoctColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHubConsoleModesConnectionRow(uiState: SenderUiState, viewModel: SenderViewModel, highlighted: Boolean, reducedMotion: Boolean) {
    val accent = LocalNoctAccent.current
    val shape = RoundedCornerShape(16.dp)
    NoctGlassCard(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .gameHubFocusRing(
                shape = shape,
                accent = accent,
                focused = highlighted,
                strokeDp = if (highlighted) 3.5f else 1f,
                reducedMotion = reducedMotion,
                cornerRadius = 16.dp,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoctSecondaryButton(
                    text = if (uiState.connectionTestRunning) "Testing..." else "Test connection",
                    onClick = { if (!uiState.connectionTestRunning) viewModel.testConnection() },
                    minHeight = 40.dp,
                )
                uiState.connectionTestResult?.let { result ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            result.friendlyLabel,
                            color = NoctColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (result.isStale()) "Test again recommended." else result.explanation,
                            color = NoctColors.TextSecondary.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
