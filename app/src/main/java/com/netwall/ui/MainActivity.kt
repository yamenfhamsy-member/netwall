package com.netwall.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netwall.data.RulesStore
import com.netwall.ui.theme.NetWallTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetWallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle(
                        initialValue = RulesStore.Snapshot(),
                    )
                    if (snapshot.onboardingDone) {
                        MainScreen(viewModel)
                    } else {
                        OnboardingScreen(
                            onFinished = { vpnGranted ->
                                viewModel.finishOnboarding()
                                if (vpnGranted) {
                                    viewModel.enableProtection()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}
