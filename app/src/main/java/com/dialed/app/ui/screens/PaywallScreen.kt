package com.dialed.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dialed.app.R
import com.dialed.app.catalog.Face
import com.dialed.app.ui.components.DialedButton
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.dialedColors

@Composable
fun PaywallScreen(
    faces: List<Face>,
    faceCount: Int,
    onClose: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    price: String = "$11.99",
) {
    val c = dialedColors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(
                painterResource(R.drawable.ic_close), "Close", tint = c.onSurface,
                modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape)
                    .clickable(onClick = onClose).padding(11.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val trio = faces.take(3)
            if (trio.size == 3) {
                FaceDial(trio[0], 92.dp, Modifier.offset(x = 16.dp))
                Box(Modifier.zIndex(2f)) { FaceDial(trio[1], 116.dp) }
                FaceDial(trio[2], 92.dp, Modifier.offset(x = (-16).dp))
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "The whole collection.\nOnce.",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = c.onSurface, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ValueBullet("All $faceCount faces — plus every face we add later")
            ValueBullet("No subscription. No unlocks. No ads.")
            ValueBullet("Works on every watch signed into your account")
        }

        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(price, style = MaterialTheme.typography.displayLarge, color = c.onSurface)
            Text(
                "one-time · yours forever",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        DialedButton("Unlock everything", onPurchase, Modifier.fillMaxWidth())
        Text(
            "Restore purchase",
            style = MaterialTheme.typography.labelLarge, color = c.primary,
            modifier = Modifier.padding(top = 16.dp).clickable(onClick = onRestore),
        )
        Text(
            "Billed once via Google Play",
            style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 30.dp),
        )
    }
}

@Composable
private fun ValueBullet(text: String) {
    val c = dialedColors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            painterResource(R.drawable.ic_check), null, tint = c.primary,
            modifier = Modifier.size(22.dp).clip(CircleShape)
                .background(c.ctaContainer.copy(alpha = 0.15f)).padding(5.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
    }
}
