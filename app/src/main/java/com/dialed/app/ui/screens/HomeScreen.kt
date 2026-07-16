package com.dialed.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialed.app.R
import com.dialed.app.catalog.Face
import com.dialed.app.model.WatchStatus
import com.dialed.app.ui.components.DialStatus
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.components.FilterChip
import com.dialed.app.ui.components.UninstallButton
import com.dialed.app.ui.components.UnlockBanner
import com.dialed.app.ui.components.WatchStatusPill
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.FaceSize
import com.dialed.app.ui.theme.dialedColors

@Composable
fun HomeScreen(
    faces: List<Face>,
    entitled: Boolean,
    watchStatus: WatchStatus,
    installedFaceIds: Set<String>,
    activeFaceId: String?,
    uninstallingFaceId: String?,
    onFaceClick: (Face) -> Unit,
    onUninstall: (Face) -> Unit,
    onUnlock: () -> Unit,
    onSettings: () -> Unit,
    price: String = "$11.99",
) {
    val c = dialedColors
    val filters = remember(faces) { listOf("All") + faces.flatMap { it.styleTags }.distinct() }
    var selected by remember { mutableStateOf("All") }
    // Hoisted out of the grid's header item: state remembered inside a lazy item is disposed when
    // that item scrolls off, which would silently rewind the filter row to "All" on the way back.
    val filterScroll = rememberScrollState()
    val shown = remember(selected, faces) {
        if (selected == "All") faces else faces.filter { selected in it.styleTags }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = DialedSpacing.screenMargin, end = DialedSpacing.screenMargin,
            top = DialedSpacing.md, bottom = DialedSpacing.xxl,
        ),
        horizontalArrangement = Arrangement.spacedBy(DialedSpacing.gridGutter),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(top = DialedSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wordmark()
                    // 22dp gear inside a 48dp target (HANDOFF.md §8) — and a gear, not the filter
                    // glyph this used to borrow. The offset pushes the oversized target's slack past
                    // the screen margin so the GLYPH still lines up with the 24dp margin (and with
                    // the grid below it); without it, centring 22dp in 48dp visibly indents the gear.
                    Box(
                        modifier = Modifier
                            .offset(x = 13.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_settings), contentDescription = "Settings",
                            tint = c.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(DialedSpacing.md))
                WatchStatusPill(watchStatus)
                Spacer(Modifier.height(DialedSpacing.lg))
                // Must scroll: a plain Row neither wraps nor scrolls, so any chip past the screen
                // width is clipped and permanently unreachable. v0.21.0 took the series count 5 -> 10
                // (filters 6 -> 11, ~900dp of chips against ~312dp of usable width), which would have
                // hidden half the store behind an unusable filter. Phase 2D replaces this with
                // collection cards; until then, scrolling keeps every series reachable.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DialedSpacing.sm),
                    modifier = Modifier.horizontalScroll(filterScroll),
                ) {
                    filters.forEach { f ->
                        FilterChip(text = f, selected = f == selected, onClick = { selected = f })
                    }
                }
                if (!entitled) {
                    Spacer(Modifier.height(DialedSpacing.lg))
                    UnlockBanner(faceCount = faces.size, price = price, onUnlock = onUnlock)
                }
                Spacer(Modifier.height(DialedSpacing.sm))
            }
        }

        items(shown, key = { it.id }) { face ->
            val installed = face.id in installedFaceIds
            val active = face.id == activeFaceId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onFaceClick(face) },
            ) {
                FaceDial(
                    face = face,
                    size = FaceSize.grid,
                    locked = !entitled,
                    status = when {
                        active -> DialStatus.ACTIVE
                        installed -> DialStatus.INSTALLED
                        else -> DialStatus.NONE
                    },
                )
                Spacer(Modifier.height(DialedSpacing.sm))
                Text(face.displayName, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
                Text(
                    face.tag, style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                if (installed) {
                    Spacer(Modifier.height(DialedSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (active) "Active" else "On watch",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) c.success else c.onSurfaceVariant,
                        )
                        UninstallButton(
                            onClick = { onUninstall(face) },
                            loading = uninstallingFaceId == face.id,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Wordmark() {
    val c = dialedColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Dialed",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = c.onSurface,
        )
        Text(
            ".",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = c.primary,
        )
    }
}
