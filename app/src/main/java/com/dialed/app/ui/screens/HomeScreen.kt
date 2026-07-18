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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.dialed.app.R
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
 * Collections Home (docs/DESIGN-ADDENDUM-COLLECTIONS.md §3): a scrolling list of collection cards.
 * No paywall, no prices, no lock badges — this is the browse spine; the commercial layer is parked
 * on the collection MAP (audit §11). Covers are clean previews; watch-state badges live on the
 * Collection face grid, not here.
 */
@Composable
fun HomeScreen(
    collections: List<FaceCollection>,
    watchStatus: WatchStatus,
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

        items(collections, key = { it.id }) { collection ->
            CollectionCard(collection = collection, onClick = { onCollectionClick(collection) })
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

    Row(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(DialedRadius.lg), clip = false)
            .clip(RoundedCornerShape(DialedRadius.lg))
            .background(c.surfaceContainerHigh)
            .border(1.dp, c.outlineVariant, RoundedCornerShape(DialedRadius.lg))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(DialedSpacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverTrio(collection)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = DialedSpacing.lg),
        ) {
            Text(
                collection.title,
                style = MaterialTheme.typography.titleLarge,
                color = c.onSurface,
            )
            if (collection.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    collection.subtitle.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(DialedSpacing.sm))
            val n = collection.faces.size
            Text(
                if (n == 1) "1 face" else "$n faces",
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = c.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The vitrine cover language (spec §1e): the first three faces as overlapping circular previews,
 * fanned left-to-right with the first face on top. Clean covers — no lock/status badges.
 */
@Composable
private fun CoverTrio(collection: FaceCollection) {
    val cover = collection.cover
    val coverSize = 60.dp
    val peek = 24.dp
    val width = coverSize + peek * (cover.size - 1).coerceAtLeast(0)
    Box(Modifier.width(width).height(coverSize)) {
        // Draw back-to-front so faces[0] lands on top (drawn last), fanned to the right.
        cover.indices.reversed().forEach { i ->
            FaceDial(
                face = cover[i],
                size = coverSize,
                status = DialStatus.NONE,
                modifier = Modifier.offset(x = peek * i),
            )
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
