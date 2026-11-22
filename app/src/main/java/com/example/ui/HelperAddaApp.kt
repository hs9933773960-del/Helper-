package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.signOut
import com.example.localization.AppLanguage
import com.example.localization.stringsFor
import com.example.model.ChatThread
import com.example.model.Job
import com.example.model.UiState
import com.example.ui.components.PostJobSheet
import com.example.ui.components.ProfileEditorSheet
import com.example.ui.components.ReviewDialog
import com.example.ui.components.UpiPaymentSheet
import com.example.ui.components.UserReviewsSheet
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.JobsScreen
import com.example.ui.screens.MyWorkScreen
import com.example.ui.screens.ProfileScreen
import com.example.util.fetchCurrentCityAndArea
import com.example.util.launchDialer
import kotlinx.coroutines.launch

private enum class MainTab {
    JOBS, MY_WORK, CHATS, PROFILE, ADMIN
}

private data class PendingJobPostDraft(
    val title: String,
    val description: String,
    val category: String,
    val jobType: String,
    val wageAmount: Long,
    val wagePeriod: String,
    val city: String,
    val area: String,
    val latitude: Double?,
    val longitude: Double?
)

private data class PendingReviewTarget(
    val jobId: String,
    val targetUserId: String,
    val targetName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelperAddaApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val language by viewModel.language.collectAsStateWithLifecycle()
    val strings = remember(language) { stringsFor(language) }
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val publicProfilesState by viewModel.publicProfilesState.collectAsStateWithLifecycle()
    val paymentConfig by viewModel.paymentConfig.collectAsStateWithLifecycle()
    val filteredOpenJobsState by viewModel.filteredOpenJobsState.collectAsStateWithLifecycle()
    val employerJobsState by viewModel.employerJobsState.collectAsStateWithLifecycle()
    val workerJobsState by viewModel.workerJobsState.collectAsStateWithLifecycle()
    val userPaymentsState by viewModel.userPaymentsState.collectAsStateWithLifecycle()
    val userChatsState by viewModel.userChatsState.collectAsStateWithLifecycle()
    val activeChatMessagesState by viewModel.activeChatMessagesState.collectAsStateWithLifecycle()
    val selectedReviewsState by viewModel.selectedUserReviewsState.collectAsStateWithLifecycle()
    val adminJobsState by viewModel.adminJobsState.collectAsStateWithLifecycle()
    val adminPaymentsState by viewModel.adminPaymentsState.collectAsStateWithLifecycle()
    val adminUsersState by viewModel.adminUsersState.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedJobType by viewModel.selectedJobType.collectAsStateWithLifecycle()
    val selectedCity by viewModel.selectedCity.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.JOBS) }
    var activeChatThread by remember { mutableStateOf<ChatThread?>(null) }
    var showPostJobSheet by remember { mutableStateOf(false) }
    var pendingPostDraft by remember { mutableStateOf<PendingJobPostDraft?>(null) }
    var pendingAcceptJob by remember { mutableStateOf<Job?>(null) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var pendingReviewTarget by remember { mutableStateOf<PendingReviewTarget?>(null) }
    var inspectingReviewsName by remember { mutableStateOf<String?>(null) }

    // Pending GPS callback for either search filter or PostJobSheet
    var onGpsResolvedCallback by remember {
        mutableStateOf<((city: String, area: String, lat: Double?, lon: Double?) -> Unit)?>(null)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val res = fetchCurrentCityAndArea(context)
                res.fold(
                    onSuccess = { loc ->
                        onGpsResolvedCallback?.invoke(loc.city, loc.area, loc.latitude, loc.longitude)
                        viewModel.showMessage("Location: ${loc.city}")
                    },
                    onFailure = {
                        viewModel.showMessage(it.localizedMessage ?: "Could not detect GPS location")
                    }
                )
            }
        } else {
            viewModel.showMessage("Location permission denied. Pick a city chip instead.")
        }
    }

    fun requestGpsLocation(callback: (city: String, area: String, lat: Double?, lon: Double?) -> Unit) {
        onGpsResolvedCallback = callback
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            scope.launch {
                val res = fetchCurrentCityAndArea(context)
                res.fold(
                    onSuccess = { loc ->
                        callback(loc.city, loc.area, loc.latitude, loc.longitude)
                        viewModel.showMessage("Location: ${loc.city}")
                    },
                    onFailure = {
                        viewModel.showMessage(it.localizedMessage ?: "Could not detect GPS location")
                    }
                )
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    if (currentTab != MainTab.JOBS && activeChatThread == null) {
        BackHandler { currentTab = MainTab.JOBS }
    }

    val currentUserProfile = (userProfileState as? UiState.Success)?.data
    val publicProfileMap = remember(publicProfilesState) {
        (publicProfilesState as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
    }

    val tabs = remember(isAdmin, strings) {
        buildList {
            add(Triple(MainTab.JOBS, strings.tabJobs, Icons.Default.Work))
            add(Triple(MainTab.MY_WORK, strings.tabMyWork, Icons.Default.Assignment))
            add(Triple(MainTab.CHATS, strings.tabChats, Icons.AutoMirrored.Filled.Chat))
            add(Triple(MainTab.PROFILE, strings.tabProfile, Icons.Default.Person))
            if (isAdmin) {
                add(Triple(MainTab.ADMIN, strings.tabAdmin, Icons.Default.AdminPanelSettings))
            }
        }
    }

    fun callJobCounterparty(jobId: String, callingEmployer: Boolean) {
        scope.launch {
            val res = viewModel.loadJobContact(jobId)
            res.fold(
                onSuccess = { contact ->
                    val phone = if (callingEmployer) contact?.employerPhone else contact?.workerPhone
                    if (phone.isNullOrBlank()) {
                        viewModel.showMessage("Phone number not added yet by counterparty.")
                    } else if (!launchDialer(context, phone)) {
                        viewModel.showMessage("Dialer app unavailable on device.")
                    }
                },
                onFailure = {
                    viewModel.showMessage(it.localizedMessage ?: "Unable to fetch phone number")
                }
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (activeChatThread == null) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Text(
                            text = if (currentTab == MainTab.ADMIN) strings.adminPanelTitle else strings.appName,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        // Quick language toggle pill in top bar
                        val nextLang = when (language) {
                            AppLanguage.ENGLISH -> AppLanguage.HINDI
                            AppLanguage.HINDI -> AppLanguage.BENGALI
                            AppLanguage.BENGALI -> AppLanguage.ENGLISH
                        }
                        TextButton(
                            onClick = { viewModel.setLanguage(nextLang) },
                            modifier = Modifier.testTag("topbar_language_toggle")
                        ) {
                            Text(
                                text = language.nativeLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == MainTab.JOBS && activeChatThread == null) {
                FloatingActionButton(
                    onClick = { showPostJobSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_post_job")
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.postJob)
                }
            }
        },
        bottomBar = {
            if (activeChatThread == null) {
                BoxWithConstraints {
                    if (maxWidth < 600.dp) {
                        NavigationBar {
                            tabs.forEach { (tab, label, icon) ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val useRail = maxWidth >= 600.dp && activeChatThread == null
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail) {
                    NavigationRail {
                        tabs.forEach { (tab, label, icon) ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (currentUserProfile?.blocked == true && !isAdmin) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                        ) {
                            Text(
                                text = "Your account has been suspended by Admin.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    } else if (activeChatThread != null) {
                        val chat = activeChatThread!!
                        ChatRoomScreen(
                            chat = chat,
                            messagesState = activeChatMessagesState,
                            currentUserId = viewModel.currentUserId,
                            strings = strings,
                            onBack = {
                                activeChatThread = null
                                viewModel.openChat(null)
                            },
                            onCallClick = {
                                val callingEmployer = chat.employerId != viewModel.currentUserId
                                callJobCounterparty(chat.jobId, callingEmployer = callingEmployer)
                            },
                            onSendMessage = { text ->
                                viewModel.sendChatMessage(chat, text)
                            }
                        )
                    } else {
                        when (currentTab) {
                            MainTab.JOBS -> JobsScreen(
                                jobsState = filteredOpenJobsState,
                                publicProfiles = publicProfileMap,
                                currentUserId = viewModel.currentUserId,
                                searchQuery = searchQuery,
                                selectedJobType = selectedJobType,
                                selectedCity = selectedCity,
                                selectedCategory = selectedCategory,
                                paymentConfig = paymentConfig,
                                strings = strings,
                                onSearchChange = viewModel::setSearchQuery,
                                onJobTypeChange = viewModel::setSelectedJobType,
                                onCityChange = viewModel::setSelectedCity,
                                onCategoryChange = viewModel::setSelectedCategory,
                                onDetectGpsClick = {
                                    requestGpsLocation { city, _, _, _ ->
                                        viewModel.setSelectedCity(city)
                                    }
                                },
                                onAcceptJobClick = { job ->
                                    pendingAcceptJob = job
                                },
                                onInspectEmployerReviews = { uid, name ->
                                    inspectingReviewsName = name
                                    viewModel.inspectUserReviews(uid)
                                },
                                onOpenPostJobClick = { showPostJobSheet = true }
                            )

                            MainTab.MY_WORK -> MyWorkScreen(
                                employerJobsState = employerJobsState,
                                workerJobsState = workerJobsState,
                                strings = strings,
                                onOpenChat = { jobId ->
                                    val chats = (userChatsState as? UiState.Success)?.data.orEmpty()
                                    val found = chats.firstOrNull { it.jobId == jobId || it.id == jobId }
                                    if (found != null) {
                                        activeChatThread = found
                                        viewModel.openChat(found.id)
                                    } else {
                                        currentTab = MainTab.CHATS
                                    }
                                },
                                onCallCounterparty = { job, isEmployerView ->
                                    callJobCounterparty(job.id, callingEmployer = !isEmployerView)
                                },
                                onMarkCompleted = { jobId ->
                                    viewModel.updateJobStatus(jobId, "COMPLETED")
                                },
                                onCancelJob = { jobId ->
                                    viewModel.updateJobStatus(jobId, "CANCELLED")
                                },
                                onDeleteJob = { jobId ->
                                    viewModel.deleteJob(jobId)
                                },
                                onRateUser = { jobId, targetUid, targetName ->
                                    pendingReviewTarget = PendingReviewTarget(jobId, targetUid, targetName)
                                }
                            )

                            MainTab.CHATS -> ChatListScreen(
                                chatsState = userChatsState,
                                currentUserId = viewModel.currentUserId,
                                strings = strings,
                                onSelectChat = { chat ->
                                    activeChatThread = chat
                                    viewModel.openChat(chat.id)
                                }
                            )

                            MainTab.PROFILE -> ProfileScreen(
                                profileState = userProfileState,
                                publicProfile = publicProfileMap[viewModel.currentUserId],
                                paymentsState = userPaymentsState,
                                currentUserName = viewModel.currentUserName,
                                currentUserEmail = viewModel.currentUserEmail,
                                language = language,
                                strings = strings,
                                onLanguageChange = viewModel::setLanguage,
                                onEditProfileClick = { showProfileEditor = true },
                                onViewMyReviewsClick = {
                                    inspectingReviewsName = currentUserProfile?.displayName ?: viewModel.currentUserName
                                    viewModel.inspectUserReviews(viewModel.currentUserId)
                                },
                                onSignOutClick = {
                                    signOut(credentialManager, scope)
                                }
                            )

                            MainTab.ADMIN -> if (isAdmin) {
                                AdminPanelScreen(
                                    paymentConfig = paymentConfig,
                                    allJobsState = adminJobsState,
                                    allPaymentsState = adminPaymentsState,
                                    allUsersState = adminUsersState,
                                    strings = strings,
                                    onSavePaymentConfig = viewModel::savePaymentConfig,
                                    onUpdatePaymentStatus = viewModel::updatePaymentStatus,
                                    onUpdateJobStatus = viewModel::updateJobStatus,
                                    onDeleteJob = viewModel::deleteJob,
                                    onToggleBlockUser = viewModel::toggleUserBlock
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPostJobSheet) {
        PostJobSheet(
            defaultCity = currentUserProfile?.city.orEmpty(),
            defaultArea = currentUserProfile?.area.orEmpty(),
            strings = strings,
            feeAmount = paymentConfig.employerBookingFee,
            onRequestGps = { callback -> requestGpsLocation(callback) },
            onDismiss = { showPostJobSheet = false },
            onProceedToPayment = { title, desc, cat, type, wage, period, city, area, lat, lon ->
                showPostJobSheet = false
                pendingPostDraft = PendingJobPostDraft(
                    title, desc, cat, type, wage, period, city, area, lat, lon
                )
            }
        )
    }

    if (pendingPostDraft != null) {
        val draft = pendingPostDraft!!
        UpiPaymentSheet(
            title = strings.payOnlineTitle,
            feeNote = strings.postJobFeeNote,
            amountInr = paymentConfig.employerBookingFee,
            paymentConfig = paymentConfig,
            strings = strings,
            onDismiss = { pendingPostDraft = null },
            onConfirmPayment = { app, ref ->
                viewModel.postJobWithFee(
                    title = draft.title,
                    description = draft.description,
                    category = draft.category,
                    jobType = draft.jobType,
                    wageAmount = draft.wageAmount,
                    wagePeriod = draft.wagePeriod,
                    city = draft.city,
                    area = draft.area,
                    latitude = draft.latitude,
                    longitude = draft.longitude,
                    paymentApp = app,
                    transactionRef = ref,
                    onSuccess = { pendingPostDraft = null }
                )
            }
        )
    }

    if (pendingAcceptJob != null) {
        val job = pendingAcceptJob!!
        UpiPaymentSheet(
            title = strings.payOnlineTitle,
            feeNote = strings.acceptFeeNote,
            amountInr = paymentConfig.workerAcceptFee,
            paymentConfig = paymentConfig,
            strings = strings,
            onDismiss = { pendingAcceptJob = null },
            onConfirmPayment = { app, ref ->
                viewModel.acceptJobWithFee(
                    job = job,
                    paymentApp = app,
                    transactionRef = ref,
                    onSuccess = {
                        pendingAcceptJob = null
                        currentTab = MainTab.MY_WORK
                    }
                )
            }
        )
    }

    if (showProfileEditor) {
        ProfileEditorSheet(
            existingProfile = currentUserProfile,
            defaultName = viewModel.currentUserName,
            language = language,
            strings = strings,
            onDismiss = { showProfileEditor = false },
            onSave = { name, phone, verified, role, city, area, skills, bio ->
                viewModel.saveUserProfile(
                    displayName = name,
                    phoneNumber = phone,
                    phoneVerified = verified,
                    role = role,
                    city = city,
                    area = area,
                    skills = skills,
                    bio = bio,
                    languageCode = language.code,
                    onSuccess = { showProfileEditor = false }
                )
            }
        )
    }

    if (pendingReviewTarget != null) {
        val target = pendingReviewTarget!!
        ReviewDialog(
            targetName = target.targetName,
            strings = strings,
            onDismiss = { pendingReviewTarget = null },
            onSubmit = { rating, comment ->
                viewModel.submitReview(
                    jobId = target.jobId,
                    targetUserId = target.targetUserId,
                    rating = rating,
                    comment = comment,
                    onSuccess = { pendingReviewTarget = null }
                )
            }
        )
    }

    if (inspectingReviewsName != null) {
        val reviews = (selectedReviewsState as? UiState.Success)?.data.orEmpty()
        UserReviewsSheet(
            userName = inspectingReviewsName!!,
            reviews = reviews,
            strings = strings,
            onDismiss = {
                inspectingReviewsName = null
                viewModel.inspectUserReviews(null)
            }
        )
    }
}
