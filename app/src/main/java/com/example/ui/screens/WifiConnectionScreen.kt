package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.data.model.ImportedCredentialEntry
import com.example.ui.MainViewModel
import com.example.ui.components.ActiveConnectionCard
import com.example.ui.components.BatchNavigationCard
import com.example.ui.components.IndexProgressCard
import com.example.ui.components.SuccessfulResultCard
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiConnectionScreen(
    viewModel: MainViewModel,
    onLaunchFilePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val selectedNetwork by viewModel.selectedNetwork.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val manualPassword by viewModel.manualPasswordInput.collectAsState()
    val isPasswordVisible by viewModel.isManualPasswordVisible.collectAsState()
    val saveToVault by viewModel.saveToVaultChecked.collectAsState()

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectionMessage by viewModel.connectionMessage.collectAsState()
    val successfulResult by viewModel.successfulResult.collectAsState()

    val passwordListMetadata by viewModel.passwordListMetadata.collectAsState()
    val indexProgress by viewModel.indexProgress.collectAsState()
    val currentBatchEntries by viewModel.currentBatchEntries.collectAsState()
    val currentBatchIndex by viewModel.currentBatchIndex.collectAsState()
    val listErrorMessage by viewModel.listErrorMessage.collectAsState()

    val network = selectedNetwork ?: return

    Scaffold(
        modifier = modifier.testTag("wifi_connection_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = network.ssid,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Security: ${network.securityType} • ${network.bandDisplay}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("connection_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Header Card
            item {
                NetworkStatusHeaderCard(
                    networkSsid = network.ssid,
                    securityType = network.securityType,
                    band = network.bandDisplay,
                    connectionStatus = connectionStatus
                )
            }

            // Active Connection State Card (Connecting / Failed / Cancelled)
            item {
                ActiveConnectionCard(
                    status = connectionStatus,
                    message = connectionMessage,
                    onStopClick = { viewModel.stopActiveConnection() },
                    onDismissClick = { viewModel.resetConnection() }
                )
            }

            // Successful Result Card (Shown when connection succeeds)
            if (connectionStatus == ConnectionStatus.CONNECTED && successfulResult != null) {
                item {
                    SuccessfulResultCard(result = successfulResult!!)
                }
            }

            // Tabs for Option A & Option B
            item {
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.testTag("connection_tabs")
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        text = { Text("Option A — Enter Password", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("tab_option_a")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        text = { Text("Option B — Import List", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("tab_option_b")
                    )
                }
            }

            // Tab Content
            if (selectedTab == 0) {
                // OPTION A — ENTER PASSWORD
                item {
                    OptionAContent(
                        manualPassword = manualPassword,
                        isPasswordVisible = isPasswordVisible,
                        saveToVault = saveToVault,
                        isConnecting = connectionStatus == ConnectionStatus.CONNECTING,
                        onPasswordChange = { viewModel.setManualPassword(it) },
                        onToggleVisibility = { viewModel.toggleManualPasswordVisibility() },
                        onSaveToVaultChange = { viewModel.setSaveToVault(it) },
                        onConnectClick = { viewModel.connectWithManualPassword() }
                    )
                }
            } else {
                // OPTION B — IMPORT LIST
                item {
                    OptionBHeader(
                        listErrorMessage = listErrorMessage,
                        isIndexing = indexProgress.isBuilding,
                        onImportFileClick = onLaunchFilePicker,
                        onGenerateSampleClick = { count -> viewModel.generateSampleList(count) }
                    )
                }

                // Indexing Progress Card
                if (indexProgress.isBuilding) {
                    item {
                        IndexProgressCard(
                            progress = indexProgress,
                            onStopClick = { viewModel.stopImport() }
                        )
                    }
                }

                // Batch Navigation & List Entries
                if (passwordListMetadata != null && !indexProgress.isBuilding) {
                    val meta = passwordListMetadata!!
                    if (meta.isAvailable && meta.totalBatches > 0) {
                        item {
                            BatchNavigationCard(
                                metadata = meta,
                                currentBatchIndex = currentBatchIndex,
                                onPreviousClick = { viewModel.previousBatch() },
                                onNextClick = { viewModel.nextBatch() }
                            )
                        }

                        item {
                            Text(
                                text = "Select an authorized credential candidate below to initiate connection:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 500-Entry Batch Items
                        items(
                            items = currentBatchEntries,
                            key = { "${it.batchNumber}_${it.globalLineNumber}" }
                        ) { entry ->
                            ImportedEntryRow(
                                entry = entry,
                                totalLines = meta.totalLines,
                                isConnecting = connectionStatus == ConnectionStatus.CONNECTING,
                                onConnectClick = {
                                    viewModel.connectWithImportedEntry(entry)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkStatusHeaderCard(
    networkSsid: String,
    securityType: String,
    band: String,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = networkSsid,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Security: $securityType  •  $band",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> "Connected"
                        ConnectionStatus.CONNECTING -> "Connecting…"
                        ConnectionStatus.FAILED -> "Failed"
                        ConnectionStatus.CANCELLED -> "Cancelled"
                        ConnectionStatus.IDLE -> "Not Connected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.tertiary
                        ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                        ConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("header_connection_status")
                )
            }
        }
    }
}

@Composable
private fun OptionAContent(
    manualPassword: String,
    isPasswordVisible: Boolean,
    saveToVault: Boolean,
    isConnecting: Boolean,
    onPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSaveToVaultChange: (Boolean) -> Unit,
    onConnectClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("option_a_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Enter Password Manually",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = manualPassword,
                onValueChange = onPasswordChange,
                label = { Text("Wi-Fi Password") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_password_input"),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier.testTag("toggle_manual_password_visibility_btn")
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = saveToVault,
                    onCheckedChange = onSaveToVaultChange,
                    modifier = Modifier.testTag("save_to_vault_checkbox")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Save to Authorized Credentials Vault",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConnectClick,
                enabled = manualPassword.isNotBlank() && !isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("manual_connect_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnecting) "Connecting…" else "Connect",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OptionBHeader(
    listErrorMessage: String?,
    isIndexing: Boolean,
    onImportFileClick: () -> Unit,
    onGenerateSampleClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!listErrorMessage.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = listErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onImportFileClick,
                enabled = !isIndexing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_file_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = "Import TXT",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import TXT File", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = { onGenerateSampleClick(2000L) },
                enabled = !isIndexing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("generate_sample_btn")
            ) {
                Text("Sample (2,000)", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ImportedEntryRow(
    entry: ImportedCredentialEntry,
    totalLines: Long,
    isConnecting: Boolean,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    var isRevealed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("entry_row_${entry.globalLineNumber}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Line ${numberFormat.format(entry.globalLineNumber)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " / ${numberFormat.format(totalLines)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(Pos: ${entry.positionInBatch}/500)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isRevealed) entry.credential else "•".repeat(entry.credential.length.coerceAtLeast(6)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("credential_text_${entry.globalLineNumber}")
                    )
                    IconButton(
                        onClick = { isRevealed = !isRevealed },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle display",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onConnectClick,
                enabled = !isConnecting,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("connect_entry_btn_${entry.globalLineNumber}")
            ) {
                Text(text = "Connect", fontSize = 12.sp)
            }
        }
    }
}
