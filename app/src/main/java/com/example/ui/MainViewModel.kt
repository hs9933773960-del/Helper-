package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HelperAddaRepository
import com.example.localization.AppLanguage
import com.example.localization.LanguagePreferences
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.Job
import com.example.model.JobContact
import com.example.model.PaymentConfig
import com.example.model.PaymentRecord
import com.example.model.PublicProfile
import com.example.model.Review
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: HelperAddaRepository,
    private val languagePrefs: LanguagePreferences,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String,
    isEmailVerified: Boolean
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val BOOTSTRAPPED_ADMIN_EMAIL = "sktauseef938433@gmail.com"
    }

    private fun <T> Flow<T>.toUiState(defaultError: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, defaultError, rootError)
                emit(UiState.Error(rootError.message ?: defaultError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _isAdmin = MutableStateFlow(
        currentUserEmail.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) && isEmailVerified
    )
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    val language: StateFlow<AppLanguage> = languagePrefs.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppLanguage.ENGLISH)

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiState("Failed to load profile")

    val publicProfilesState: StateFlow<UiState<List<PublicProfile>>> =
        repository.observeActivePublicProfiles(currentUserId)
            .toUiState("Failed to load profiles")

    val paymentConfig: StateFlow<PaymentConfig> =
        repository.observePaymentConfig(currentUserId)
            .catch { emit(PaymentConfig()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PaymentConfig())

    val openJobsState: StateFlow<UiState<List<Job>>> =
        repository.observeOpenJobs(currentUserId)
            .toUiState("Failed to load open jobs")

    val employerJobsState: StateFlow<UiState<List<Job>>> =
        repository.observeEmployerJobs(currentUserId)
            .toUiState("Failed to load posted jobs")

    val workerJobsState: StateFlow<UiState<List<Job>>> =
        repository.observeWorkerAcceptedJobs(currentUserId)
            .toUiState("Failed to load accepted jobs")

    val userPaymentsState: StateFlow<UiState<List<PaymentRecord>>> =
        repository.observeUserPayments(currentUserId)
            .toUiState("Failed to load payments")

    val userChatsState: StateFlow<UiState<List<ChatThread>>> =
        repository.observeUserChats(currentUserId)
            .toUiState("Failed to load chats")

    val adminJobsState: StateFlow<UiState<List<Job>>> =
        _isAdmin.flatMapLatest { admin ->
            if (admin) repository.observeAllJobsForAdmin(currentUserId) else flowOf(emptyList())
        }.toUiState("Failed to load all jobs")

    val adminPaymentsState: StateFlow<UiState<List<PaymentRecord>>> =
        _isAdmin.flatMapLatest { admin ->
            if (admin) repository.observeAllPaymentsForAdmin(currentUserId) else flowOf(emptyList())
        }.toUiState("Failed to load all payments")

    val adminUsersState: StateFlow<UiState<List<UserProfile>>> =
        _isAdmin.flatMapLatest { admin ->
            if (admin) repository.observeAllUsersForAdmin(currentUserId) else flowOf(emptyList())
        }.toUiState("Failed to load users")

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        _activeChatId.flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeChatMessages(chatId, currentUserId)
        }.toUiState("Failed to load messages")

    private val _selectedReviewUserId = MutableStateFlow<String?>(null)
    val selectedUserReviewsState: StateFlow<UiState<List<Review>>> =
        _selectedReviewUserId.flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else repository.observeReviews(uid, currentUserId)
        }.toUiState("Failed to load reviews")

    // Job browsing filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedJobType = MutableStateFlow("ALL") // ALL, DAILY, PERMANENT
    val selectedJobType: StateFlow<String> = _selectedJobType.asStateFlow()

    private val _selectedCity = MutableStateFlow("")
    val selectedCity: StateFlow<String> = _selectedCity.asStateFlow()

    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val filteredOpenJobsState: StateFlow<UiState<List<Job>>> = combine(
        openJobsState,
        _searchQuery,
        _selectedJobType,
        _selectedCity,
        _selectedCategory
    ) { state, query, type, city, category ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val q = query.trim().lowercase()
                val c = city.trim().lowercase()
                val filtered = state.data.filter { job ->
                    val matchesType = type == "ALL" || job.jobType == type
                    val matchesCity = c.isEmpty() ||
                        job.city.lowercase().contains(c) ||
                        job.area.lowercase().contains(c)
                    val matchesCategory = category == "ALL" || job.category.equals(category, ignoreCase = true)
                    val matchesQuery = q.isEmpty() ||
                        job.title.lowercase().contains(q) ||
                        job.description.lowercase().contains(q) ||
                        job.category.lowercase().contains(q) ||
                        job.city.lowercase().contains(q) ||
                        job.area.lowercase().contains(q)
                    matchesType && matchesCity && matchesCategory && matchesQuery
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            if (!_isAdmin.value) {
                val hasDoc = repository.checkIsAdminDoc(currentUserId)
                if (hasDoc) _isAdmin.value = true
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun showMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            languagePrefs.setLanguage(lang)
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setSelectedJobType(type: String) {
        _selectedJobType.value = type
    }

    fun setSelectedCity(city: String) {
        _selectedCity.value = city
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun openChat(chatId: String?) {
        _activeChatId.value = chatId
    }

    fun inspectUserReviews(targetUid: String?) {
        _selectedReviewUserId.value = targetUid
    }

    fun saveUserProfile(
        displayName: String,
        phoneNumber: String,
        phoneVerified: Boolean,
        role: String,
        city: String,
        area: String,
        skills: String,
        bio: String,
        languageCode: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveUserProfile(
                displayName = displayName,
                phoneNumber = phoneNumber,
                phoneVerified = phoneVerified,
                email = currentUserEmail,
                role = role,
                city = city,
                area = area,
                skills = skills,
                bio = bio,
                language = languageCode
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Profile saved"
                    onSuccess()
                },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to save profile" }
            )
        }
    }

    fun postJobWithFee(
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
        paymentApp: String,
        transactionRef: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = (userProfileState.value as? UiState.Success)?.data
            val employerName = profile?.displayName?.ifBlank { currentUserName } ?: currentUserName
            val employerPhone = profile?.phoneNumber.orEmpty()
            val feeAmount = paymentConfig.value.employerBookingFee
            val result = repository.postJobWithBookingFee(
                employerName = employerName,
                employerPhone = employerPhone,
                title = title,
                description = description,
                category = category,
                jobType = jobType,
                wageAmount = wageAmount,
                wagePeriod = wagePeriod,
                city = city,
                area = area,
                latitude = latitude,
                longitude = longitude,
                feeAmount = feeAmount,
                paymentApp = paymentApp,
                transactionRef = transactionRef
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Job posted successfully!"
                    onSuccess()
                },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to post job" }
            )
        }
    }

    fun acceptJobWithFee(
        job: Job,
        paymentApp: String,
        transactionRef: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = (userProfileState.value as? UiState.Success)?.data
            val workerName = profile?.displayName?.ifBlank { currentUserName } ?: currentUserName
            val workerPhone = profile?.phoneNumber.orEmpty()
            val feeAmount = paymentConfig.value.workerAcceptFee
            val result = repository.acceptJobWithFee(
                job = job,
                workerName = workerName,
                workerPhone = workerPhone,
                feeAmount = feeAmount,
                paymentApp = paymentApp,
                transactionRef = transactionRef
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Job accepted! Chat & Call unlocked."
                    onSuccess()
                },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to accept job" }
            )
        }
    }

    fun updateJobStatus(jobId: String, newStatus: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateJobStatus(jobId, newStatus)
            _isBusy.value = false
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update job" }
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteJob(jobId)
            _isBusy.value = false
            res.fold(
                onSuccess = { _actionMessage.value = "Job deleted" },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to delete job" }
            )
        }
    }

    suspend fun loadJobContact(jobId: String): Result<JobContact?> =
        repository.getJobContact(jobId)

    fun sendChatMessage(chat: ChatThread, text: String) {
        viewModelScope.launch {
            val profile = (userProfileState.value as? UiState.Success)?.data
            val senderName = profile?.displayName?.ifBlank { currentUserName } ?: currentUserName
            val res = repository.sendChatMessage(chat, senderName, text)
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to send message" }
        }
    }

    fun submitReview(
        jobId: String,
        targetUserId: String,
        rating: Int,
        comment: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val profile = (userProfileState.value as? UiState.Success)?.data
            val reviewerName = profile?.displayName?.ifBlank { currentUserName } ?: currentUserName
            val res = repository.submitReview(jobId, reviewerName, targetUserId, rating, comment)
            _isBusy.value = false
            res.fold(
                onSuccess = {
                    _actionMessage.value = "Thank you for your review!"
                    onSuccess()
                },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Review already submitted or failed" }
            )
        }
    }

    fun savePaymentConfig(config: PaymentConfig) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.savePaymentConfig(config)
            _isBusy.value = false
            res.fold(
                onSuccess = { _actionMessage.value = "Admin payment settings saved" },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to save settings" }
            )
        }
    }

    fun updatePaymentStatus(paymentId: String, status: String) {
        viewModelScope.launch {
            val res = repository.updatePaymentStatus(paymentId, status)
            res.fold(
                onSuccess = { _actionMessage.value = "Payment marked $status" },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to update payment" }
            )
        }
    }

    fun toggleUserBlock(targetUid: String, blocked: Boolean) {
        viewModelScope.launch {
            val res = repository.setUserBlocked(targetUid, blocked)
            res.fold(
                onSuccess = { _actionMessage.value = if (blocked) "User blocked" else "User unblocked" },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Action failed" }
            )
        }
    }
}
