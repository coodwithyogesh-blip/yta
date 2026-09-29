package com.example.data.model

enum class SignalStrength(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Strong"),
    FAIR("Fair"),
    POOR("Weak")
}

data class WifiNetworkInfo(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val frequency: Int, // in MHz
    val level: Int, // in dBm
    val isConnected: Boolean = false,
    val securityType: String = parseSecurity(capabilities),
    val bandDisplay: String = parseBand(frequency)
) {
    val signalStrength: SignalStrength = when {
        level >= -55 -> SignalStrength.EXCELLENT
        level >= -67 -> SignalStrength.GOOD
        level >= -80 -> SignalStrength.FAIR
        else -> SignalStrength.POOR
    }

    companion object {
        fun parseSecurity(capabilities: String): String {
            return when {
                capabilities.contains("WPA3", ignoreCase = true) || capabilities.contains("SAE", ignoreCase = true) -> "WPA3"
                capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
                capabilities.contains("WPA", ignoreCase = true) -> "WPA"
                capabilities.contains("WEP", ignoreCase = true) -> "WEP"
                capabilities.contains("EAP", ignoreCase = true) -> "802.1x EAP"
                capabilities.isEmpty() || capabilities.contains("ESS", ignoreCase = true) && !capabilities.contains("WPA", ignoreCase = true) -> "Open"
                else -> "WPA2"
            }
        }

        fun parseBand(frequency: Int): String {
            return when {
                frequency in 2400..2500 -> "2.4 GHz"
                frequency in 4900..5900 -> "5 GHz"
                frequency in 5925..7125 -> "6 GHz"
                frequency == 0 -> "Dual Band"
                else -> "$frequency MHz"
            }
        }
    }
}
