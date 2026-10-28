// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol

import android.Manifest
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import com.marcelo.protocol.notification.NotificationHelper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.marcelo.protocol.notification.NotificationScheduler
import com.marcelo.protocol.ui.navigation.AppNavigation
import com.marcelo.protocol.ui.theme.ProtocolTheme

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        requestedTab.value = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_TAB)

        setContent {
            ProtocolTheme {
                AppNavigation(
                    requestedTab = requestedTab.value,
                    onTabRequestHandled = { requestedTab.value = null },
                )
            }
        }
    }

    /** Tab requested by a notification tap (e.g. "plan"), consumed by AppNavigation. */
    private val requestedTab = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(NotificationHelper.EXTRA_OPEN_TAB)?.let { requestedTab.value = it }
    }

    override fun onResume() {
        super.onResume()
        NotificationScheduler.ensureScheduled(this)
    }
}
