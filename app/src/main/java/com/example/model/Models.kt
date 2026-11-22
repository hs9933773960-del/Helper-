package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UserRole {
    WORKER, EMPLOYER, BOTH;

    companion object {
        fun safeParse(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: WORKER
    }
}

enum class JobType {
    DAILY, PERMANENT;

    companion object {
        fun safeParse(raw: String?): JobType =
            entries.firstOrNull { it.name == raw } ?: DAILY
    }
}

enum class WagePeriod {
    PER_DAY, PER_MONTH;

    companion object {
        fun safeParse(raw: String?): WagePeriod =
            entries.firstOrNull { it.name == raw } ?: PER_DAY
    }
}

enum class JobStatus {
    OPEN, ACCEPTED, COMPLETED, CANCELLED;

    companion object {
        fun safeParse(raw: String?): JobStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val phoneVerified: Boolean = false,
    val email: String = "",
    val role: String = "WORKER",
    val city: String = "",
    val area: String = "",
    val skills: String = "",
    val bio: String = "",
    val language: String = "EN",
    val blocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = "WORKER",
    val city: String = "",
    val area: String = "",
    val skills: String = "",
    val bio: String = "",
    val phoneVerified: Boolean = false,
    val blocked: Boolean = false,
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val averageRating: Double
        get() = if (ratingCount > 0L) ratingSum.toDouble() / ratingCount.toDouble() else 0.0
}

@Keep
@IgnoreExtraProperties
data class Review(
    @DocumentId val id: String = "",
    val jobId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val targetUserId: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentConfig(
    @DocumentId val id: String = "",
    val adminName: String = "Helper Adda Admin",
    val upiId: String = "helperadda@upi",
    val accountNumber: String = "348900112233",
    val ifscCode: String = "SBIN0001234",
    val phonePeNumber: String = "9830012345",
    val employerBookingFee: Long = 10L,
    val workerAcceptFee: Long = 10L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Job(
    @DocumentId val id: String = "",
    val employerId: String = "",
    val employerName: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Electrician",
    val jobType: String = "DAILY",
    val wageAmount: Long = 500L,
    val wagePeriod: String = "PER_DAY",
    val city: String = "",
    val area: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = "OPEN",
    val bookingFeePaid: Boolean = false,
    val bookingFeeRef: String = "",
    val acceptedWorkerId: String? = null,
    val acceptedWorkerName: String? = null,
    val workerFeePaid: Boolean = false,
    val workerFeeRef: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class JobContact(
    @DocumentId val id: String = "",
    val jobId: String = "",
    val employerId: String = "",
    val employerPhone: String = "",
    val workerId: String? = null,
    val workerPhone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userRole: String = "EMPLOYER",
    val feeType: String = "JOB_POST_FEE",
    val amount: Long = 10L,
    val paymentApp: String = "PhonePe",
    val transactionRef: String = "",
    val jobId: String = "",
    val jobTitle: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val jobId: String = "",
    val jobTitle: String = "",
    val employerId: String = "",
    val employerName: String = "",
    val workerId: String = "",
    val workerName: String = "",
    val memberUids: List<String> = emptyList(),
    val lastMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

val JOB_CATEGORIES = listOf(
    "Electrician",
    "Plumber",
    "Cook / Maid",
    "Driver",
    "Carpenter",
    "Painter",
    "Mason / Helper",
    "Delivery",
    "Shop Staff",
    "Other"
)

val POPULAR_CITIES = listOf(
    "Kolkata",
    "Howrah",
    "Siliguri",
    "Delhi",
    "Mumbai",
    "Patna",
    "Ranchi",
    "Durgapur"
)
