package com.example.data.model

data class IndexProgress(
    val processedLines: Long = 0L,
    val totalEstimatedLines: Long = 0L,
    val progressPercent: Float = 0f,
    val remainingLines: Long = 0L,
    val speedEntriesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val isBuilding: Boolean = false,
    val statusMessage: String = ""
) {
    val etaFormatted: String
        get() {
            if (etaSeconds <= 0) return "--"
            val mins = etaSeconds / 60
            val secs = etaSeconds % 60
            return if (mins > 0) {
                String.format(java.util.Locale.US, "%d min %02d sec", mins, secs)
            } else {
                String.format(java.util.Locale.US, "%d sec", secs)
            }
        }
}
