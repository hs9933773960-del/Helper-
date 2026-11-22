package com.example.data

import android.content.Context
import com.example.R
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.Job
import com.example.model.JobContact
import com.example.model.PaymentConfig
import com.example.model.PaymentRecord
import com.example.model.PublicProfile
import com.example.model.Review
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class HelperAddaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.guardStream(op: OperationType, path: String, uid: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(exceptionToLog, op, path)
            }
            throw error
        }

    fun observeUserProfile(uid: String): Flow<UserProfile?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .guardStream(OperationType.GET, "users/$uid", uid)

    fun observeActivePublicProfiles(uid: String): Flow<List<PublicProfile>> =
        db.collection("public_profiles")
            .whereEqualTo("blocked", false)
            .snapshots()
            .map { snap ->
                snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .guardStream(OperationType.LIST, "public_profiles", uid)

    fun observeReviews(targetUserId: String, currentUid: String): Flow<List<Review>> =
        db.collection("public_profiles").document(targetUserId)
            .collection("reviews")
            .snapshots()
            .map { snap ->
                snap.toObjects(Review::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "public_profiles/$targetUserId/reviews", currentUid)

    fun observePaymentConfig(uid: String): Flow<PaymentConfig> =
        db.collection("app_config").document("payments")
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(PaymentConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: PaymentConfig()
                } else {
                    PaymentConfig()
                }
            }
            .guardStream(OperationType.GET, "app_config/payments", uid)

    fun observeOpenJobs(uid: String): Flow<List<Job>> =
        db.collection("jobs")
            .whereEqualTo("status", "OPEN")
            .snapshots()
            .map { snap ->
                snap.toObjects(Job::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "jobs", uid)

    fun observeEmployerJobs(uid: String): Flow<List<Job>> =
        db.collection("jobs")
            .whereEqualTo("employerId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Job::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "jobs", uid)

    fun observeWorkerAcceptedJobs(uid: String): Flow<List<Job>> =
        db.collection("jobs")
            .whereEqualTo("acceptedWorkerId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Job::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "jobs", uid)

    fun observeUserPayments(uid: String): Flow<List<PaymentRecord>> =
        db.collection("payments")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "payments", uid)

    fun observeUserChats(uid: String): Flow<List<ChatThread>> =
        db.collection("chats")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "chats", uid)

    fun observeChatMessages(chatId: String, uid: String): Flow<List<ChatMessage>> =
        db.collection("chats").document(chatId)
            .collection("messages")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp.now() }
            }
            .guardStream(OperationType.LIST, "chats/$chatId/messages", uid)

    fun observeAllJobsForAdmin(uid: String): Flow<List<Job>> =
        db.collection("jobs")
            .snapshots()
            .map { snap ->
                snap.toObjects(Job::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "jobs", uid)

    fun observeAllPaymentsForAdmin(uid: String): Flow<List<PaymentRecord>> =
        db.collection("payments")
            .snapshots()
            .map { snap ->
                snap.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "payments", uid)

    fun observeAllUsersForAdmin(uid: String): Flow<List<UserProfile>> =
        db.collection("users")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .guardStream(OperationType.LIST, "users", uid)

    suspend fun checkIsAdminDoc(uid: String): Boolean = try {
        val snap = db.collection("admins").document(uid).get().await()
        snap.exists()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    suspend fun getJobContact(jobId: String): Result<JobContact?> = try {
        requireUserId()
        val snap = db.collection("job_contacts").document(jobId).get().await()
        val contact = if (snap.exists()) {
            snap.toObject(JobContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else null
        Result.success(contact)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, "job_contacts/$jobId")
        Result.failure(e)
    }

    suspend fun saveUserProfile(
        displayName: String,
        phoneNumber: String,
        phoneVerified: Boolean,
        email: String,
        role: String,
        city: String,
        area: String,
        skills: String,
        bio: String,
        language: String
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val safeName = displayName.trim().let { if (it.length < 2) "Helper User" else it.take(60) }
            val safePhone = phoneNumber.trim().take(20)
            val safeEmail = email.trim().take(100)
            val safeRole = if (role in listOf("WORKER", "EMPLOYER", "BOTH")) role else "WORKER"
            val safeCity = city.trim().take(60)
            val safeArea = area.trim().take(80)
            val safeSkills = skills.trim().take(120)
            val safeBio = bio.trim().take(240)
            val safeLang = if (language in listOf("EN", "HI", "BN")) language else "EN"

            val userRef = db.collection("users").document(uid)
            val pubRef = db.collection("public_profiles").document(uid)
            path = userRef.path

            val existingUser = userRef.get().await()
            val existingPub = pubRef.get().await()
            val batch = db.batch()

            if (!existingUser.exists()) {
                batch.set(
                    userRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "phoneNumber" to safePhone,
                        "phoneVerified" to phoneVerified,
                        "email" to safeEmail,
                        "role" to safeRole,
                        "city" to safeCity,
                        "area" to safeArea,
                        "skills" to safeSkills,
                        "bio" to safeBio,
                        "language" to safeLang,
                        "blocked" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    userRef,
                    mapOf(
                        "displayName" to safeName,
                        "phoneNumber" to safePhone,
                        "phoneVerified" to phoneVerified,
                        "email" to safeEmail,
                        "role" to safeRole,
                        "city" to safeCity,
                        "area" to safeArea,
                        "skills" to safeSkills,
                        "bio" to safeBio,
                        "language" to safeLang,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (!existingPub.exists()) {
                batch.set(
                    pubRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "role" to safeRole,
                        "city" to safeCity,
                        "area" to safeArea,
                        "skills" to safeSkills,
                        "bio" to safeBio,
                        "phoneVerified" to phoneVerified,
                        "blocked" to false,
                        "ratingSum" to 0L,
                        "ratingCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    pubRef,
                    mapOf(
                        "displayName" to safeName,
                        "role" to safeRole,
                        "city" to safeCity,
                        "area" to safeArea,
                        "skills" to safeSkills,
                        "bio" to safeBio,
                        "phoneVerified" to phoneVerified,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun postJobWithBookingFee(
        employerName: String,
        employerPhone: String,
        title: String,
        description: String,
        category: String,
        jobType: String,
        wageAmount: Long,
        wagePeriod: String,
        city: String,
        area: String,
        latitude: Double?,
        longitude: Double?,
        feeAmount: Long,
        paymentApp: String,
        transactionRef: String
    ): Result<String> {
        val jobId = "job_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val payId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "jobs/$jobId"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(80)
            val cleanDesc = description.trim().take(400)
            val cleanCity = city.trim().take(60)
            val cleanRef = transactionRef.trim().take(80)
            require(cleanTitle.length >= 3) { "Job title must be at least 3 characters." }
            require(cleanDesc.length >= 5) { "Description must be at least 5 characters." }
            require(cleanCity.length >= 2) { "City must be at least 2 characters." }
            require(wageAmount in 50L..500000L) { "Wage must be between ₹50 and ₹5,00,000." }
            require(cleanRef.length >= 3) { "Payment reference must be at least 3 characters." }

            val safeName = employerName.trim().let { if (it.length < 2) "Employer" else it.take(60) }
            val safeJobType = if (jobType in listOf("DAILY", "PERMANENT")) jobType else "DAILY"
            val safePeriod = if (wagePeriod in listOf("PER_DAY", "PER_MONTH")) wagePeriod else "PER_DAY"
            val safeApp = if (paymentApp in listOf("PhonePe", "Paytm", "GPay", "UPI")) paymentApp else "UPI"

            @Suppress("UNCHECKED_CAST")
            val jobPayload = mapOf(
                "employerId" to uid,
                "employerName" to safeName,
                "title" to cleanTitle,
                "description" to cleanDesc,
                "category" to category.trim().ifBlank { "Other" }.take(40),
                "jobType" to safeJobType,
                "wageAmount" to wageAmount,
                "wagePeriod" to safePeriod,
                "city" to cleanCity,
                "area" to area.trim().take(80),
                "latitude" to latitude,
                "longitude" to longitude,
                "status" to "OPEN",
                "bookingFeePaid" to true,
                "bookingFeeRef" to cleanRef,
                "workerFeePaid" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val contactPayload = mapOf(
                "jobId" to jobId,
                "employerId" to uid,
                "employerPhone" to employerPhone.trim().take(20),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val paymentPayload = mapOf(
                "userId" to uid,
                "userName" to safeName,
                "userRole" to "EMPLOYER",
                "feeType" to "JOB_POST_FEE",
                "amount" to feeAmount.coerceIn(0L, 1000L),
                "paymentApp" to safeApp,
                "transactionRef" to cleanRef,
                "jobId" to jobId,
                "jobTitle" to cleanTitle,
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(db.collection("jobs").document(jobId), jobPayload)
            batch.set(db.collection("job_contacts").document(jobId), contactPayload)
            batch.set(db.collection("payments").document(payId), paymentPayload)
            batch.commit().await()

            Result.success(jobId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptJobWithFee(
        job: Job,
        workerName: String,
        workerPhone: String,
        feeAmount: Long,
        paymentApp: String,
        transactionRef: String
    ): Result<Unit> {
        val jobId = job.id
        val payId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "jobs/$jobId"
        return try {
            val uid = requireUserId()
            require(jobId.isNotBlank()) { "Job ID cannot be blank." }
            require(job.employerId != uid) { "You cannot accept your own posted job." }
            val cleanRef = transactionRef.trim().take(80)
            require(cleanRef.length >= 3) { "Payment reference must be at least 3 characters." }

            val safeWorkerName = workerName.trim().let { if (it.isEmpty()) "Worker" else it.take(60) }
            val safeApp = if (paymentApp in listOf("PhonePe", "Paytm", "GPay", "UPI")) paymentApp else "UPI"

            val batch = db.batch()
            batch.update(
                db.collection("jobs").document(jobId),
                mapOf(
                    "status" to "ACCEPTED",
                    "acceptedWorkerId" to uid,
                    "acceptedWorkerName" to safeWorkerName,
                    "workerFeePaid" to true,
                    "workerFeeRef" to cleanRef,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("job_contacts").document(jobId),
                mapOf(
                    "workerId" to uid,
                    "workerPhone" to workerPhone.trim().take(20),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("chats").document(jobId),
                mapOf(
                    "jobId" to jobId,
                    "jobTitle" to job.title.take(80),
                    "employerId" to job.employerId,
                    "employerName" to job.employerName.take(60),
                    "workerId" to uid,
                    "workerName" to safeWorkerName,
                    "memberUids" to listOf(job.employerId, uid),
                    "lastMessage" to "Booking accepted",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("payments").document(payId),
                mapOf(
                    "userId" to uid,
                    "userName" to safeWorkerName,
                    "userRole" to "WORKER",
                    "feeType" to "JOB_ACCEPT_FEE",
                    "amount" to feeAmount.coerceIn(0L, 1000L),
                    "paymentApp" to safeApp,
                    "transactionRef" to cleanRef,
                    "jobId" to jobId,
                    "jobTitle" to job.title.take(80),
                    "status" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateJobStatus(jobId: String, newStatus: String): Result<Unit> {
        val path = "jobs/$jobId"
        return try {
            requireUserId()
            require(newStatus in listOf("OPEN", "ACCEPTED", "COMPLETED", "CANCELLED"))
            db.collection("jobs").document(jobId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteJob(jobId: String): Result<Unit> {
        val path = "jobs/$jobId"
        return try {
            requireUserId()
            db.collection("jobs").document(jobId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun sendChatMessage(
        chat: ChatThread,
        senderName: String,
        text: String
    ): Result<Unit> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "chats/${chat.id}/messages/$msgId"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(500)
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }
            val safeSender = senderName.trim().ifEmpty { "User" }.take(60)

            val batch = db.batch()
            batch.set(
                db.collection("chats").document(chat.id).collection("messages").document(msgId),
                mapOf(
                    "chatId" to chat.id,
                    "senderId" to uid,
                    "senderName" to safeSender,
                    "memberUids" to chat.memberUids,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("chats").document(chat.id),
                mapOf(
                    "lastMessage" to cleanText.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReview(
        jobId: String,
        reviewerName: String,
        targetUserId: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        val path = "public_profiles/$targetUserId/reviews"
        return try {
            val uid = requireUserId()
            require(targetUserId != uid) { "Cannot review yourself." }
            val safeRating = rating.coerceIn(1, 5).toLong()
            val reviewId = "${jobId}_$uid"
            val batch = db.batch()
            batch.set(
                db.collection("public_profiles").document(targetUserId)
                    .collection("reviews").document(reviewId),
                mapOf(
                    "jobId" to jobId,
                    "reviewerId" to uid,
                    "reviewerName" to reviewerName.trim().ifEmpty { "User" }.take(60),
                    "targetUserId" to targetUserId,
                    "rating" to safeRating,
                    "comment" to comment.trim().take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("public_profiles").document(targetUserId),
                mapOf(
                    "ratingSum" to FieldValue.increment(safeRating),
                    "ratingCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun savePaymentConfig(config: PaymentConfig): Result<Unit> {
        val path = "app_config/payments"
        return try {
            requireUserId()
            val cleanAdminName = config.adminName.trim().let { if (it.length < 2) "Helper Adda Admin" else it.take(80) }
            val cleanUpi = config.upiId.trim().let { if (it.length < 3) "helperadda@upi" else it.take(80) }
            val cleanAcc = config.accountNumber.trim().let { if (it.length < 4) "348900112233" else it.take(40) }
            db.collection("app_config").document("payments")
                .set(
                    mapOf(
                        "adminName" to cleanAdminName,
                        "upiId" to cleanUpi,
                        "accountNumber" to cleanAcc,
                        "ifscCode" to config.ifscCode.trim().take(20),
                        "phonePeNumber" to config.phonePeNumber.trim().take(20),
                        "employerBookingFee" to config.employerBookingFee.coerceIn(0L, 1000L),
                        "workerAcceptFee" to config.workerAcceptFee.coerceIn(0L, 1000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePaymentStatus(paymentId: String, status: String): Result<Unit> {
        val path = "payments/$paymentId"
        return try {
            requireUserId()
            require(status in listOf("PENDING", "VERIFIED", "REJECTED"))
            db.collection("payments").document(paymentId)
                .update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setUserBlocked(targetUid: String, blocked: Boolean): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            val batch = db.batch()
            batch.update(
                db.collection("users").document(targetUid),
                mapOf(
                    "blocked" to blocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("public_profiles").document(targetUid),
                mapOf(
                    "blocked" to blocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
