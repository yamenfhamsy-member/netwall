package com.netwall.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netwall.R
import com.netwall.data.InstalledApp
import com.netwall.data.RulesStore
import com.netwall.vpn.FirewallController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var showDiagnostics by remember { mutableStateOf(false) }

    if (showDiagnostics) {
        DiagnosticsScreen(onBack = { showDiagnostics = false })
        return
    }

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle(
        initialValue = RulesStore.Snapshot(),
    )
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val scanCount by viewModel.scanCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.enableProtection()
        }
    }
    fun requestEnable() {
        val intent = FirewallController.prepareIntent(context)
        if (intent == null) {
            viewModel.enableProtection()
        } else {
            try {
                vpnLauncher.launch(intent)
            } catch (_: Exception) {
            }
        }
    }

    val visible = remember(apps, query, filter, snapshot) {
        apps.filter { app ->
            val matchesQuery = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            if (!matchesQuery) return@filter false
            val blocked = snapshot.wifiBlocked.contains(app.packageName) ||
                snapshot.dataBlocked.contains(app.packageName)
            when (filter) {
                AppFilter.ALL -> !app.isSystem || snapshot.showSystem
                AppFilter.BLOCKED -> blocked && (!app.isSystem || snapshot.showSystem)
                AppFilter.SYSTEM -> app.isSystem
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Text(
                        text = if (snapshot.masterEnabled) {
                            stringResource(R.string.master_on)
                        } else {
                            stringResource(R.string.master_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = snapshot.masterEnabled,
                        onCheckedChange = { on ->
                            if (on) requestEnable() else viewModel.disableProtection()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading && apps.isEmpty() && !loadError -> {
                    LoadingView(count = scanCount)
                }
                loadError && apps.isEmpty() -> {
                    ErrorView(onRetry = viewModel::refresh)
                }
                else -> {
                    if (loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FilterChip(
                            selected = filter == AppFilter.ALL,
                            onClick = { viewModel.setFilter(AppFilter.ALL) },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = filter == AppFilter.BLOCKED,
                            onClick = { viewModel.setFilter(AppFilter.BLOCKED) },
                            label = { Text(stringResource(R.string.filter_blocked)) },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = filter == AppFilter.SYSTEM,
                            onClick = { viewModel.setFilter(AppFilter.SYSTEM) },
                            label = { Text(stringResource(R.string.filter_system)) },
                        )
                    }
                    SettingsRow(
                        label = stringResource(R.string.whitelist_mode),
                        checked = snapshot.whitelistMode,
                        onCheckedChange = viewModel::setWhitelistMode,
                    )
                    SettingsRow(
                        label = stringResource(R.string.show_system),
                        checked = snapshot.showSystem,
                        onCheckedChange = viewModel::setShowSystem,
                    )
                    TextButton(
                        onClick = { showDiagnostics = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.diag_show))
                    }
                    if (visible.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.empty_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(visible, key = { it.packageName }) { app ->
                                AppRow(
                                    app = app,
                                    wifiBlocked = snapshot.wifiBlocked.contains(app.packageName),
                                    dataBlocked = snapshot.dataBlocked.contains(app.packageName),
                                    onWifiChange = { viewModel.toggleWifi(app.packageName, it) },
                                    onDataChange = { viewModel.toggleData(app.packageName, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView(count: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.loading_apps),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.loading_found, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ErrorView(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.loading_failed),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.loading_retry))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SettingsRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Lazy app icon: gray placeholder with the first letter until the real icon loads. */
@Composable
private fun AppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(IconLoader.cached(packageName)) }
    if (bitmap == null) {
        LaunchedEffect(packageName) {
            bitmap = IconLoader.load(context.applicationContext, packageName)
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    wifiBlocked: Boolean,
    dataBlocked: Boolean,
    onWifiChange: (Boolean) -> Unit,
    onDataChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName = app.packageName, label = app.label)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val status = buildString {
                    if (wifiBlocked) append(stringResource(R.string.blocked_on_wifi))
                    if (wifiBlocked && dataBlocked) append(" • ")
                    if (dataBlocked) append(stringResource(R.string.blocked_on_data))
                }
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilterChip(
                selected = wifiBlocked,
                onClick = { onWifiChange(!wifiBlocked) },
                label = { Text(stringResource(R.string.chip_wifi)) },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = dataBlocked,
                onClick = { onDataChange(!dataBlocked) },
                label = { Text(stringResource(R.string.chip_data)) },
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
