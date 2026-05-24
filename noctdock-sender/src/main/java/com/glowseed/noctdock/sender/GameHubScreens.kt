package com.glowseed.noctdock.sender

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glowseed.noctdock.core.DiscoveredReceiver
import com.glowseed.noctdock.core.LocalNoctAccent
import com.glowseed.noctdock.core.NoctColors
import com.glowseed.noctdock.core.NoctGlassCard
import com.glowseed.noctdock.core.NoctOrb
import com.glowseed.noctdock.core.NoctPrimaryButton
import com.glowseed.noctdock.core.NoctSecondaryButton
import com.glowseed.noctdock.core.NoctStatusPill
import com.glowseed.noctdock.core.ReceiverDisplayWording

internal fun gameHubScreensItemCount(receiverCount: Int): Int = receiverCount.coerceAtLeast(1)

internal fun gameHubScreensMoveDown(index: Int, count: Int): Int {
    if (count <= 0) return 0
    return (index + 1).coerceAtMost(count - 1)
}

internal fun gameHubScreensMoveUp(index: Int, count: Int): Int {
    if (count <= 0) return 0
    return (index - 1).coerceAtLeast(0)
}

internal fun gameHubDeviceAccent(receiver: DiscoveredReceiver?, uiState: SenderUiState): Color = when {
    receiver == null -> NoctColors.Violet
    !receiver.isOnline -> NoctColors.Magenta
    uiState.trustedReceiver?.identity?.id == receiver.identity.id -> NoctColors.Green
    else -> NoctColors.Cyan
}

internal fun gameHubDeviceHeroSubtitle(receiver: DiscoveredReceiver?, uiState: SenderUiState): String = when {
    receiver == null -> "Open NoctDock Receiver on your TV, phone, or tablet."
    !receiver.isOnline -> "Connection interrupted"
    uiState.trustedReceiver?.identity?.id == receiver.identity.id -> "Ready when you are"
    receiver.pairingRequired -> "Ready to pair"
    else -> "Ready nearby"
}

internal fun gameHubReceiverNoun(receiver: DiscoveredReceiver): String = ReceiverDisplayWording.receiverNoun(receiver.formFactor)

@Composable
internal fun GameHubScreensStage(
    uiState: SenderUiState,
    viewModel: SenderViewModel,
    focusedIndex: Int,
    listInputActive: Boolean,
    reducedMotion: Boolean,
    connectedScreenPill: String? = null,
    onSearchAgain: () -> Unit,
) {
    val receivers = uiState.receivers
    val scrollState = rememberScrollState()
    val bringIntoView = remember { BringIntoViewRequester() }
    val accent = LocalNoctAccent.current

    LaunchedEffect(focusedIndex, listInputActive, receivers.size) {
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
        if (connectedScreenPill != null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                GameHubReceiverPill(text = connectedScreenPill, accent = accent)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Available screens",
                    color = NoctColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
                NoctStatusPill("${receivers.size}", NoctColors.Cyan)
            }
            NoctSecondaryButton(text = "Search again", onClick = onSearchAgain, minHeight = 40.dp)
        }
        if (receivers.isEmpty()) {
            GameHubScreensEmptyCard(
                highlighted = listInputActive && focusedIndex == 0,
                reducedMotion = reducedMotion,
                onSearchAgain = onSearchAgain,
                modifier =
                if (listInputActive && focusedIndex == 0) {
                    Modifier.bringIntoViewRequester(bringIntoView)
                } else {
                    Modifier
                },
            )
        } else {
            receivers.forEachIndexed { index, receiver ->
                GameHubReceiverCard(
                    receiver = receiver,
                    uiState = uiState,
                    highlighted = listInputActive && focusedIndex == index,
                    reducedMotion = reducedMotion,
                    onSelect = { viewModel.select(receiver) },
                    onConnect = { viewModel.connect(receiver) },
                    modifier =
                    if (listInputActive && focusedIndex == index) {
                        Modifier.bringIntoViewRequester(bringIntoView)
                    } else {
                        Modifier
                    },
                )
            }
        }
        GameHubScreensManualConnection(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun GameHubScreensEmptyCard(highlighted: Boolean, reducedMotion: Boolean, onSearchAgain: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val accent = NoctColors.Violet
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
                cornerRadius = 18.dp,
            )
            .clickable(onClick = onSearchAgain),
    ) {
        NoctGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                NoctOrb(modifier = Modifier.size(36.dp), color = accent, reducedMotion = reducedMotion)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Looking for a screen",
                        color = NoctColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Open NoctDock Receiver on the same Wi-Fi.",
                        color = NoctColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHubReceiverCard(
    receiver: DiscoveredReceiver,
    uiState: SenderUiState,
    highlighted: Boolean,
    reducedMotion: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trusted = uiState.trustedReceiver?.identity?.id == receiver.identity.id
    val selected =
        uiState.selectedReceiver?.identity?.id == receiver.identity.id ||
            uiState.defaultReceiver?.identity?.id == receiver.identity.id
    val accent = gameHubDeviceAccent(receiver, uiState)
    val shape = RoundedCornerShape(18.dp)
    val noun = gameHubReceiverNoun(receiver)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(shape)
            .gameHubFocusRing(
                shape = shape,
                accent = accent,
                focused = highlighted,
                strokeDp = if (highlighted) {
                    3.5f
                } else if (selected) {
                    2f
                } else {
                    1f
                },
                reducedMotion = reducedMotion,
                cornerRadius = 18.dp,
            ),
    ) {
        NoctGlassCard(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameHubTvOrbBadge(accent = accent, modifier = Modifier.size(36.dp), iconSize = 16.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            receiver.displayName,
                            color = NoctColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            gameHubDeviceHeroSubtitle(receiver, uiState),
                            color = NoctColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NoctSecondaryButton(
                        text = if (selected) "Selected" else "Choose",
                        onClick = onSelect,
                        modifier = Modifier.weight(1f),
                        minHeight = 38.dp,
                    )
                    NoctPrimaryButton(
                        text = if (trusted) "Use $noun" else "Pair",
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        minHeight = 38.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHubTvOrbBadge(accent: Color, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        NoctOrb(modifier = Modifier.fillMaxSize(), color = accent, reducedMotion = true)
        Canvas(modifier = Modifier.size(iconSize)) {
            val iconColor = NoctColors.TextPrimary.copy(alpha = 0.96f)
            val strokeWidth = 2.2.dp.toPx()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            drawRoundRect(
                color = iconColor,
                topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
                size = Size(size.width * 0.76f, size.height * 0.50f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = stroke,
            )
            drawLine(
                iconColor,
                Offset(size.width * 0.50f, size.height * 0.68f),
                Offset(size.width * 0.50f, size.height * 0.84f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                iconColor,
                Offset(size.width * 0.30f, size.height * 0.86f),
                Offset(size.width * 0.70f, size.height * 0.86f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun GameHubScreensManualConnection(uiState: SenderUiState, viewModel: SenderViewModel) {
    val expanded = uiState.manualExpanded
    val shape = RoundedCornerShape(18.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(260),
        label = "manual-chevron",
    )

    Box(modifier = Modifier.fillMaxWidth().clip(shape)) {
        NoctGlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setManualExpanded(!expanded) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Advanced & Experimental",
                            color = NoctColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Manual IP connection for testing",
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
                        OutlinedTextField(
                            value = uiState.manualHost,
                            onValueChange = viewModel::updateManualHost,
                            label = { Text("Local host or IP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = uiState.manualPort,
                            onValueChange = viewModel::updateManualPort,
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        NoctPrimaryButton("Connect manually", viewModel::connectManual, minHeight = 42.dp)
                    }
                }
            }
        }
    }
}
