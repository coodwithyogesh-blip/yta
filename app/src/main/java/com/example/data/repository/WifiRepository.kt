package com.example.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.AuthorizedCredentialEntity
import com.example.data.model.ConnectionStatus
import com.example.data.model.CredentialSource
import com.example.data.model.SignalStrength
import com.example.data.model.SuccessfulConnectionResult
import com.example.data.model.WifiNetworkInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicLong

class WifiRepository(private val context: Context) {

    companion object {
        private const val TAG = "WifiRepository"
        const val DEFAULT_TIMEOUT_SECONDS = 25
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val credentialDao = AppDatabase.getInstance(context).authorizedCredentialDao()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Scanned Networks State
    private val _scannedNetworks = MutableStateFlow<List<WifiNetworkInfo>>(emptyList())
    val scannedNetworks: StateFlow<List<WifiNetworkInfo>> = _scannedNetworks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Connection State Machine
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectingSsid = MutableStateFlow<String?>(null)
    val connectingSsid: StateFlow<String?> = _connectingSsid.asStateFlow()

    private val _connectionMessage = MutableStateFlow<String?>(null)
    val connectionMessage: StateFlow<String?> = _connectionMessage.asStateFlow()

    // Successful Connection Result (Immutable)
    private val _successfulResult = MutableStateFlow<SuccessfulConnectionResult?>(null)
    val successfulResult: StateFlow<SuccessfulConnectionResult?> = _successfulResult.asStateFlow()

    // Attempt tracking
    private val attemptCounter = AtomicLong(0L)
    @Volatile
    private var activeAttemptId: Long = 0L
    private var activeCallback: ConnectivityManager.NetworkCallback? = null
    private var timeoutJob: Job? = null

    // Scan receiver
    private var isReceiverRegistered = false
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent?.action) {
                _isScanning.value = false
                processScanResults()
            }
        }
    }

    init {
        registerScanReceiver()
        updateConnectedInfo()
    }

    private fun registerScanReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                context.registerReceiver(scanReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register scan receiver: ${e.message}")
            }
        }
    }

    fun startWifiScan() {
        _isScanning.value = true
        try {
            @Suppress("DEPRECATION")
            val success = wifiManager.startScan()
            if (!success) {
                // Throttled or permission issue - fallback to cached results
                _isScanning.value = false
                processScanResults()
            }
        } catch (e: SecurityException) {
            _isScanning.value = false
            _connectionMessage.value = "Location or Nearby Wi-Fi Devices permission required to scan."
        } catch (e: Exception) {
            _isScanning.value = false
        }
    }

    fun processScanResults() {
        try {
            @Suppress("DEPRECATION")
            val results: List<ScanResult> = wifiManager.scanResults ?: emptyList()
            val connectedSsid = getCurrentlyConnectedSsid()

            val mapped = results
                .filter { !it.SSID.isNullOrBlank() }
                .groupBy { it.SSID }
                .map { (_, group) ->
                    // Pick the strongest signal for the SSID
                    group.maxByOrNull { it.level }!!
                }
                .map { sr ->
                    val isConnected = connectedSsid != null &&
                        (sr.SSID == connectedSsid || "\"${sr.SSID}\"" == connectedSsid)
                    WifiNetworkInfo(
                        ssid = sr.SSID,
                        bssid = sr.BSSID ?: "",
                        capabilities = sr.capabilities ?: "",
                        frequency = sr.frequency,
                        level = sr.level,
                        isConnected = isConnected
                    )
                }
                .sortedWith(
                    compareByDescending<WifiNetworkInfo> { it.isConnected }
                        .thenByDescending { it.level }
                )

            _scannedNetworks.value = mapped
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading scan results")
        }
    }

    private fun getCurrentlyConnectedSsid(): String? {
        try {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            if (info != null && info.networkId != -1) {
                val raw = info.ssid
                return if (raw != null && raw != "<unknown ssid>") {
                    raw.removeSurrounding("\"")
                } else null
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    fun updateConnectedInfo() {
        repositoryScope.launch {
            val ssid = getCurrentlyConnectedSsid()
            if (ssid != null && _connectionStatus.value == ConnectionStatus.IDLE) {
                // Check if active network has Wi-Fi
                val activeNetwork = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    val (ip, gw) = extractIpAndGateway(activeNetwork)
                    // If already connected to a Wi-Fi network, note it
                    processScanResults()
                }
            }
        }
    }

    /**
     * Initiates an authorized Wi-Fi connection using Android's official ConnectivityManager APIs.
     * Guaranteed:
     * - Only ONE active connection attempt exists.
     * - Attempt ID is incremented; stale callbacks are ignored safely.
     * - Password is NEVER logged.
     * - The EXACT credential passed here is captured immutably upon SUCCESS.
     */
    fun connectAuthorizedNetwork(
        ssid: String,
        candidateCredential: String,
        source: CredentialSource,
        globalLineNumber: Long? = null,
        totalLines: Long? = null,
        batchNumber: Long? = null,
        totalBatches: Long? = null,
        positionInBatch: Int? = null,
        batchSize: Int? = null,
        saveToVault: Boolean = false,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
    ) {
        // Step 1: Safely terminate previous request
        safelyTerminateActiveRequest(ConnectionStatus.CANCELLED)

        // Step 2: New attempt ID
        val thisAttemptId = attemptCounter.incrementAndGet()
        activeAttemptId = thisAttemptId

        _connectingSsid.value = ssid
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectionMessage.value = "Initiating connection to $ssid (Attempt #$thisAttemptId)..."
        Log.i(TAG, "Connection attempt #$thisAttemptId started for SSID: $ssid")

        // Step 3: Setup timeout job
        timeoutJob = repositoryScope.launch {
            delay(timeoutSeconds * 1000L)
            if (activeAttemptId == thisAttemptId && _connectionStatus.value == ConnectionStatus.CONNECTING) {
                Log.w(TAG, "Connection attempt #$thisAttemptId timed out after ${timeoutSeconds}s")
                _connectionStatus.value = ConnectionStatus.FAILED
                _connectionMessage.value = "Connection attempt timed out. Verify network range and credentials."
                safelyTerminateActiveRequest(ConnectionStatus.FAILED)
            }
        }

        // Step 4: Build Wi-Fi network specifier
        try {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(candidateCredential)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (activeAttemptId != thisAttemptId) {
                        Log.d(TAG, "Stale callback onAvailable ignored for attempt #$thisAttemptId")
                        return
                    }

                    Log.i(TAG, "Network onAvailable received for attempt #$thisAttemptId")

                    val (ip, gateway) = extractIpAndGateway(network)

                    // IMMUTABLE CAPTURE of the exact credential and metadata
                    val result = SuccessfulConnectionResult(
                        credential = candidateCredential,
                        source = source,
                        globalLineNumber = globalLineNumber,
                        totalLines = totalLines,
                        batchNumber = batchNumber,
                        totalBatches = totalBatches,
                        positionInBatch = positionInBatch,
                        batchSize = batchSize,
                        ssid = ssid,
                        ipAddress = ip,
                        gateway = gateway,
                        timestamp = System.currentTimeMillis()
                    )

                    _successfulResult.value = result
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    _connectionMessage.value = "Connected successfully to $ssid"

                    // Cancel timeout
                    timeoutJob?.cancel()
                    timeoutJob = null

                    // If user requested vault save
                    if (saveToVault) {
                        saveCredentialToVault(ssid, candidateCredential, "WPA2", source.displayName)
                    }

                    // Optional suggestion for persistent connection
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && saveToVault) {
                        try {
                            val suggestion = WifiNetworkSuggestion.Builder()
                                .setSsid(ssid)
                                .setWpa2Passphrase(candidateCredential)
                                .build()
                            wifiManager.addNetworkSuggestions(listOf(suggestion))
                        } catch (e: Exception) {
                            Log.e(TAG, "addNetworkSuggestions note: ${e.message}")
                        }
                    }
                }

                override fun onUnavailable() {
                    if (activeAttemptId != thisAttemptId) {
                        Log.d(TAG, "Stale callback onUnavailable ignored for attempt #$thisAttemptId")
                        return
                    }
                    Log.w(TAG, "Network onUnavailable for attempt #$thisAttemptId")
                    _connectionStatus.value = ConnectionStatus.FAILED
                    _connectionMessage.value = "Network unavailable or connection rejected by user/system."
                    timeoutJob?.cancel()
                    timeoutJob = null
                    safelyUnregisterCallback(this)
                }

                override fun onLost(network: Network) {
                    if (activeAttemptId != thisAttemptId) {
                        Log.d(TAG, "Stale callback onLost ignored for attempt #$thisAttemptId")
                        return
                    }
                    Log.w(TAG, "Network onLost for attempt #$thisAttemptId")
                    _connectionStatus.value = ConnectionStatus.FAILED
                    _connectionMessage.value = "Connection lost to $ssid"
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (activeAttemptId != thisAttemptId) return
                    Log.d(TAG, "Network capabilities changed for attempt #$thisAttemptId")
                }
            }

            activeCallback = callback
            connectivityManager.requestNetwork(request, callback)

        } catch (e: Exception) {
            Log.e(TAG, "Exception creating requestNetwork: ${e.message}")
            _connectionStatus.value = ConnectionStatus.FAILED
            _connectionMessage.value = "Error requesting network: ${e.localizedMessage}"
            timeoutJob?.cancel()
            timeoutJob = null
        }
    }

    fun stopActiveConnection() {
        safelyTerminateActiveRequest(ConnectionStatus.CANCELLED)
        _connectionMessage.value = "Connection stopped by user."
    }

    private fun safelyTerminateActiveRequest(finalStatus: ConnectionStatus) {
        timeoutJob?.cancel()
        timeoutJob = null

        val cb = activeCallback
        if (cb != null) {
            safelyUnregisterCallback(cb)
            activeCallback = null
        }

        if (_connectionStatus.value == ConnectionStatus.CONNECTING) {
            _connectionStatus.value = finalStatus
        }
    }

    private fun safelyUnregisterCallback(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
            Log.d(TAG, "Callback unregistered successfully")
        } catch (e: Exception) {
            // Already unregistered or invalid
            Log.d(TAG, "Safe ignore unregister exception: ${e.message}")
        }
    }

    private fun extractIpAndGateway(network: Network?): Pair<String, String> {
        var ip = "192.168.1.100"
        var gateway = "192.168.1.1"

        if (network != null) {
            val lp = connectivityManager.getLinkProperties(network)
            if (lp != null) {
                for (la in lp.linkAddresses) {
                    val addr = la.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        ip = addr.hostAddress ?: ip
                        break
                    }
                }
                for (route in lp.routes) {
                    val gw = route.gateway
                    if (gw is Inet4Address && !gw.isAnyLocalAddress) {
                        gateway = gw.hostAddress ?: gateway
                        break
                    }
                }
            }
        }
        return Pair(ip, gateway)
    }

    private fun saveCredentialToVault(ssid: String, secret: String, security: String, source: String) {
        repositoryScope.launch {
            credentialDao.insert(
                AuthorizedCredentialEntity(
                    ssid = ssid,
                    credential = secret,
                    securityType = security,
                    source = source
                )
            )
        }
    }

    fun getVaultCredentials() = credentialDao.getAll()

    suspend fun deleteVaultCredential(id: Long) = credentialDao.deleteById(id)

    suspend fun clearVault() = credentialDao.clearAll()

    fun resetConnectionState() {
        safelyTerminateActiveRequest(ConnectionStatus.IDLE)
        _connectionStatus.value = ConnectionStatus.IDLE
        _connectionMessage.value = null
        _connectingSsid.value = null
    }

    fun cleanup() {
        safelyTerminateActiveRequest(ConnectionStatus.IDLE)
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
