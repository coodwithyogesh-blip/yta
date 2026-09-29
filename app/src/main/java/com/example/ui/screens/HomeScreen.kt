package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WifiNetworkInfo
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.components.WifiNetworkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scannedNetworks by viewModel.scannedNetworks.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var showAddManualDialog by remember { mutableStateOf(false) }
    var manualSsidInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.testTag("home_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wi-Fi Networks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onRequestPermissions()
                            viewModel.scanNetworks()
                        },
                        modifier = Modifier.testTag("scan_networks_icon_btn")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Networks"
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                        modifier = Modifier.testTag("open_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scanner Status / Action Banner
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isScanning) "Scanning for nearby networks…" else "Available Networks (${scannedNetworks.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row {
                        OutlinedButton(
                            onClick = { showAddManualDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("add_custom_network_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add SSID",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Add SSID", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Networks List
            if (scannedNetworks.isEmpty() && !isScanning) {
                item {
                    EmptyNetworksCard(
                        onScanClick = {
                            onRequestPermissions()
                            viewModel.scanNetworks()
                        },
                        onAddManualClick = { showAddManualDialog = true }
                    )
                }
            } else {
                items(
                    items = scannedNetworks,
                    key = { it.bssid.ifBlank { it.ssid } }
                ) { network ->
                    WifiNetworkCard(
                        network = network,
                        onConnectClick = {
                            viewModel.selectNetwork(network)
                        }
                    )
                }
            }
        }
    }

    if (showAddManualDialog) {
        AlertDialog(
            onDismissRequest = { showAddManualDialog = false },
            title = { Text(text = "Enter Network SSID") },
            text = {
                Column {
                    Text(
                        text = "Specify a network name (e.g. for hidden or direct testing networks):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualSsidInput,
                        onValueChange = { manualSsidInput = it },
                        label = { Text("Network SSID") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_ssid_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualSsidInput.isNotBlank()) {
                            val custom = WifiNetworkInfo(
                                ssid = manualSsidInput.trim(),
                                bssid = "00:00:00:00:00:00",
                                capabilities = "[WPA2-PSK-CCMP][ESS]",
                                frequency = 2412,
                                level = -60,
                                isConnected = false
                            )
                            viewModel.selectNetwork(custom)
                            showAddManualDialog = false
                            manualSsidInput = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_manual_ssid_btn")
                ) {
                    Text("Select Network")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyNetworksCard(
    onScanClick: () -> Unit,
    onAddManualClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("empty_networks_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "No Networks",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Wi-Fi Networks Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ensure Wi-Fi and Location permissions are enabled, then scan for nearby authorized networks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("empty_scan_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Networks")
                }

                OutlinedButton(
                    onClick = onAddManualClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("empty_add_ssid_btn")
                ) {
                    Text("Add SSID")
                }
            }
        }
    }
}
