package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AuthorizedCredentialEntity
import com.example.data.model.ConnectionStatus
import com.example.data.model.CredentialSource
import com.example.data.model.ImportedCredentialEntry
import com.example.data.model.IndexProgress
import com.example.data.model.PasswordListMetadata
import com.example.data.model.SuccessfulConnectionResult
import com.example.data.model.WifiNetworkInfo
import com.example.data.repository.PasswordListRepository
import com.example.data.repository.WifiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    CONNECTION,
    SETTINGS,
    VAULT
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val wifiRepository = WifiRepository(application)
    val passwordListRepository = PasswordListRepository(application)

    // Navigation state
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Selected Wi-Fi Network
    private val _selectedNetwork = MutableStateFlow<WifiNetworkInfo?>(null)
    val selectedNetwork: StateFlow<WifiNetworkInfo?> = _selectedNetwork.asStateFlow()

    // Active Tab in Connection Screen (0: Option A - Enter Password, 1: Option B - Import List)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Option A: Manual Password Input
    private val _manualPasswordInput = MutableStateFlow("")
    val manualPasswordInput: StateFlow<String> = _manualPasswordInput.asStateFlow()

    private val _isManualPasswordVisible = MutableStateFlow(false)
    val isManualPasswordVisible: StateFlow<Boolean> = _isManualPasswordVisible.asStateFlow()

    private val _saveToVaultChecked = MutableStateFlow(true)
    val saveToVaultChecked: StateFlow<Boolean> = _saveToVaultChecked.asStateFlow()

    // Settings
    private val _timeoutSeconds = MutableStateFlow(25)
    val timeoutSeconds: StateFlow<Int> = _timeoutSeconds.asStateFlow()

    // Expose repository StateFlows
    val scannedNetworks: StateFlow<List<WifiNetworkInfo>> = wifiRepository.scannedNetworks
    val isScanning: StateFlow<Boolean> = wifiRepository.isScanning
    val connectionStatus: StateFlow<ConnectionStatus> = wifiRepository.connectionStatus
    val connectionMessage: StateFlow<String?> = wifiRepository.connectionMessage
    val successfulResult: StateFlow<SuccessfulConnectionResult?> = wifiRepository.successfulResult

    val passwordListMetadata: StateFlow<PasswordListMetadata?> = passwordListRepository.metadata
    val indexProgress: StateFlow<IndexProgress> = passwordListRepository.indexProgress
    val currentBatchEntries: StateFlow<List<ImportedCredentialEntry>> = passwordListRepository.currentBatchEntries
    val currentBatchIndex: StateFlow<Long> = passwordListRepository.currentBatchIndex
    val listErrorMessage: StateFlow<String?> = passwordListRepository.errorMessage

    val vaultItems: StateFlow<List<AuthorizedCredentialEntity>> = wifiRepository.getVaultCredentials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun navigateBack() {
        when (_currentScreen.value) {
            AppScreen.CONNECTION, AppScreen.SETTINGS, AppScreen.VAULT -> {
                _currentScreen.value = AppScreen.HOME
            }
            AppScreen.HOME -> { /* at root */ }
        }
    }

    fun selectNetwork(network: WifiNetworkInfo) {
        _selectedNetwork.value = network
        wifiRepository.resetConnectionState()
        _currentScreen.value = AppScreen.CONNECTION
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun setManualPassword(password: String) {
        _manualPasswordInput.value = password
    }

    fun toggleManualPasswordVisibility() {
        _isManualPasswordVisible.value = !_isManualPasswordVisible.value
    }

    fun setSaveToVault(save: Boolean) {
        _saveToVaultChecked.value = save
    }

    fun setTimeoutSeconds(seconds: Int) {
        _timeoutSeconds.value = seconds
    }

    fun scanNetworks() {
        wifiRepository.startWifiScan()
    }

    fun connectWithManualPassword() {
        val network = _selectedNetwork.value ?: return
        val password = _manualPasswordInput.value
        if (password.isBlank()) return

        wifiRepository.connectAuthorizedNetwork(
            ssid = network.ssid,
            candidateCredential = password,
            source = CredentialSource.MANUALLY_ENTERED,
            globalLineNumber = null,
            totalLines = null,
            batchNumber = null,
            totalBatches = null,
            positionInBatch = null,
            batchSize = null,
            saveToVault = _saveToVaultChecked.value,
            timeoutSeconds = _timeoutSeconds.value
        )
    }

    fun connectWithImportedEntry(entry: ImportedCredentialEntry) {
        val network = _selectedNetwork.value ?: return
        val meta = passwordListMetadata.value

        wifiRepository.connectAuthorizedNetwork(
            ssid = network.ssid,
            candidateCredential = entry.credential,
            source = CredentialSource.IMPORTED_TXT,
            globalLineNumber = entry.globalLineNumber,
            totalLines = meta?.totalLines,
            batchNumber = entry.batchNumber,
            totalBatches = meta?.totalBatches,
            positionInBatch = entry.positionInBatch,
            batchSize = meta?.batchSize ?: 500,
            saveToVault = _saveToVaultChecked.value,
            timeoutSeconds = _timeoutSeconds.value
        )
    }

    fun stopActiveConnection() {
        wifiRepository.stopActiveConnection()
    }

    fun resetConnection() {
        wifiRepository.resetConnectionState()
    }

    fun importListFromUri(uri: Uri, fileName: String, fileSize: Long) {
        passwordListRepository.startImportFromUri(uri, fileName, fileSize)
    }

    fun generateSampleList(count: Long) {
        passwordListRepository.startImportSample(count)
    }

    fun stopImport() {
        passwordListRepository.stopImport()
    }

    fun nextBatch() {
        passwordListRepository.nextBatch()
    }

    fun previousBatch() {
        passwordListRepository.previousBatch()
    }

    fun jumpToBatch(batchIndex: Long) {
        passwordListRepository.loadBatch(batchIndex)
    }

    fun clearStoredPasswordList(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            passwordListRepository.clearStorage()
            onComplete()
        }
    }

    fun deleteVaultCredential(id: Long) {
        viewModelScope.launch {
            wifiRepository.deleteVaultCredential(id)
        }
    }

    fun clearVault() {
        viewModelScope.launch {
            wifiRepository.clearVault()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiRepository.cleanup()
    }
}
