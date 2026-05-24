package com.glowseed.noctdock.sender

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.AppearanceDefaults
import com.glowseed.noctdock.core.LocalNoctAccent
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctGlassCard
import com.glowseed.noctdock.core.NoctMetricRow
import com.glowseed.noctdock.core.NoctPrimaryButton
import com.glowseed.noctdock.core.NoctPrivacyBar
import com.glowseed.noctdock.core.NoctSecondaryButton
import com.glowseed.noctdock.core.NoctSelectableCard
import com.glowseed.noctdock.core.NoctSpacing
import com.glowseed.noctdock.core.ScreenCloakMode
import com.glowseed.noctdock.core.SoundMode
import com.glowseed.noctdock.core.StreamHealth
import com.glowseed.noctdock.core.StreamHealthCalculator
import com.glowseed.noctdock.core.noctSpace
import kotlin.math.roundToInt

@Composable
internal fun GameHubSettingsStage(
    uiState: SenderUiState,
    viewModel: SenderViewModel,
    focusedIndex: Int,
    listInputActive: Boolean,
    reducedMotion: Boolean,
    systemStatusExpanded: Boolean,
    onSystemStatusExpandedChange: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
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
                onScreenCloakTest = {
                    viewModel.refreshScreenCloakTest()
                    showScreenCloakResultDialog = true
                },
            )
        }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(focusedIndex, listInputActive, rows) {
        if (!listInputActive) return@LaunchedEffect
        bringIntoView.bringIntoView()
        gameHubSettingsSectionForFocusIndex(rows, focusedIndex)?.let { sectionTitle ->
            expandedSections[sectionTitle] = true
        }
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
            val sectionTitle = block.header.title
            val sectionExpanded = expandedSections[sectionTitle] ?: gameHubSettingsSectionDefaultExpanded(sectionTitle)
            GameHubSettingsSection(
                title = sectionTitle,
                subtitle = block.header.subtitle,
                expanded = sectionExpanded,
                onExpandedChange = { expandedSections[sectionTitle] = it },
            ) {
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
        GameHubSettingsSystemStatusCard(
            uiState = uiState,
            viewModel = viewModel,
            expanded = systemStatusExpanded,
            onExpandedChange = onSystemStatusExpandedChange,
            onOpenDiagnostics = onOpenDiagnostics,
        )
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
    val shape = RoundedCornerShape(18.dp)
    val summary = gameHubSettingsActiveSummary(uiState)

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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Current settings",
                    color = NoctColors.TextSecondary.copy(alpha = 0.94f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    summary,
                    color = accent.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Hub appearance, sound routing, and streaming preferences.",
                    color = NoctColors.TextSecondary.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun gameHubSettingsActiveSummary(uiState: SenderUiState): String {
    val appearance = uiState.appearanceSettings
    return listOf(
        appearance.accentTheme.name,
        appearance.uiDensity.name,
        AppearanceDefaults.launcherLayoutLabel(appearance.launcherLayout),
        uiState.performanceSettings.soundMode.label.replace(" Mode", ""),
        appearance.screenCloakMode.label,
    ).joinToString(" · ")
}

@Composable
private fun GameHubSettingsSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(260),
        label = "settings-section-chevron",
    )

    Box(modifier = Modifier.fillMaxWidth().clip(shape)) {
        NoctGlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onExpandedChange(!expanded) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(title, color = NoctColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            subtitle,
                            color = NoctColors.TextSecondary.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GameHubCollapseChevron(
                        modifier = Modifier.rotate(chevronRotation),
                        tint = NoctColors.TextSecondary.copy(alpha = 0.92f),
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
                ) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(NoctColors.GlassBorder.copy(alpha = 0.45f)),
                        )
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHubSettingsSystemStatusCard(
    uiState: SenderUiState,
    viewModel: SenderViewModel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val accent = LocalNoctAccent.current
    val shape = RoundedCornerShape(18.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(260),
        label = "system-status-chevron",
    )
    val snapshot = viewModel.diagnosticsSnapshot()
    val streamGrade =
        StreamHealthCalculator.grade(
            StreamHealth(
                packetLossPercent = snapshot.metrics.packetLossPercent,
                jitterMs = snapshot.metrics.jitterMs,
                queueDepth = snapshot.metrics.queueDepth,
                droppedFramesPerMinute = snapshot.metrics.droppedFrames,
                receiverDecodeErrors = 0,
                thermalThrottling = false,
                encoderOverloaded = false,
            ),
        )

    Box(modifier = Modifier.fillMaxWidth().clip(shape)) {
        NoctGlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onExpandedChange(!expanded) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "System Status",
                            color = NoctColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Detailed Console Mode health",
                            color = NoctColors.TextSecondary.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    GameHubCollapseChevron(
                        modifier = Modifier.rotate(chevronRotation),
                        tint = NoctColors.TextSecondary.copy(alpha = 0.92f),
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
                ) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(NoctColors.GlassBorder.copy(alpha = 0.45f)),
                        )
                        NoctMetricRow("Stream health", streamGrade.name)
                        NoctMetricRow("Current screen", snapshot.receiverName)
                        NoctMetricRow("Connection state", snapshot.connectionState.name)
                        NoctMetricRow("Stream status", snapshot.streamState.name)
                        NoctMetricRow("Connection test", snapshot.connectionTestResult?.friendlyLabel ?: "Not run")
                        NoctMetricRow("Detected handheld", snapshot.deviceProfile)
                        NoctMetricRow("Recommended mode", snapshot.recommendedProfile)
                        NoctMetricRow("Last error", snapshot.lastError?.message ?: "None")
                        NoctPrimaryButton(
                            text = "Copy support report",
                            onClick = viewModel::copySupportReportToClipboard,
                            minHeight = 42.dp,
                        )
                        if (uiState.diagnosticsCopied) {
                            Text(
                                "Support report copied to clipboard.",
                                color = NoctColors.Green,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "View full System Status",
                            color = accent.copy(alpha = 0.92f),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable(onClick = onOpenDiagnostics),
                        )
                    }
                }
            }
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
