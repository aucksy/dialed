package com.dialed.app.wear.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.ui.components.DialMark
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.DialedScreen
import com.dialed.app.wear.ui.theme.DialedWearColors

/** 1n — Wear OS < 6 gate. Graceful, no version numbers of blame. */
@Composable
fun UnsupportedScreen(onOk: () -> Unit) {
    DialedScreen(
        edgeButton = { DialedEdgeButton("OK", onOk, filled = false) },
    ) {
        DialMark(size = 46.dp, modifier = Modifier.alpha(0.55f))
        Spacer(Modifier.height(14.dp))
        Text(
            "Dialed needs a newer Wear OS",
            style = MaterialTheme.typography.titleMedium,
            color = DialedWearColors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Installing faces here needs Wear OS 6. Your watch stays just as it is.",
            style = MaterialTheme.typography.bodyMedium,
            color = DialedWearColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}
