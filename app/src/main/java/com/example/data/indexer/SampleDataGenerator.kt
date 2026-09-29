package com.example.data.indexer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object SampleDataGenerator {

    /**
     * Generates a sample candidate password list as an InputStream.
     * Contains the exact test sequence from requirement 23:
     * Line 1: 12ertyuoi
     * Line 2: equiwywyw
     * Line 3: Aarti7756
     * followed by realistic entries up to [count].
     */
    fun createSampleStream(count: Long): Pair<InputStream, Long> {
        val stringBuilder = StringBuilder()

        // Required test entries from prompt requirement 23
        val corePrefix = listOf(
            "12ertyuoi",
            "equiwywyw",
            "Aarti7756",
            "OfficeAdmin2024",
            "GuestAccess@99",
            "StaffNet!772",
            "CampusWiFi#881",
            "SecureCorpPass3",
            "ConferenceRoom01",
            "LabNetwork#44"
        )

        for (i in 1..count) {
            val entry = if (i <= corePrefix.size) {
                corePrefix[(i - 1).toInt()]
            } else {
                "AuthKey_${i}_pass"
            }
            stringBuilder.append(entry).append("\n")
        }

        val bytes = stringBuilder.toString().toByteArray(Charsets.UTF_8)
        return Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
    }
}
