package dev.brgr.outspoke.ui.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.brgr.outspoke.inference.EngineState
import dev.brgr.outspoke.ui.keyboard.components.TalkButton
import dev.brgr.outspoke.ui.theme.MyIcons

private val DOT_SIZE = 28.dp
private val PILL_WIDTH = 120.dp
private val PILL_HEIGHT = 44.dp
private val PILL_CORNER = 22.dp
private val ICON_SIZE = 36.dp
private val ICON_TINT_SIZE = 18.dp

@Composable
fun OverlayContent(
    viewModel: OverlayViewModel,
    onInjectText: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.overlayState.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val haptic = LocalHapticFeedback.current

    val isPill = state == OverlayState.Pill

    val dpSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val floatSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val width by animateDpAsState(
        targetValue = if (isPill) PILL_WIDTH else DOT_SIZE,
        animationSpec = dpSpec,
        label = "w",
    )
    val height by animateDpAsState(
        targetValue = if (isPill) PILL_HEIGHT else DOT_SIZE,
        animationSpec = dpSpec,
        label = "h",
    )
    val cornerRadius by animateFloatAsState(
        targetValue = if (isPill) PILL_CORNER.value else DOT_SIZE.value / 2,
        animationSpec = floatSpec,
        label = "cr",
    )

    Surface(
        shape = RoundedCornerShape(cornerRadius.dp),
        color = if (isPill) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = if (isPill) 4.dp else 0.dp,
        modifier = Modifier.size(width, height),
    ) {
        if (isPill) {
            PillContent(
                engineState = engineState,
                isRecording = isRecording,
                transcript = transcript,
                onUninitialise = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onUninitialise()
                },
                onRecordStart = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onRecordStart()
                },
                onRecordStop = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onRecordStop(onInjectText)
                },
                onClose = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClose()
                },
            )
        } else {
            DotContent(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onDotClicked()
            })
        }
    }
}

@Composable
private fun DotContent(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun PillContent(
    engineState: EngineState,
    isRecording: Boolean,
    transcript: String,
    onUninitialise: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = onUninitialise,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.size(ICON_SIZE),
        ) {
            Icon(
                MyIcons.Close,
                contentDescription = "Back to dot",
                modifier = Modifier.size(ICON_TINT_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (engineState is EngineState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (engineState is EngineState.Ready) {
            TalkButton(
                isListening = isRecording,
                isContinuous = false,
                onRecordStart = onRecordStart,
                onRecordStop = onRecordStop,
                onContinuousModeEnabled = {},
                modifier = Modifier.size(ICON_SIZE),
                triggerMode = "HOLD",
            )
        }

        if (transcript.isNotEmpty()) {
            Text(
                text = transcript,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 100.dp)
                    .padding(horizontal = 2.dp),
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(ICON_SIZE),
        ) {
            Icon(
                MyIcons.ArrowBack,
                contentDescription = "Hide",
                modifier = Modifier.size(ICON_TINT_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
