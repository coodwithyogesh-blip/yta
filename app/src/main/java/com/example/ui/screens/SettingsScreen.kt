package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AuthorizedCredentialEntity
import com.example.data.model.PasswordListMetadata
import com.example.ui.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val metadata by viewModel.passwordListMetadata.collectAsState()
    val timeoutSeconds by viewModel.timeoutSeconds.collectAsState()
    val vaultItems by viewModel.vaultItems.collectAsState()

    var showClearListDialog by remember { mutableStateOf(false) }
    var showClearVaultDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.testTag("settings_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION: PASSWORD LIST STORAGE
            item {
                StorageSettingsCard(
                    metadata = metadata,
                    dataFileBytes = viewModel.passwordListRepository.getDataFileSize(),
                    indexFileBytes = viewModel.passwordListRepository.getIndexFileSize(),
                    onClearClick = { showClearListDialog = true }
                )
            }

            // SECTION: CONNECTION SETTINGS
            item {
                ConnectionSettingsCard(
                    timeoutSeconds = timeoutSeconds,
                    onTimeoutChange = { viewModel.setTimeoutSeconds(it) }
                )
            }

            // SECTION: AUTHORIZED CREDENTIALS VAULT
            item {
                VaultHeaderCard(
                    itemCount = vaultItems.size,
                    onClearVaultClick = { showClearVaultDialog = true }
                )
            }

            if (vaultItems.isNotEmpty()) {
                items(
                    items = vaultItems,
                    key = { it.id }
                ) { item ->
                    VaultItemRow(
                        item = item,
                        onDeleteClick = { viewModel.deleteVaultCredential(item.id) }
                    )
                }
            }

            // SECTION: ABOUT & PRIVACY
            item {
                AboutPrivacyCard()
            }
        }
    }

    if (showClearListDialog) {
        AlertDialog(
            onDismissRequest = { showClearListDialog = false },
            title = { Text("Clear Stored Password List?") },
            text = {
                Text("This will permanently delete the stored credentials file, index, and associated metadata from device storage.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearStoredPasswordList {
                            showClearListDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_clear_list_btn")
                ) {
                    Text("Clear List")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearListDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearVaultDialog) {
        AlertDialog(
            onDismissRequest = { showClearVaultDialog = false },
            title = { Text("Clear Credentials Vault?") },
            text = {
                Text("This will remove all saved authorized credentials from the local vault database.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearVault()
                        showClearVaultDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_clear_vault_btn")
                ) {
                    Text("Clear Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearVaultDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StorageSettingsCard(
    metadata: PasswordListMetadata?,
    dataFileBytes: Long,
    indexFileBytes: Long,
    onClearClick: () -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storage_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Password List Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (metadata != null && metadata.isAvailable) {
                StorageRow("File:", metadata.fileName)
                StorageRow("Total:", numberFormat.format(metadata.totalLines))
                StorageRow("Total Batches:", numberFormat.format(metadata.totalBatches))
                StorageRow("Batch Size:", "${metadata.batchSize}")
                StorageRow("Status:", "Available")
                StorageRow("Imported:", dateFormat.format(Date(metadata.importedTimestamp)))
                StorageRow("Data File Size:", formatBytes(dataFileBytes))
                StorageRow("Offset Index Size:", formatBytes(indexFileBytes))

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onClearClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_stored_password_list_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Clear List",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Clear Stored Password List", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    text = "No Password List Imported",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("no_list_imported_text")
                )
            }
        }
    }
}

@Composable
private fun ConnectionSettingsCard(
    timeoutSeconds: Int,
    onTimeoutChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connection Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connection Timeout",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${timeoutSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = timeoutSeconds.toFloat(),
                onValueChange = { onTimeoutChange(it.toInt()) },
                valueRange = 10f..60f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Each authorized Wi-Fi connection attempt automatically aborts and cleans up after the timeout window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VaultHeaderCard(
    itemCount: Int,
    onClearVaultClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Authorized Credentials Vault ($itemCount)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (itemCount > 0) {
            TextButton(
                onClick = onClearVaultClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear All")
            }
        }
    }
}

@Composable
private fun VaultItemRow(
    item: AuthorizedCredentialEntity,
    onDeleteClick: () -> Unit
) {
    var isRevealed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.ssid,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Security: ${item.securityType} • ${item.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isRevealed) item.credential else "••••••••",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { isRevealed = !isRevealed },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle credential visibility",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete from vault",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutPrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About & Privacy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Wi-Fi Access Manager v1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• All credentials are stored and processed strictly locally.\n• Never logs or transmits credentials over network.\n• Memory-bounded streaming architecture supports up to 10,000,000 line lists.\n• Uses official Android ConnectivityManager and WifiNetworkSpecifier APIs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
