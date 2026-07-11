package com.dialed.app.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.dialed.app.wear.common.WearConstants
import com.dialed.app.wear.ui.WearApp
import com.dialed.app.wear.ui.theme.DialedWearTheme
import com.dialed.app.wear.wfp.ReceiveState
import com.dialed.app.wear.wfp.TransferSession

class MainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    private val requestPushPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onPushPermissionResult(granted)
        }

    private val requestSetActivePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onSetActivePermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Manual launch (not from a push) shouldn't drop the user into a stale receive/concierge.
        val fromTransfer = intent.getBooleanExtra(EXTRA_FROM_TRANSFER, false)
        if (!fromTransfer) {
            val current = TransferSession.state.value
            if (current is ReceiveState.Success || current is ReceiveState.Failed) {
                viewModel.dismissReceive()
            }
        }

        setContent {
            DialedWearTheme {
                WearApp(
                    viewModel = viewModel,
                    onAllow = { requestPushPermission.launch(WearConstants.PERMISSION_PUSH) },
                    onSetActive = { requestSetActivePermission.launch(WearConstants.PERMISSION_SET_ACTIVE) },
                    onOpenSettings = ::openAppSettings,
                    onExit = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            },
        )
    }

    companion object {
        const val EXTRA_FROM_TRANSFER = "dialed.from_transfer"
    }
}
