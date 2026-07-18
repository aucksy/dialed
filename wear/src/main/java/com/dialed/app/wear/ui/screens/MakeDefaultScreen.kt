package com.dialed.app.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.ui.components.DialMark
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.theme.DialedWearColors

/**
 * The one-time "Make Dialed your watch face" onboarding step (the functional half of the first-apply
 * fix). Shown once, after permissions, on a fresh watch with no Dialed face yet. Confirming installs
 * the bundled Dialed default face and spends the one-shot set-active IN CONTEXT, so Dialed owns the
 * active slot — after which every pushed face just appears, with no long-press ever again.
 *
 * Big brand mark, one primary action. Built on a scrollable ScreenScaffold (not the centred hero) so
 * the mark + two lines + skip can never clip on a small round screen. "Not now" is a text action in
 * the body (the single EdgeButton is reserved for the primary "Make it my face").
 */
@Composable
fun MakeDefaultScreen(
    onMakeDefault: () -> Unit,
    onSkip: () -> Unit,
) {
    val scrollState = rememberScrollState()
    ScreenScaffold(
        scrollInfoProvider = ScrollInfoProvider(scrollState),
        edgeButton = { DialedEdgeButton("Make it my face", onMakeDefault, filled = true) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(6.dp))
            DialMark(size = 96.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Make Dialed your watch face",
                style = MaterialTheme.typography.titleMedium,
                color = DialedWearColors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Set it up once, and every face you push just appears — no long-press.",
                style = MaterialTheme.typography.bodyMedium,
                color = DialedWearColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(14.dp))
            // Secondary action: a quiet text button (the EdgeButton owns the primary action).
            Text(
                "Not now",
                style = MaterialTheme.typography.labelLarge,
                color = DialedWearColors.disabled,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
