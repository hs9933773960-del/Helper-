package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.localization.AppLanguage
import com.example.localization.AppStrings
import com.example.model.JOB_CATEGORIES
import com.example.model.POPULAR_CITIES
import com.example.model.PaymentConfig
import com.example.model.Review
import com.example.model.UserProfile
import com.example.util.launchUpiPaymentApp

@Composable
fun LanguageSelectorRow(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLanguage.entries.forEach { lang ->
            val isSelected = lang == selected
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .clickable { onSelect(lang) }
                    .testTag("lang_chip_${lang.code}")
            ) {
                Text(
                    text = lang.nativeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiPaymentSheet(
    title: String,
    feeNote: String,
    amountInr: Long,
    paymentConfig: PaymentConfig,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirmPayment: (paymentApp: String, transactionRef: String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedApp by remember { mutableStateOf("PhonePe") }
    var transactionRef by remember { mutableStateOf("") }
    var fallbackMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = feeNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "₹$amountInr",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.adminAccountDetails,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "${strings.adminNameLabel}: ${paymentConfig.adminName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${strings.upiIdLabel}: ${paymentConfig.upiId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${strings.accountNoLabel}: ${paymentConfig.accountNumber} (${paymentConfig.ifscCode}) • ${paymentConfig.phonePeNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = strings.payViaAppHint,
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val apps = listOf(
                    Triple("PhonePe", "com.phonepe.app", "PhonePe"),
                    Triple("Paytm", "net.one97.paytm", "Paytm"),
                    Triple("GPay", "com.google.android.apps.nbu.paisa.user", "GPay"),
                    Triple("UPI", null, "All UPI")
                )
                apps.forEach { (appId, pkg, label) ->
                    FilterChip(
                        selected = selectedApp == appId,
                        onClick = {
                            selectedApp = appId
                            val opened = launchUpiPaymentApp(
                                context = context,
                                upiId = paymentConfig.upiId,
                                payeeName = paymentConfig.adminName,
                                amountInr = amountInr,
                                note = title,
                                targetPackage = pkg
                            )
                            if (!opened) {
                                fallbackMessage = strings.paymentAppsFallback
                            } else {
                                fallbackMessage = null
                            }
                        },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (fallbackMessage != null) {
                Text(
                    text = fallbackMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = transactionRef,
                onValueChange = { transactionRef = it },
                label = { Text(strings.transactionRefHint) },
                placeholder = { Text("e.g. PP40928172 or UTR9912") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Payment, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_ref_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(strings.closeBtn)
                }
                Button(
                    onClick = {
                        onConfirmPayment(selectedApp, transactionRef.trim())
                    },
                    enabled = transactionRef.trim().length >= 3,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(50.dp)
                        .testTag("confirm_payment_button")
                ) {
                    Text(strings.confirmPaymentAndSubmit)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostJobSheet(
    defaultCity: String,
    defaultArea: String,
    strings: AppStrings,
    feeAmount: Long,
    onRequestGps: ((city: String, area: String, lat: Double?, lon: Double?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onProceedToPayment: (
        title: String,
        description: String,
        category: String,
        jobType: String,
        wageAmount: Long,
        wagePeriod: String,
        city: String,
        area: String,
        lat: Double?,
        lon: Double?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(JOB_CATEGORIES.first()) }
    var jobType by remember { mutableStateOf("DAILY") }
    var wageText by remember { mutableStateOf("600") }
    var city by remember { mutableStateOf(defaultCity.ifBlank { "Kolkata" }) }
    var area by remember { mutableStateOf(defaultArea) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.postJob,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.postJobFeeNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.closeBtn)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = jobType == "DAILY",
                    onClick = {
                        jobType = "DAILY"
                        if (wageText == "15000") wageText = "600"
                    },
                    label = { Text(strings.dailyJob) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = jobType == "PERMANENT",
                    onClick = {
                        jobType = "PERMANENT"
                        if (wageText == "600") wageText = "15000"
                    },
                    label = { Text(strings.permanentJob) },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(strings.jobTitleHint) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_job_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(strings.jobDescHint) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_job_desc_input")
            )

            Text(text = strings.categoryLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                JOB_CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) }
                    )
                }
            }

            OutlinedTextField(
                value = wageText,
                onValueChange = { wageText = it.filter { ch -> ch.isDigit() } },
                label = {
                    val periodLabel = if (jobType == "DAILY") strings.perDay else strings.perMonth
                    Text("${strings.wageHint} ($periodLabel)")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_job_wage_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(strings.cityLabel) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text(strings.areaLabel) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        onRequestGps { c, a, la, lo ->
                            city = c
                            if (a.isNotBlank()) area = a
                            lat = la
                            lon = lo
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = strings.detectLocation,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                POPULAR_CITIES.forEach { quickCity ->
                    FilterChip(
                        selected = city.equals(quickCity, ignoreCase = true),
                        onClick = { city = quickCity },
                        label = { Text(quickCity) }
                    )
                }
            }

            val wage = wageText.toLongOrNull() ?: 0L
            val isValid = title.trim().length >= 3 &&
                description.trim().length >= 5 &&
                city.trim().length >= 2 &&
                wage in 50L..500000L

            Button(
                onClick = {
                    val period = if (jobType == "DAILY") "PER_DAY" else "PER_MONTH"
                    onProceedToPayment(
                        title.trim(),
                        description.trim(),
                        category,
                        jobType,
                        wage,
                        period,
                        city.trim(),
                        area.trim(),
                        lat,
                        lon
                    )
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("proceed_post_job_payment_button")
            ) {
                Text("${strings.postJob} • ₹$feeAmount")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    existingProfile: UserProfile?,
    defaultName: String,
    language: AppLanguage,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        phoneNumber: String,
        phoneVerified: Boolean,
        role: String,
        city: String,
        area: String,
        skills: String,
        bio: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existingProfile?.displayName?.ifBlank { defaultName } ?: defaultName) }
    var phone by remember { mutableStateOf(existingProfile?.phoneNumber.orEmpty()) }
    var phoneVerified by remember { mutableStateOf(existingProfile?.phoneVerified ?: false) }
    var otpSent by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var generatedOtp by remember { mutableStateOf("1234") }
    var role by remember { mutableStateOf(existingProfile?.role ?: "BOTH") }
    var city by remember { mutableStateOf(existingProfile?.city?.ifBlank { "Kolkata" } ?: "Kolkata") }
    var area by remember { mutableStateOf(existingProfile?.area.orEmpty()) }
    var skills by remember { mutableStateOf(existingProfile?.skills.orEmpty()) }
    var bio by remember { mutableStateOf(existingProfile?.bio.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.editProfile,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "WORKER" to strings.roleWorker,
                    "EMPLOYER" to strings.roleEmployer,
                    "BOTH" to strings.roleBoth
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = role == value,
                        onClick = { role = value },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.fullName) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        val digits = it.filter { ch -> ch.isDigit() }.take(10)
                        if (digits != phone) {
                            phone = digits
                            phoneVerified = false
                            otpSent = false
                        }
                    },
                    label = { Text(strings.mobileNumber) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                    trailingIcon = {
                        if (phoneVerified) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = strings.verifiedBadge,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("profile_phone_input")
                )
                if (!phoneVerified && phone.length == 10) {
                    Button(
                        onClick = {
                            generatedOtp = "1234"
                            otpSent = true
                        },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("OTP")
                    }
                }
            }

            if (otpSent && !phoneVerified) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { otpInput = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text(strings.enterOtpCode) },
                        supportingText = { Text("Demo OTP: $generatedOtp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (otpInput == generatedOtp || otpInput == "1234") {
                                phoneVerified = true
                                otpSent = false
                            }
                        },
                        enabled = otpInput.length == 4
                    ) {
                        Text(strings.verifyBtn)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(strings.cityLabel) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text(strings.areaLabel) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = skills,
                onValueChange = { skills = it },
                label = { Text(strings.skillsLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(strings.bioLabel) },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSave(name.trim(), phone.trim(), phoneVerified, role, city.trim(), area.trim(), skills.trim(), bio.trim())
                },
                enabled = name.trim().length >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_profile_button")
            ) {
                Text(strings.saveProfile)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ReviewDialog(
    targetName: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, comment: String) -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${strings.rateAndReview}: $targetName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$star stars",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(strings.reviewCommentHint) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_comment_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(strings.submitReview)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeBtn)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserReviewsSheet(
    userName: String,
    reviews: List<Review>,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${strings.reviewsTitle} • $userName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            if (reviews.isEmpty()) {
                Text(
                    text = strings.noReviewsYet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                reviews.forEach { review ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(review.reviewerName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "★ ${review.rating}/5",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (review.comment.isNotBlank()) {
                                Text(review.comment, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
