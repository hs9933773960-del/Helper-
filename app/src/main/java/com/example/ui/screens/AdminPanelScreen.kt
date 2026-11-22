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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.localization.AppStrings
import com.example.model.Job
import com.example.model.PaymentConfig
import com.example.model.PaymentRecord
import com.example.model.UiState
import com.example.model.UserProfile

@Composable
fun AdminPanelScreen(
    paymentConfig: PaymentConfig,
    allJobsState: UiState<List<Job>>,
    allPaymentsState: UiState<List<PaymentRecord>>,
    allUsersState: UiState<List<UserProfile>>,
    strings: AppStrings,
    onSavePaymentConfig: (PaymentConfig) -> Unit,
    onUpdatePaymentStatus: (paymentId: String, status: String) -> Unit,
    onUpdateJobStatus: (jobId: String, status: String) -> Unit,
    onDeleteJob: (jobId: String) -> Unit,
    onToggleBlockUser: (userId: String, blocked: Boolean) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }

    val jobsList = (allJobsState as? UiState.Success)?.data.orEmpty()
    val paymentsList = (allPaymentsState as? UiState.Success)?.data.orEmpty()
    val usersList = (allUsersState as? UiState.Success)?.data.orEmpty()

    val verifiedRevenue = paymentsList.filter { it.status == "VERIFIED" }.sumOf { it.amount }
    val pendingPaymentsCount = paymentsList.count { it.status == "PENDING" }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // KPI strip
        Row(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminStatChip(strings.adminRevenue, "₹$verifiedRevenue", Modifier.weight(1f))
            AdminStatChip(strings.adminTotalJobs, "${jobsList.size}", Modifier.weight(1f))
            AdminStatChip(strings.adminPendingFees, "$pendingPaymentsCount", Modifier.weight(1f))
        }

        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Payments") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Jobs (${jobsList.size})") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Users (${usersList.size})") })
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            when (subTab) {
                0 -> AdminPaymentsAndConfigTab(
                    paymentConfig = paymentConfig,
                    paymentsState = allPaymentsState,
                    strings = strings,
                    onSavePaymentConfig = onSavePaymentConfig,
                    onUpdatePaymentStatus = onUpdatePaymentStatus
                )
                1 -> AdminJobsTab(
                    jobsState = allJobsState,
                    strings = strings,
                    onUpdateJobStatus = onUpdateJobStatus,
                    onDeleteJob = onDeleteJob
                )
                else -> AdminUsersTab(
                    usersState = allUsersState,
                    strings = strings,
                    onToggleBlockUser = onToggleBlockUser
                )
            }
        }
    }
}

@Composable
private fun AdminStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AdminPaymentsAndConfigTab(
    paymentConfig: PaymentConfig,
    paymentsState: UiState<List<PaymentRecord>>,
    strings: AppStrings,
    onSavePaymentConfig: (PaymentConfig) -> Unit,
    onUpdatePaymentStatus: (paymentId: String, status: String) -> Unit
) {
    var adminName by remember(paymentConfig.adminName) { mutableStateOf(paymentConfig.adminName) }
    var upiId by remember(paymentConfig.upiId) { mutableStateOf(paymentConfig.upiId) }
    var accountNo by remember(paymentConfig.accountNumber) { mutableStateOf(paymentConfig.accountNumber) }
    var ifsc by remember(paymentConfig.ifscCode) { mutableStateOf(paymentConfig.ifscCode) }
    var phonePe by remember(paymentConfig.phonePeNumber) { mutableStateOf(paymentConfig.phonePeNumber) }
    var employerFee by remember(paymentConfig.employerBookingFee) { mutableStateOf(paymentConfig.employerBookingFee.toString()) }
    var workerFee by remember(paymentConfig.workerAcceptFee) { mutableStateOf(paymentConfig.workerAcceptFee.toString()) }

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.adminPaymentSettings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = adminName,
                        onValueChange = { adminName = it },
                        label = { Text(strings.adminNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text(strings.upiIdLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = accountNo,
                            onValueChange = { accountNo = it },
                            label = { Text("Account No") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ifsc,
                            onValueChange = { ifsc = it },
                            label = { Text("IFSC") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    OutlinedTextField(
                        value = phonePe,
                        onValueChange = { phonePe = it },
                        label = { Text("PhonePe / Paytm Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = employerFee,
                            onValueChange = { employerFee = it.filter(Char::isDigit) },
                            label = { Text("Employer Fee (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = workerFee,
                            onValueChange = { workerFee = it.filter(Char::isDigit) },
                            label = { Text("Worker Fee (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            onSavePaymentConfig(
                                PaymentConfig(
                                    adminName = adminName,
                                    upiId = upiId,
                                    accountNumber = accountNo,
                                    ifscCode = ifsc,
                                    phonePeNumber = phonePe,
                                    employerBookingFee = employerFee.toLongOrNull() ?: 10L,
                                    workerAcceptFee = workerFee.toLongOrNull() ?: 10L
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_save_config_button")
                    ) {
                        Text(strings.adminSaveSettings)
                    }
                }
            }
        }

        when (paymentsState) {
            is UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item { Text(paymentsState.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> {
                items(paymentsState.data, key = { it.id }) { pay ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${pay.userName} (${pay.userRole})", fontWeight = FontWeight.Bold)
                                Text("₹${pay.amount} • ${pay.status}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text("${pay.feeType} • ${pay.paymentApp} • Ref: ${pay.transactionRef}", style = MaterialTheme.typography.bodySmall)
                            Text(pay.jobTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            if (pay.status == "PENDING") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onUpdatePaymentStatus(pay.id, "VERIFIED") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(strings.verifyBtn)
                                    }
                                    OutlinedButton(
                                        onClick = { onUpdatePaymentStatus(pay.id, "REJECTED") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(strings.rejectBtn)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminJobsTab(
    jobsState: UiState<List<Job>>,
    strings: AppStrings,
    onUpdateJobStatus: (jobId: String, status: String) -> Unit,
    onDeleteJob: (jobId: String) -> Unit
) {
    when (jobsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { Text(jobsState.message) }
        is UiState.Success -> {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(jobsState.data, key = { it.id }) { job ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(job.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(job.status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            Text("${job.employerName} • ${job.city} • ₹${job.wageAmount}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (job.status != "CANCELLED") {
                                    OutlinedButton(onClick = { onUpdateJobStatus(job.id, "CANCELLED") }) {
                                        Text(strings.cancelJob)
                                    }
                                }
                                OutlinedButton(onClick = { onDeleteJob(job.id) }) {
                                    Text(strings.deleteJob)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    usersState: UiState<List<UserProfile>>,
    strings: AppStrings,
    onToggleBlockUser: (userId: String, blocked: Boolean) -> Unit
) {
    when (usersState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { Text(usersState.message) }
        is UiState.Success -> {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(usersState.data, key = { it.id }) { user ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName, fontWeight = FontWeight.Bold)
                                Text("${user.role} • ${user.city} • ${user.phoneNumber}", style = MaterialTheme.typography.bodySmall)
                                Text(user.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { onToggleBlockUser(user.userId, !user.blocked) }) {
                                Text(if (user.blocked) strings.unblockUser else strings.blockUser)
                            }
                        }
                    }
                }
            }
        }
    }
}
