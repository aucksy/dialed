package com.dialed.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dialed.app.MainViewModel
import com.dialed.app.ui.components.PushToWatchSheet
import com.dialed.app.ui.screens.FaceDetailScreen
import com.dialed.app.ui.screens.HomeScreen
import com.dialed.app.ui.screens.OnboardingPager
import com.dialed.app.ui.screens.PaywallScreen
import com.dialed.app.ui.screens.SettingsScreen
import com.dialed.app.ui.theme.DialedMotion
import com.dialed.app.ui.theme.dialedColors

/** Lightweight nav (no navigation-compose dep) — a screen state + AnimatedContent. */
sealed interface Screen {
    data object Home : Screen
    data class Detail(val faceId: String) : Screen
    data object Paywall : Screen
    data object Settings : Screen
}

@Composable
fun DialedApp(viewModel: MainViewModel) {
    val onboarded by viewModel.onboarded.collectAsStateWithLifecycle()
    val entitled by viewModel.entitled.collectAsStateWithLifecycle()
    val watchStatus by viewModel.watchStatus.collectAsStateWithLifecycle()
    val c = dialedColors

    Surface(modifier = Modifier.fillMaxSize().background(c.background), color = c.background) {
        if (!onboarded) {
            OnboardingPager(
                faces = viewModel.faces,
                onFinish = viewModel::completeOnboarding,
            )
            return@Surface
        }

        val pushingFace by viewModel.pushingFace.collectAsStateWithLifecycle()
        val pushStatus by viewModel.pushStatus.collectAsStateWithLifecycle()

        var screen: Screen by rememberSaveable(
            stateSaver = ScreenSaver,
        ) { mutableStateOf(Screen.Home) }

        BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn(animationSpec = DialedMotion.springStandard()) togetherWith
                    fadeOut(animationSpec = DialedMotion.springStandard())
            },
            label = "screen",
        ) { target ->
            when (target) {
                is Screen.Home -> HomeScreen(
                    faces = viewModel.faces,
                    entitled = entitled,
                    watchStatus = watchStatus,
                    onFaceClick = { screen = Screen.Detail(it.id) },
                    onUnlock = { screen = Screen.Paywall },
                    onSettings = { screen = Screen.Settings },
                )
                is Screen.Detail -> {
                    val face = viewModel.faces.first { it.id == target.faceId }
                    FaceDetailScreen(
                        face = face,
                        entitled = entitled,
                        watchStatus = watchStatus,
                        onBack = { screen = Screen.Home },
                        onUnlock = { screen = Screen.Paywall },
                        onInstall = { viewModel.startPush(face) },
                    )
                }
                is Screen.Paywall -> PaywallScreen(
                    faces = viewModel.faces,
                    faceCount = viewModel.faces.size,
                    onClose = { screen = Screen.Home },
                    onPurchase = { viewModel.debugToggleEntitlement(); screen = Screen.Home },
                    onRestore = { viewModel.debugToggleEntitlement() },
                )
                is Screen.Settings -> SettingsScreen(
                    watchStatus = watchStatus,
                    entitled = entitled,
                    themeMode = viewModel.themeMode.collectAsStateWithLifecycle().value,
                    onThemeChange = viewModel::setThemeMode,
                    onBack = { screen = Screen.Home },
                    onRestore = { viewModel.debugToggleEntitlement() },
                )
            }
        }

        pushingFace?.let { face ->
            PushToWatchSheet(
                face = face,
                status = pushStatus,
                deviceName = watchStatus.deviceName,
                onRetry = viewModel::retryPush,
                onDismiss = viewModel::dismissPush,
            )
        }
    }
}

private val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, String>(
    save = {
        when (it) {
            is Screen.Home -> "home"
            is Screen.Paywall -> "paywall"
            is Screen.Settings -> "settings"
            is Screen.Detail -> "detail:${it.faceId}"
        }
    },
    restore = {
        when {
            it == "home" -> Screen.Home
            it == "paywall" -> Screen.Paywall
            it == "settings" -> Screen.Settings
            it.startsWith("detail:") -> Screen.Detail(it.removePrefix("detail:"))
            else -> Screen.Home
        }
    },
)
