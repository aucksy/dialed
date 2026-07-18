package com.dialed.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dialed.app.MainViewModel
import com.dialed.app.ui.components.PushToWatchSheet
import com.dialed.app.ui.components.isReducedMotion
import com.dialed.app.ui.screens.CollectionScreen
import com.dialed.app.ui.screens.FaceDetailScreen
import com.dialed.app.ui.screens.HomeScreen
import com.dialed.app.ui.screens.OnboardingPager
import com.dialed.app.ui.screens.PaywallScreen
import com.dialed.app.ui.screens.SettingsScreen
import com.dialed.app.ui.theme.DialedMotion
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.dialedColors

/** Lightweight nav (no navigation-compose dep) — a screen state + AnimatedContent. */
sealed interface Screen {
    data object Home : Screen
    data class Collection(val collectionId: String) : Screen
    /** [fromCollectionId] is the collection the face was opened from, so Back returns there. */
    data class Detail(val faceId: String, val fromCollectionId: String? = null) : Screen
    data object Paywall : Screen
    data object Settings : Screen
}

/** The screen Back (system + on-screen) returns to. Home is the root. */
private fun Screen.parent(): Screen = when (this) {
    is Screen.Home -> Screen.Home
    is Screen.Collection -> Screen.Home
    is Screen.Detail -> fromCollectionId?.let { Screen.Collection(it) } ?: Screen.Home
    is Screen.Paywall -> Screen.Home
    is Screen.Settings -> Screen.Home
}

/** Navigation depth — drives the F2-flavoured expand transition (deeper = scale up into view). */
private fun Screen.depth(): Int = when (this) {
    is Screen.Home -> 0
    is Screen.Collection -> 1
    is Screen.Settings -> 1
    is Screen.Detail -> 2
    is Screen.Paywall -> 2
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
        val installedFaceIds by viewModel.installedFaceIds.collectAsStateWithLifecycle()
        val activeFaceId by viewModel.activeFaceId.collectAsStateWithLifecycle()
        val uninstallingFaceId by viewModel.uninstallingFaceId.collectAsStateWithLifecycle()
        val uninstallError by viewModel.uninstallError.collectAsStateWithLifecycle()

        // Re-read the watch's installed/active snapshot whenever the app returns to the foreground —
        // the user may have switched or removed the face on the watch while we were backgrounded.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshInstalledState() }

        var screen: Screen by rememberSaveable(
            stateSaver = ScreenSaver,
        ) { mutableStateOf(Screen.Home) }
        val reduced = isReducedMotion()

        BackHandler(enabled = screen !is Screen.Home) { screen = screen.parent() }

        // A failed uninstall must be visible: the button used to just stop spinning.
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(uninstallError) {
            uninstallError?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.clearUninstallError()
            }
        }

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (reduced) {
                        // Reduced motion (HANDOFF.md §5): shared-element → a plain 200ms crossfade.
                        fadeIn(tween(DialedMotion.DUR_STD)) togetherWith fadeOut(tween(DialedMotion.DUR_FAST))
                    } else {
                        // F2-flavoured expand: going deeper (Home→Collection→Detail) scales up into
                        // view; going back settles down. Circular clip stays intact per screen.
                        val forward = targetState.depth() >= initialState.depth()
                        val enter = fadeIn(DialedMotion.springStandard()) +
                            scaleIn(DialedMotion.springExpressive(), initialScale = if (forward) 0.90f else 1.06f)
                        val exit = fadeOut(tween(DialedMotion.DUR_FAST)) +
                            scaleOut(DialedMotion.springExpressive(), targetScale = if (forward) 1.06f else 0.94f)
                        enter togetherWith exit
                    }
                },
                label = "screen",
            ) { target ->
                when (target) {
                    is Screen.Home -> HomeScreen(
                        collections = viewModel.collections,
                        watchStatus = watchStatus,
                        onCollectionClick = { screen = Screen.Collection(it.id) },
                        onSettings = { screen = Screen.Settings },
                    )
                    is Screen.Collection -> {
                        // A restored collection id can outlive the collection it names (catalog
                        // changed across an update) — fall back Home rather than crash (M1 class).
                        val collection = viewModel.collections.firstOrNull { it.id == target.collectionId }
                        if (collection == null) {
                            LaunchedEffect(target.collectionId) { screen = Screen.Home }
                        } else {
                            CollectionScreen(
                                collection = collection,
                                watchStatus = watchStatus,
                                installedFaceIds = installedFaceIds,
                                activeFaceId = activeFaceId,
                                uninstallingFaceId = uninstallingFaceId,
                                onFaceClick = { screen = Screen.Detail(it.id, fromCollectionId = collection.id) },
                                onUninstall = viewModel::uninstallFace,
                                onBack = { screen = Screen.Home },
                            )
                        }
                    }
                    is Screen.Detail -> {
                        // firstOrNull, not first: a restored "detail:<id>" can outlive the face it
                        // names (catalog changed across an update), and that must not crash the app.
                        val face = viewModel.faces.firstOrNull { it.id == target.faceId }
                        if (face == null) {
                            LaunchedEffect(target.faceId) { screen = Screen.Home }
                        } else {
                            FaceDetailScreen(
                                face = face,
                                entitled = entitled,
                                watchStatus = watchStatus,
                                isInstalled = face.id in installedFaceIds,
                                isActive = face.id == activeFaceId,
                                slotOccupied = installedFaceIds.isNotEmpty() && face.id !in installedFaceIds,
                                uninstalling = uninstallingFaceId == face.id,
                                onBack = { screen = target.parent() },
                                onUnlock = { screen = Screen.Paywall },
                                onInstall = { viewModel.startPush(face) },
                                onUninstall = { viewModel.uninstallFace(face) },
                            )
                        }
                    }
                    is Screen.Paywall -> PaywallScreen(
                        faces = viewModel.faces,
                        faceCount = viewModel.faces.size,
                        onClose = { screen = Screen.Home },
                        // Debug-only stand-ins until real Play Billing lands (Phase 3); both are
                        // hard no-ops in release, so a release build can never hand out a free unlock.
                        onPurchase = { viewModel.debugToggleEntitlement(); screen = Screen.Home },
                        onRestore = { viewModel.debugRestoreEntitlement() },
                    )
                    is Screen.Settings -> SettingsScreen(
                        watchStatus = watchStatus,
                        entitled = entitled,
                        themeMode = viewModel.themeMode.collectAsStateWithLifecycle().value,
                        onThemeChange = viewModel::setThemeMode,
                        onBack = { screen = Screen.Home },
                        onRestore = { viewModel.debugRestoreEntitlement() },
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = DialedSpacing.lg, vertical = DialedSpacing.xl),
            )
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
            is Screen.Collection -> "collection:${it.collectionId}"
            is Screen.Detail ->
                if (it.fromCollectionId != null) "detail:${it.faceId}|from:${it.fromCollectionId}"
                else "detail:${it.faceId}"
        }
    },
    restore = {
        when {
            it == "home" -> Screen.Home
            it == "paywall" -> Screen.Paywall
            it == "settings" -> Screen.Settings
            it.startsWith("collection:") -> Screen.Collection(it.removePrefix("collection:"))
            it.startsWith("detail:") -> {
                val rest = it.removePrefix("detail:")
                val marker = "|from:"
                val idx = rest.indexOf(marker)
                if (idx >= 0) {
                    Screen.Detail(rest.substring(0, idx), rest.substring(idx + marker.length))
                } else {
                    Screen.Detail(rest)
                }
            }
            else -> Screen.Home
        }
    },
)
