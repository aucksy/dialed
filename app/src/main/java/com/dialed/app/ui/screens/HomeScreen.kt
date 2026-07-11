package com.dialed.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.components.FilterChip
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
    onFaceClick: (Face) -> Unit,
    onUnlock: () -> Unit,
    onSettings: () -> Unit,
    price: String = "$11.99",
) {
    val c = dialedColors
    val filters = remember(faces) { listOf("All") + faces.flatMap { it.styleTags }.distinct() }
    var selected by remember { mutableStateOf("All") }
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
                    Icon(
                        painterResource(R.drawable.ic_filter), contentDescription = "Settings",
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).clickable(onClick = onSettings),
                    )
                }
                Spacer(Modifier.height(DialedSpacing.md))
                WatchStatusPill(watchStatus)
                Spacer(Modifier.height(DialedSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(DialedSpacing.sm)) {
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onFaceClick(face) },
            ) {
                FaceDial(face = face, size = FaceSize.grid, locked = !entitled)
                Spacer(Modifier.height(DialedSpacing.sm))
                Text(face.displayName, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
                Text(
                    face.tag, style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                )
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
