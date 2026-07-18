package com.dialed.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialed.app.R
import com.dialed.app.catalog.Face
import com.dialed.app.catalog.FaceCollection
import com.dialed.app.model.WatchStatus
import com.dialed.app.ui.components.DialStatus
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.components.UninstallButton
import com.dialed.app.ui.components.WatchStatusPill
import com.dialed.app.ui.theme.DialedMotion
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.FaceSize
import com.dialed.app.ui.theme.dialedColors

/**
 * One collection's faces (docs/DESIGN-ADDENDUM-COLLECTIONS.md §4): a header + the existing showroom
 * grid (spec §1d) scoped to this collection. Browse surface — no locks, no paywall; install-status
 * badges DO show (real watch state, not entitlement).
 */
@Composable
fun CollectionScreen(
    collection: FaceCollection,
    watchStatus: WatchStatus,
    installedFaceIds: Set<String>,
    activeFaceId: String?,
    uninstallingFaceId: String?,
    onFaceClick: (Face) -> Unit,
    onUninstall: (Face) -> Unit,
    onBack: () -> Unit,
) {
    val c = dialedColors

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
                    Modifier.fillMaxWidth().padding(top = DialedSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 22dp glyph inside a 48dp target; the -13dp offset pulls the target's slack
                    // back past the screen margin so the glyph lines up with the 24dp margin.
                    Box(
                        modifier = Modifier
                            .offset(x = (-13).dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_back), contentDescription = "Back",
                            tint = c.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.size(DialedSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            collection.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = c.onSurface,
                        )
                        val sub = buildString {
                            if (collection.subtitle.isNotEmpty()) {
                                append(collection.subtitle.uppercase()); append("  ·  ")
                            }
                            val n = collection.faces.size
                            append(if (n == 1) "1 FACE" else "$n FACES")
                        }
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = c.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(DialedSpacing.md))
                WatchStatusPill(watchStatus)
                Spacer(Modifier.height(DialedSpacing.sm))
            }
        }

        items(collection.faces, key = { it.id }) { face ->
            val installed = face.id in installedFaceIds
            val active = face.id == activeFaceId
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            // F5 micro: press scale .96 (springFast).
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.96f else 1f,
                animationSpec = DialedMotion.springFast(),
                label = "facePress",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scale)
                    .clickable(interactionSource = interaction, indication = null) { onFaceClick(face) },
            ) {
                FaceDial(
                    face = face,
                    size = FaceSize.grid,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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

        item(span = { GridItemSpan(maxLineSpan) }) {
            // Expectation-setting copy (assessment §1) — honest, no paywall.
            Text(
                "Your watch holds one Dialed face at a time — installing another replaces it.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DialedSpacing.lg, start = DialedSpacing.sm, end = DialedSpacing.sm),
            )
        }
    }
}
