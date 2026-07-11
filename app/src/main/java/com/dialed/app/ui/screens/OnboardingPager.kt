package com.dialed.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dialed.app.R
import com.dialed.app.catalog.Face
import com.dialed.app.ui.components.DialedButton
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.theme.FaceSize
import com.dialed.app.ui.theme.dialedColors
import kotlinx.coroutines.launch

private data class OnbPage(val title: String, val body: String)

private val pages = listOf(
    OnbPage("A face for every moment.", "A curated collection of watch faces, made for Wear OS. Yours in one purchase."),
    OnbPage("Connect your watch", "Dialed pushes faces straight to your Pixel Watch or Galaxy Watch — one tap, on your wrist."),
    OnbPage("One tap of permission", "Dialed uses the Wear OS connection to install faces. Nothing else leaves your phone."),
)

@Composable
fun OnboardingPager(faces: List<Face>, onFinish: () -> Unit) {
    val c = dialedColors
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            Text(
                "Skip", style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onFinish),
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                when (page) {
                    0 -> if (faces.isNotEmpty()) FaceDial(faces[0], FaceSize.grid)
                    1 -> WatchGlyph { if (faces.size > 1) FaceDial(faces[1], 120.dp) }
                    else -> Box(
                        Modifier.size(120.dp).clip(CircleShape)
                            .background(c.ctaContainer.copy(alpha = 0.08f))
                            .border(1.dp, c.ctaContainer.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_shield), null, tint = c.primary, modifier = Modifier.size(44.dp)) }
                }
            }
        }

        Column(Modifier.padding(bottom = 34.dp)) {
            Text(
                pages[pagerState.currentPage].title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = c.onSurface,
            )
            Text(
                pages[pagerState.currentPage].body,
                style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.padding(vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size) { i ->
                    val on = i == pagerState.currentPage
                    Box(
                        Modifier.height(5.dp).width(if (on) 16.dp else 5.dp).clip(CircleShape)
                            .background(if (on) c.primary else c.outline),
                    )
                }
            }
            val last = pagerState.currentPage == pages.lastIndex
            DialedButton(
                text = if (last) "Get started" else "Continue",
                onClick = {
                    if (last) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                height = 52.dp,
            )
        }
    }
}

@Composable
private fun WatchGlyph(content: @Composable () -> Unit) {
    val c = dialedColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(56.dp).height(30.dp).clip(CircleShape).background(c.surfaceContainerHigh))
        Box(
            Modifier.size(148.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF050506))
                .border(5.dp, c.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
        Box(Modifier.width(56.dp).height(30.dp).clip(CircleShape).background(c.surfaceContainerHigh))
    }
}
