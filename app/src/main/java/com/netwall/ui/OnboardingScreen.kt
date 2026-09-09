package com.netwall.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.netwall.R
import com.netwall.vpn.FirewallController

/**
 * Three-step intro: what the app does, the local-VPN explanation,
 * then permission grants. Text-only monochrome, no illustrations.
 */
@Composable
fun OnboardingScreen(onFinished: (vpnGranted: Boolean) -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var vpnGranted by remember { mutableStateOf(FirewallController.isPrepared(context)) }
    var batteryOk by remember {
        mutableStateOf(isBatteryOptimizedOff(context))
    }
    var notifOk by remember { mutableStateOf(isNotificationGranted(context)) }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        vpnGranted = result.resultCode == Activity.RESULT_OK &&
            FirewallController.isPrepared(context)
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifOk = granted || isNotificationGranted(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            0 -> OnboardingPage(
                title = stringResource(R.string.onboarding_title_1),
                text = stringResource(R.string.onboarding_text_1),
            )
            1 -> OnboardingPage(
                title = stringResource(R.string.onboarding_title_2),
                text = stringResource(R.string.onboarding_text_2),
            )
            else -> {
                OnboardingPage(
                    title = stringResource(R.string.onboarding_title_3),
                    text = stringResource(R.string.onboarding_text_3),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        val intent = FirewallController.prepareIntent(context)
                        if (intent == null) {
                            vpnGranted = true
                        } else {
                            try {
                                vpnLauncher.launch(intent)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.onboarding_grant_vpn) +
                            if (vpnGranted) " ✓" else "",
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        batteryOk = requestBatteryExclusion(context) ||
                            isBatteryOptimizedOff(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.onboarding_battery) +
                            if (batteryOk) " ✓" else "",
                    )
                }
                if (Build.VERSION.SDK_INT >= 33 && !notifOk) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Notifications")
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${step + 1} / 3",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 14.dp),
            )
            Button(
                onClick = {
                    if (step < 2) {
                        step++
                    } else {
                        onFinished(vpnGranted)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (step < 2) stringResource(R.string.onboarding_next)
                    else stringResource(R.string.onboarding_start),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(title: String, text: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun isBatteryOptimizedOff(context: android.content.Context): Boolean {
    return try {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }
}

private fun requestBatteryExclusion(context: android.content.Context): Boolean {
    return try {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        try {
            val fallback = Intent(
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            true
        } catch (_: Exception) {
            false
        }
    }
}

private fun isNotificationGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
