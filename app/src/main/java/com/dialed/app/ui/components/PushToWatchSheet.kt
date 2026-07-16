package com.dialed.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialed.app.R
import com.dialed.app.catalog.Face
import com.dialed.app.transport.PushStatus
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.FaceSize
import com.dialed.app.ui.theme.dialedColors

/**
 * The money moment (HANDOFF.md F3): the face flies from the phone to the watch. Driven by
 * [PushStatus]. Never vetoes swipe-dismiss (that deadlocks a ModalBottomSheet) — dismiss is
 * always allowed; a mid-flight transfer simply completes on the watch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushToWatchSheet(
    face: Face,
    status: PushStatus,
    deviceName: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = dialedColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // No auto-dismiss: the success state is shown until the user taps Done or swipes the sheet away.
    // (Previously the "already active" repeat-push path self-closed in ~1.6s, so the user never saw
    // any confirmation — the reported "no success on either side" symptom.) Swipe-dismiss stays free.

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surfaceContainer,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp)) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.onSurface.copy(alpha = 0.2f)))
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 40.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (status) {
                is PushStatus.NoWatch -> NoWatchContent(face)
                is PushStatus.Unsupported -> UnsupportedContent(deviceName, onDismiss)
                is PushStatus.Error -> ErrorContent(face, status.message, onRetry, onDismiss)
                is PushStatus.Done -> DoneContent(face, needsActivation = status.needsActivation, onDone = onDismiss)
                else -> SendingContent(face, deviceName)
            }
        }
    }
}

@Composable
private fun SendingContent(face: Face, deviceName: String?) {
    val c = dialedColors
    FaceDial(face = face, size = FaceSize.sheet)
    Spacer(Modifier.height(DialedSpacing.lg))
    Text(
        deviceName?.let { "Sending to $it…" } ?: "Sending to your watch…",
        style = MaterialTheme.typography.titleMedium,
        color = c.onPrimaryContainer,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Keep it close",
        style = MaterialTheme.typography.bodyMedium,
        color = c.onSurfaceVariant,
    )
    Spacer(Modifier.height(DialedSpacing.xl))
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
        color = c.primary,
        trackColor = c.outlineVariant,
    )
}

@Composable
private fun DoneContent(face: Face, needsActivation: Boolean, onDone: () -> Unit) {
    val c = dialedColors
    val haptic = LocalHapticFeedback.current
    // F3 beat 3 landing haptic — fires once when the success state appears.
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
    Box(
        Modifier.size(84.dp).clip(CircleShape).background(c.success.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check), null, tint = c.success, modifier = Modifier.size(40.dp))
    }
    Spacer(Modifier.height(DialedSpacing.lg))
    Text(
        if (needsActivation) "On your watch" else "On your wrist.",
        style = MaterialTheme.typography.headlineSmall,
        color = c.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        if (needsActivation) {
            "${face.displayName} is installed. Finish setting it on your watch."
        } else {
            "${face.displayName} is now your watch face."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = c.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    // Persistent confirmation for BOTH branches — the sheet no longer auto-dismisses, so a real
    // success is never missed. Tapping Done (or swiping) closes it.
    Spacer(Modifier.height(DialedSpacing.xl))
    DialedButton("Done", onDone, variant = DialedButtonVariant.TONAL, height = 48.dp)
}

@Composable
private fun ErrorContent(face: Face, message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val c = dialedColors
    FaceDial(face = face, size = FaceSize.sheet, modifier = Modifier)
    Spacer(Modifier.height(DialedSpacing.lg))
    Text("Interrupted", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(message, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(DialedSpacing.xl))
    DialedButton("Retry", onRetry, height = 52.dp)
    Spacer(Modifier.height(DialedSpacing.sm))
    DialedButton("Not now", onDismiss, variant = DialedButtonVariant.TEXT)
}

/**
 * The watch answered that it has no Watch Face Push (Wear OS < 6). There is no retry — the honest
 * move is to say the watch can't do this, not to offer a button that will fail identically.
 */
@Composable
private fun UnsupportedContent(deviceName: String?, onDismiss: () -> Unit) {
    val c = dialedColors
    Box(
        Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(c.error.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_watch), null, tint = c.error, modifier = Modifier.size(38.dp))
    }
    Spacer(Modifier.height(DialedSpacing.lg))
    Text("This watch can't install faces", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(
        "${deviceName ?: "Your watch"} needs Wear OS 6 to receive faces from Dialed. Everything else on it stays exactly as it is.",
        style = MaterialTheme.typography.bodyMedium,
        color = c.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(DialedSpacing.xl))
    DialedButton("Got it", onDismiss, variant = DialedButtonVariant.TONAL, height = 48.dp)
}

@Composable
private fun NoWatchContent(face: Face) {
    val c = dialedColors
    Box(
        Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_watch), null, tint = c.onSurfaceVariant, modifier = Modifier.size(38.dp))
    }
    Spacer(Modifier.height(DialedSpacing.lg))
    Text("No watch connected", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(
        "Open Dialed on your Wear OS 6 watch, then push ${face.displayName} again.",
        style = MaterialTheme.typography.bodyMedium,
        color = c.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
