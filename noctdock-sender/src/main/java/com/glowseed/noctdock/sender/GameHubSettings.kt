package com.glowseed.noctdock.sender

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.LocalNoctAccent
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctGlassCard
import com.glowseed.noctdock.core.NoctPrivacyBar
import com.glowseed.noctdock.core.NoctSecondaryButton
import com.glowseed.noctdock.core.NoctSelectableCard
import com.glowseed.noctdock.core.NoctSpacing
import com.glowseed.noctdock.core.NoctStatusPill
import com.glowseed.noctdock.core.ScreenCloakMode
import com.glowseed.noctdock.core.SoundMode
import com.glowseed.noctdock.core.noctSpace
import kotlin.math.roundToInt

@Composable
internal fun GameHubSettingsStage(uiState: SenderUiState, viewModel: SenderViewModel, focusedIndex: Int, listInputActive: Boolean, reducedMotion: Boolean, onOpenDiagnostics: () -> Unit) {
    val context = LocalContext.current
    var showScreenCloakResultDialog by remember { mutableStateOf(false) }
    val overlayAllowed = ScreenCloakPermissionHelper.canDrawOverlays(context)
    val systemWriteAllowed = ScreenCloakPermissionHelper.canWriteSystemSettings(context)
    val scrollState = rememberScrollState()
    val bringIntoView = remember { BringIntoViewRequester() }
    val onScreenCloakModeSelected = remember(context, uiState.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue) {
        { mode: ScreenCloakMode ->
            viewModel.updateScreenCloakMode(mode)
            if (
                mode != ScreenCloakMode.OFF &&
                !uiState.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue &&
                !ScreenCloakPermissionHelper.canDrawOverlays(context)
            ) {
                context.startActivity(ScreenCloakPermissionHelper.overlayPermissionIntent(context))
            }
        }
    }
    val rows =
        remember(
            uiState,
            overlayAllowed,
            systemWriteAllowed,
        ) {
            buildGameHubSettingsRows(
                uiState = uiState,
                viewModel = viewModel,
                overlayAllowed = overlayAllowed,
                systemWriteAllowed = systemWriteAllowed,
                onOpenDiagnostics = onOpenDiagnostics,
                onScreenCloakTest = {
                    viewModel.refreshScreenCloakTest()
                    showScreenCloakResultDialog = true
                },
            )
        }
    LaunchedEffect(focusedIndex, listInputActive) {
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
        GameHubSettingsFeaturedStrip(uiState = uiState)
        var focusCursor = 0
        groupGameHubSettingsSections(rows).forEach { block ->
            GameHubSettingsSection(title = block.header.title, subtitle = block.header.subtitle) {
                block.children.forEach { row ->
                    when (row) {
                        is GameHubSettingsRow.Note ->
                            Text(
                                row.text,
                                color = NoctColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )

                        is GameHubSettingsRow.Privacy ->
                            NoctPrivacyBar(modifier = Modifier.fillMaxWidth())

                        is GameHubSettingsRow.Focus -> {
                            val itemFocusIndex = focusCursor++
                            val highlighted = listInputActive && focusedIndex == itemFocusIndex
                            val focusModifier =
                                if (highlighted) {
                                    Modifier.bringIntoViewRequester(bringIntoView)
                                } else {
                                    Modifier
                                }
                            GameHubSettingsFocusableRow(
                                highlighted = highlighted,
                                reducedMotion = reducedMotion,
                                modifier = focusModifier,
                            ) {
                                when (val item = row.item) {
                                    is GameHubSettingsFocusItem.Toggle ->
                                        GameHubSettingsToggle(
                                            label = item.label,
                                            checked = item.read(),
                                            onCheckedChange = item.write,
                                            supportingText = item.supportingText,
                                        )

                                    is GameHubSettingsFocusItem.Option ->
                                        GameHubSettingsOptionPicker(
                                            label = item.label,
                                            options = item.options,
                                            selected = item.read(),
                                            onSelect = item.write,
                                        )

                                    is GameHubSettingsFocusItem.Slider ->
                                        GameHubSettingsSlider(
                                            label = item.label,
                                            value = item.read(),
                                            range = item.range,
                                            onValueChange = item.write,
                                            supportingText = item.supportingText,
                                        )

                                    is GameHubSettingsFocusItem.ScreenCloak ->
                                        GameHubSettingsScreenCloakPicker(
                                            selectedMode = item.read(),
                                            onSelect = {
                                                item.write(it)
                                                onScreenCloakModeSelected(it)
                                            },
                                        )

                                    is GameHubSettingsFocusItem.Sound ->
                                        GameHubSettingsSoundPicker(
                                            selectedMode = item.read(),
                                            onSelect = item.write,
                                        )

                                    is GameHubSettingsFocusItem.Button ->
                                        NoctSecondaryButton(
                                            text = item.label,
                                            onClick = {
                                                gameHubSettingsPerformAccept(
                                                    item = item,
                                                    context = context,
                                                    overlayAllowed = overlayAllowed,
                                                    onScreenCloakModeSelected = onScreenCloakModeSelected,
                                                )
                                            },
                                            minHeight = 40.dp,
                                        )
                                }
                            }
                        }

                        is GameHubSettingsRow.Section -> Unit
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }

    if (showScreenCloakResultDialog) {
        AlertDialog(
            onDismissRequest = { showScreenCloakResultDialog = false },
            title = { Text("Screen Cloak test") },
            text = { Text("Did the TV picture stay clear while the handheld darkened?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showScreenCloakResultDialog = false
                        viewModel.updateScreenCloakOverlayBlocked(false)
                    },
                ) { Text("TV picture stayed clear") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showScreenCloakResultDialog = false
                        viewModel.updateScreenCloakOverlayBlocked(true)
                        if (!ScreenCloakPermissionHelper.canWriteSystemSettings(context)) {
                            context.startActivity(ScreenCloakPermissionHelper.writeSettingsIntent(context))
                        }
                    },
                ) { Text("TV picture went dark") }
            },
        )
    }
}

@Composable
private fun GameHubSettingsFocusableRow(highlighted: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val accent = LocalNoctAccent.current
    val shape = RoundedCornerShape(14.dp)
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
                cornerRadius = 14.dp,
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        content()
    }
}

@Composable
private fun GameHubSettingsFeaturedStrip(uiState: SenderUiState) {
    val accent = LocalNoctAccent.current
    val chips =
        listOf(
            uiState.appearanceSettings.accentTheme.name,
            uiState.appearanceSettings.uiDensity.name,
            uiState.performanceSettings.soundMode.label.replace(" Mode", ""),
            uiState.appearanceSettings.screenCloakMode.label,
        )
    NoctGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Settings",
                color = NoctColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Tune experience, appearance, and sound without leaving the hub.",
                color = NoctColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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

@Composable
private fun GameHubSettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    NoctGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, color = NoctColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun GameHubSettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, supportingText: String) {
    val accent = LocalNoctAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
private fun GameHubSettingsSlider(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit, supportingText: String) {
    val accent = LocalNoctAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label: $value",
            color = NoctColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(supportingText, color = NoctColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )
    }
}

@Composable
private fun GameHubSettingsOptionPicker(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                NoctSelectableCard(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    modifier = Modifier.widthIn(min = 120.dp),
                    contentPadding = PaddingValues(horizontal = noctSpace(NoctSpacing.sm), vertical = noctSpace(NoctSpacing.xs)),
                ) {
                    Text(
                        option,
                        color = NoctColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHubSettingsScreenCloakPicker(selectedMode: ScreenCloakMode, onSelect: (ScreenCloakMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Screen Cloak", color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenCloakMode.entries.forEach { mode ->
                NoctSelectableCard(
                    selected = selectedMode == mode,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.widthIn(min = 140.dp),
                    contentPadding = PaddingValues(horizontal = noctSpace(NoctSpacing.sm), vertical = noctSpace(NoctSpacing.xs)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            mode.label,
                            color = NoctColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            mode.description,
                            color = NoctColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHubSettingsSoundPicker(selectedMode: SoundMode, onSelect: (SoundMode) -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SoundMode.entries.forEach { mode ->
            NoctSelectableCard(
                selected = mode == selectedMode,
                onClick = { onSelect(mode) },
                modifier = Modifier.widthIn(min = 130.dp),
                contentPadding = PaddingValues(horizontal = noctSpace(NoctSpacing.sm), vertical = noctSpace(NoctSpacing.xs)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        mode.label,
                        color = NoctColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        gameHubSettingsSoundModeDescription(mode),
                        color = NoctColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun gameHubSettingsSoundModeDescription(mode: SoundMode): String = when (mode) {
    SoundMode.RETROID -> "Sound on handheld."
    SoundMode.TV -> "Sound on screen."
    SoundMode.BOTH -> "Both devices; may echo."
    SoundMode.QUIET -> "Quieter handheld while docked."
}
