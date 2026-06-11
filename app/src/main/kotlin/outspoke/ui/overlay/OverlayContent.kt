package dev.brgr.outspoke.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    val feedback by viewModel.feedback.collectAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collect { event ->
            when (event) {
                HapticEvent.Start -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                HapticEvent.Success -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HapticEvent.Error -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                HapticEvent.Empty -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

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
                feedback = feedback,
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
                onConsumeFeedback = { viewModel.consumeFeedback() },
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

/**
 * MD3 Expressive recording indicator.
 *
 * A spring-animated pulsing ring around a mic icon that signals the microphone is live.
 * Tapping stops recording and collapses back to the dot.
 */
@Composable
private fun RecordingIndicator(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    // Spring-like pulse — M3 Expressive organic feel via FastOutSlowIn easing
    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseFraction",
    )
    // Inner dot scale — breathes with the pulse
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ICON_SIZE)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = size.minDimension / 2f
            val innerRadius = outerRadius * 0.42f

            // Outer pulsing ring — tonal primaryContainer, thick stroke
            val ringRadius = innerRadius + (outerRadius - innerRadius) * pulse
            val ringAlpha = 0.3f + pulse * 0.45f
            drawCircle(
                color = primaryContainer.copy(alpha = ringAlpha),
                radius = ringRadius,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )

            // Inner filled dot — primary, scales with spring
            drawCircle(
                color = primary,
                radius = innerRadius * dotScale,
            )
        }
    }
}

/**
 * Brief expressive flash shown after recording ends.
 *
 * [OverlayFeedback.Error]   → red ring + expressive X cross.
 * [OverlayFeedback.Success] → primary ring + checkmark.
 * [OverlayFeedback.Empty]   → tertiary ring + mic-off.
 *
 * Auto-dismisses after [durationMs] or on tap.
 */
@Composable
private fun FeedbackIndicator(
    feedback: OverlayFeedback,
    onClick: () -> Unit,
    onAnimEnd: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 800,
) {
    val error = MaterialTheme.colorScheme.error
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val onError = MaterialTheme.colorScheme.onError
    val tertiary = MaterialTheme.colorScheme.tertiary
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiary = MaterialTheme.colorScheme.onTertiary

    val dotColor: androidx.compose.ui.graphics.Color
    val iconColor: androidx.compose.ui.graphics.Color
    val containerColor: androidx.compose.ui.graphics.Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    when (feedback) {
        is OverlayFeedback.Error -> {
            dotColor = error
            iconColor = onError
            containerColor = errorContainer
            icon = MyIcons.Close
        }
        is OverlayFeedback.Success -> {
            dotColor = Color(0xFF46A35E)
            iconColor = Color.White
            containerColor = Color(0xFFD4F5DC)
            icon = MyIcons.CheckCircle
        }
        is OverlayFeedback.Empty -> {
            dotColor = tertiary
            iconColor = onTertiary
            containerColor = tertiaryContainer
            icon = MyIcons.MicOff
        }
    }

    // Spring-like pulse — bouncy M3 Expressive feel
    val infiniteTransition = rememberInfiniteTransition(label = "feedbackPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "feedbackPulseFraction",
    )
    // Icon scale — breathes with the pulse
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "feedbackIconScale",
    )

    // Auto-dismiss after durationMs
    LaunchedEffect(feedback) {
        delay(durationMs)
        onAnimEnd()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ICON_SIZE)
            .clip(CircleShape)
            .clickable {
                onAnimEnd()
                onClick()
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = size.minDimension / 2f
            val innerRadius = outerRadius * 0.42f

            // Outer ring — tonal container colour, spring-pulsing
            val ringRadius = innerRadius + (outerRadius - innerRadius) * pulse
            val ringAlpha = 0.35f + pulse * 0.5f
            drawCircle(
                color = containerColor.copy(alpha = ringAlpha),
                radius = ringRadius,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )

            // Inner filled dot — scales with spring
            drawCircle(
                color = dotColor,
                radius = innerRadius * iconScale,
            )
        }

        // Expressive icon — scales with spring
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .size(16.dp)
                .scale(iconScale),
        )
    }
}

@Composable
private fun PillContent(
    engineState: EngineState,
    isRecording: Boolean,
    transcript: String,
    feedback: OverlayFeedback?,
    onUninitialise: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onConsumeFeedback: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isRecording) {
            RecordingIndicator(onClick = onUninitialise)
        } else if (feedback != null) {
            FeedbackIndicator(
                feedback = feedback,
                onClick = onUninitialise,
                onAnimEnd = onConsumeFeedback,
            )
        } else {
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
