package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WifiConnectionScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        viewModel = viewModel,
                        onRequestPermissions = { requestWifiPermissions() },
                        onImportFile = { filePickerLauncher?.launch(arrayOf("text/plain", "*/*")) }
                    )
                }
            }
        }
    }

    private var filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null

    private fun requestWifiPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher?.launch(permissions.toTypedArray())
    }

    private var permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null

    @Composable
    private fun AppNavigation(
        viewModel: MainViewModel,
        onRequestPermissions: () -> Unit,
        onImportFile: () -> Unit
    ) {
        val currentScreen by viewModel.currentScreen.collectAsState()

        // Setup File Picker Launcher
        val fileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let { handleDocumentUri(it) }
        }

        // Setup Permission Launcher
        val permLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineLocationGranted || coarseLocationGranted) {
                viewModel.scanNetworks()
            }
        }

        LaunchedEffect(Unit) {
            filePickerLauncher = fileLauncher
            permissionLauncher = permLauncher
            // Initial scan if permission is already granted
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                viewModel.scanNetworks()
            } else {
                requestWifiPermissions()
            }
        }

        when (currentScreen) {
            AppScreen.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    onRequestPermissions = onRequestPermissions
                )
            }
            AppScreen.CONNECTION -> {
                WifiConnectionScreen(
                    viewModel = viewModel,
                    onLaunchFilePicker = onImportFile
                )
            }
            AppScreen.SETTINGS, AppScreen.VAULT -> {
                SettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }

    private fun handleDocumentUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Persistable permission may not be supported by some providers; continue
        }

        var fileName = "password_list.txt"
        var fileSize = 0L

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        viewModel.importListFromUri(uri, fileName, fileSize)
    }
}
