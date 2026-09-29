package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PasswordListMetadata
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BatchNavigationCard(
    metadata: PasswordListMetadata,
    currentBatchIndex: Long, // 0-based
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val currentBatchNumber = currentBatchIndex + 1
    val isPreviousEnabled = currentBatchIndex > 0
    val isNextEnabled = currentBatchIndex < (metadata.totalBatches - 1)

    // Calculate range showing for current batch
    val startEntry = (currentBatchIndex * metadata.batchSize) + 1
    val endEntry = minOf((currentBatchIndex + 1) * metadata.batchSize, metadata.totalLines)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("batch_navigation_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Password List",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total Entries: ${numberFormat.format(metadata.totalLines)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("total_entries_text")
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Batch ${numberFormat.format(currentBatchNumber)} / ${numberFormat.format(metadata.totalBatches)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("current_batch_indicator")
                    )
                    Text(
                        text = "Showing: ${numberFormat.format(startEntry)}–${numberFormat.format(endEntry)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("showing_range_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onPreviousClick,
                    enabled = isPreviousEnabled,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("previous_batch_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Batch",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Previous")
                }

                Button(
                    onClick = onNextClick,
                    enabled = isNextEnabled,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("next_batch_btn")
                ) {
                    Text(text = "Next")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Batch",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
