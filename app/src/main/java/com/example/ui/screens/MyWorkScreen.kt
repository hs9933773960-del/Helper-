package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.localization.AppStrings
import com.example.model.Job
import com.example.model.UiState

@Composable
fun MyWorkScreen(
    employerJobsState: UiState<List<Job>>,
    workerJobsState: UiState<List<Job>>,
    strings: AppStrings,
    onOpenChat: (jobId: String) -> Unit,
    onCallCounterparty: (job: Job, isEmployerView: Boolean) -> Unit,
    onMarkCompleted: (jobId: String) -> Unit,
    onCancelJob: (jobId: String) -> Unit,
    onDeleteJob: (jobId: String) -> Unit,
    onRateUser: (jobId: String, targetUserId: String, targetName: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.postedJobsTab) },
                modifier = Modifier.testTag("mywork_tab_employer")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(strings.acceptedJobsTab) },
                modifier = Modifier.testTag("mywork_tab_worker")
            )
        }

        val activeState = if (selectedTab == 0) employerJobsState else workerJobsState
        val isEmployerView = selectedTab == 0

        when (activeState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(activeState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                if (activeState.data.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = strings.emptyJobsTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(activeState.data, key = { it.id }) { job ->
                            MyWorkCard(
                                job = job,
                                isEmployerView = isEmployerView,
                                strings = strings,
                                onOpenChat = { onOpenChat(job.id) },
                                onCallClick = { onCallCounterparty(job, isEmployerView) },
                                onMarkCompleted = { onMarkCompleted(job.id) },
                                onCancelJob = { onCancelJob(job.id) },
                                onDeleteJob = { onDeleteJob(job.id) },
                                onRateClick = {
                                    val targetId = if (isEmployerView) job.acceptedWorkerId.orEmpty() else job.employerId
                                    val targetName = if (isEmployerView) job.acceptedWorkerName.orEmpty() else job.employerName
                                    if (targetId.isNotBlank()) {
                                        onRateUser(job.id, targetId, targetName)
                                    }
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
private fun MyWorkCard(
    job: Job,
    isEmployerView: Boolean,
    strings: AppStrings,
    onOpenChat: () -> Unit,
    onCallClick: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCancelJob: () -> Unit,
    onDeleteJob: () -> Unit,
    onRateClick: () -> Unit
) {
    val statusLabel = when (job.status) {
        "OPEN" -> strings.statusOpen
        "ACCEPTED" -> strings.statusAccepted
        "COMPLETED" -> strings.statusCompleted
        else -> strings.statusCancelled
    }
    val statusBg = when (job.status) {
        "OPEN" -> MaterialTheme.colorScheme.primaryContainer
        "ACCEPTED" -> MaterialTheme.colorScheme.tertiaryContainer
        "COMPLETED" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = statusBg) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "₹${job.wageAmount} ${if (job.wagePeriod == "PER_DAY") strings.perDay else strings.perMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(job.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(job.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val partnerLine = if (isEmployerView) {
                if (!job.acceptedWorkerName.isNullOrBlank()) "${strings.roleWorker}: ${job.acceptedWorkerName}" else strings.statusOpen
            } else {
                "${strings.roleEmployer}: ${job.employerName}"
            }
            Text(partnerLine, style = MaterialTheme.typography.labelMedium)

            if (job.status == "ACCEPTED" || job.status == "COMPLETED") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.chatLabel)
                    }
                    OutlinedButton(onClick = onCallClick, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.callLabel)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEmployerView && job.status == "ACCEPTED") {
                    OutlinedButton(onClick = onMarkCompleted, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.markCompleted)
                    }
                }
                if (isEmployerView && job.status == "OPEN") {
                    TextButton(onClick = onCancelJob) { Text(strings.cancelJob) }
                    TextButton(onClick = onDeleteJob) {
                        Icon(Icons.Default.Delete, contentDescription = strings.deleteJob, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.deleteJob)
                    }
                }
                if ((job.status == "ACCEPTED" || job.status == "COMPLETED") && !job.acceptedWorkerId.isNullOrBlank()) {
                    OutlinedButton(onClick = onRateClick, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rateAndReview)
                    }
                }
            }
        }
    }
}
