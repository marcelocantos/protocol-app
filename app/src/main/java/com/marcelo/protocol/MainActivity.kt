// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.marcelo.protocol.notification.NotificationScheduler
import com.marcelo.protocol.ui.navigation.AppNavigation
import com.marcelo.protocol.ui.theme.ProtocolTheme
import com.marcelo.protocol.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {

    // Held by the activity so onResume can re-sync the selected date; the
    // same instance the navigation graph hands to TodayScreen.
    private val todayVm: TodayViewModel by viewModels()

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

        setContent {
            ProtocolTheme {
                AppNavigation(todayVm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationScheduler.ensureScheduled(this)
        // The activity is singleTask, so its ViewModel outlives a night in
        // the background: without this the checklist stays on yesterday.
        todayVm.refreshDate()
    }
}
