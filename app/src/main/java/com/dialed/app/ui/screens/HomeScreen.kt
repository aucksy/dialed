package com.dialed.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dialed.app.R
import com.dialed.app.catalog.Face
import com.dialed.app.catalog.FaceCollection
import com.dialed.app.model.WatchStatus
import com.dialed.app.ui.components.DialStatus
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.components.WatchStatusPill
import com.dialed.app.ui.theme.DialedMotion
import com.dialed.app.ui.theme.DialedRadius
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.dialedColors

/**
 * Collections Home (docs/DESIGN-ADDENDUM-COLLECTIONS.md §3) — Variant A "vitrine hero cards":
 * one collection per card, a large overlapping trio of real faces (centre 132dp, flanks 96dp) over
 * the collection name / style / count. Built from the spec's vitrine (§1e) + paywall trio language
 * at their real sizes. No paywall, no prices, no lock badges — the commercial layer stays parked.
 */
@Composable
fun HomeScreen(
    collections: List<FaceCollection>,
    watchStatus: WatchStatus,
    starterFaces: List<Face>,
    showStarters: Boolean,
    onStarterClick: (Face) -> Unit,
    onCollectionClick: (FaceCollection) -> Unit,
    onSettings: () -> Unit,
) {
    val c = dialedColors

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = DialedSpacing.screenMargin, end = DialedSpacing.screenMargin,
            top = DialedSpacing.md, bottom = DialedSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(DialedSpacing.lg),
    ) {
        item(key = "header") {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(top = DialedSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wordmark()
                    // 22dp gear inside a 48dp target (HANDOFF.md §8); the offset pushes the target's
                    // slack past the screen margin so the glyph still lines up with the 24dp margin.
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
                Spacer(Modifier.height(DialedSpacing.xl))
                Text(
                    "COLLECTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.onSurfaceVariant,
                )
                Spacer(Modifier.height(DialedSpacing.xs))
            }
        }

        // First-face nudge (docs/ONBOARDING-REDESIGN.md §5.2): shown until the first successful
        // install is ever observed, then retired forever. Gives a brand-new user their first move
        // instead of a wall of collection cards.
        if (showStarters && starterFaces.isNotEmpty()) {
            item(key = "starters") {
                StarterCard(faces = starterFaces, onFaceClick = onStarterClick)
            }
        }

        items(collections, key = { it.id }) { collection ->
            CollectionCard(collection = collection, onClick = { onCollectionClick(collection) })
        }
    }
}

/** "Put your first face on" — three tappable starters in the vitrine card language. */
@Composable
private fun StarterCard(faces: List<Face>, onFaceClick: (Face) -> Unit) {
    val c = dialedColors
    val shape = RoundedCornerShape(DialedRadius.lg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(c.surfaceContainerHigh)
            .border(1.dp, c.primary.copy(alpha = 0.35f), shape)
            .padding(start = DialedSpacing.lg, end = DialedSpacing.lg, top = DialedSpacing.xl, bottom = DialedSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Put your first face on",
            style = MaterialTheme.typography.headlineSmall,
            color = c.onSurface,
        )
        Spacer(Modifier.height(DialedSpacing.xs))
        Text(
            "THREE TO START WITH",
            style = MaterialTheme.typography.labelMedium,
            color = c.primary,
        )
        Spacer(Modifier.height(DialedSpacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(DialedSpacing.lg)) {
            faces.take(3).forEach { face ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(DialedRadius.md))
                        .clickable { onFaceClick(face) }
                        .padding(DialedSpacing.xs),
                ) {
                    FaceDial(face, 88.dp, status = DialStatus.NONE)
                    Spacer(Modifier.height(DialedSpacing.xs))
                    Text(
                        face.faceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(collection: FaceCollection, onClick: () -> Unit) {
    val c = dialedColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // F5 micro: press scale .96 (springFast).
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = DialedMotion.springFast(),
        label = "cardPress",
    )
    val shape = RoundedCornerShape(DialedRadius.lg)

    Column(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .shadow(6.dp, shape, clip = false)      // lvl1 card
            .clip(shape)
            .background(c.surfaceContainerHigh)
            .border(1.dp, c.outlineVariant, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = DialedSpacing.lg, end = DialedSpacing.lg, top = DialedSpacing.xl, bottom = DialedSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverTrio(collection)
        Spacer(Modifier.height(DialedSpacing.lg))
        Text(
            collection.title,
            style = MaterialTheme.typography.headlineSmall,
            color = c.onSurface,
        )
        if (collection.subtitle.isNotEmpty()) {
            Spacer(Modifier.height(DialedSpacing.xs))
            Text(
                collection.subtitle.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = c.primary,               // gold accent
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(DialedSpacing.sm))
        val n = collection.faces.size
        Text(
            if (n == 1) "1 face" else "$n faces",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
        )
    }
}

/**
 * The vitrine/paywall cover language at its real size: the collection's first three faces as
 * overlapping circular previews — centre 132dp on top, flanks 96dp behind. Clean covers, no badges.
 * Gracefully handles collections with 1–2 faces (Aether, Arclight) — shows what exists.
 */
@Composable
private fun CoverTrio(collection: FaceCollection) {
    val cover = collection.cover
    when (cover.size) {
        0 -> Unit
        1 -> FaceDial(face = cover[0], size = 150.dp, status = DialStatus.NONE)
        2 -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-16).dp),
        ) {
            FaceDial(cover[0], 120.dp, Modifier.zIndex(2f), status = DialStatus.NONE)
            FaceDial(cover[1], 108.dp, Modifier.zIndex(1f), status = DialStatus.NONE)
        }
        else -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-18).dp),
        ) {
            FaceDial(cover[0], 96.dp, Modifier.zIndex(1f), status = DialStatus.NONE)
            FaceDial(cover[1], 132.dp, Modifier.zIndex(2f), status = DialStatus.NONE)
            FaceDial(cover[2], 96.dp, Modifier.zIndex(1f), status = DialStatus.NONE)
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
