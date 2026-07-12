package com.dialed.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dialed.app.R
import com.dialed.app.ui.theme.dialedColors

/** InstallButton state machine (HANDOFF.md §6 / spec 1g). */
sealed interface InstallState {
    data object Locked : InstallState
    data object Ready : InstallState

    /** Slot = 1: another Dialed face occupies the watch, so installing this one replaces it. */
    data object Replace : InstallState
    data class Installing(val progress: Float? = null) : InstallState
    data object InstalledActive : InstallState
    data class Error(val message: String) : InstallState
}

@Composable
fun InstallButton(
    state: InstallState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = dialedColors
    val clickable = state is InstallState.Locked || state is InstallState.Ready ||
        state is InstallState.Replace || state is InstallState.Error

    val bg: Color
    val content: Color
    var borderColor: Color? = null
    when (state) {
        is InstallState.Locked, is InstallState.Ready, is InstallState.Replace -> { bg = c.ctaContainer; content = c.onCta }
        is InstallState.Installing -> {
            bg = c.primaryContainer; content = c.onPrimaryContainer
            borderColor = c.ctaContainer.copy(alpha = 0.5f)
        }
        is InstallState.InstalledActive -> {
            bg = c.success.copy(alpha = 0.12f); content = c.success
            borderColor = c.success.copy(alpha = 0.35f)
        }
        is InstallState.Error -> {
            bg = c.error.copy(alpha = 0.10f); content = c.error
            borderColor = c.error.copy(alpha = 0.35f)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(bg)
            .then(borderColor?.let { Modifier.border(1.dp, it, CircleShape) } ?: Modifier)
            .clickable(enabled = clickable, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is InstallState.Locked ->
                Icon(painterResource(R.drawable.ic_lock), null, tint = content, modifier = Modifier.size(18.dp))
            is InstallState.Ready, is InstallState.Replace ->
                Icon(painterResource(R.drawable.ic_watch), null, tint = content, modifier = Modifier.size(19.dp))
            is InstallState.Installing ->
                CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            is InstallState.InstalledActive ->
                Icon(painterResource(R.drawable.ic_check), null, tint = content, modifier = Modifier.size(18.dp))
            is InstallState.Error -> Unit
        }
        Text(
            text = when (state) {
                is InstallState.Locked -> "Unlock the collection"
                is InstallState.Ready -> "Install to watch"
                is InstallState.Replace -> "Replace on watch"
                is InstallState.Installing ->
                    state.progress?.let { "Installing… ${(it * 100).toInt()}%" } ?: "Installing…"
                is InstallState.InstalledActive -> "On your watch · Active"
                is InstallState.Error -> state.message
            },
            color = content,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Destructive single-tap uninstall (issue #3). Error-tinted; shows a spinner while [loading].
 * Used on FaceDetail (full-width) and — compact via [compact] — on the Home grid tile.
 */
@Composable
fun UninstallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val c = dialedColors
    Row(
        modifier = modifier
            .then(if (compact) Modifier.size(38.dp) else Modifier.fillMaxWidth().height(56.dp))
            .clip(CircleShape)
            .background(c.error.copy(alpha = 0.10f))
            .border(1.dp, c.error.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = !loading, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = c.error, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(
                painterResource(R.drawable.ic_trash), contentDescription = "Uninstall from watch",
                tint = c.error, modifier = Modifier.size(if (compact) 16.dp else 18.dp),
            )
        }
        if (!compact) {
            Text(
                text = if (loading) "Removing…" else "Remove from watch",
                color = c.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
