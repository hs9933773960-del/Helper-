package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.HelperAddaRepository
import com.example.localization.AppLanguage
import com.example.localization.LanguagePreferences
import com.example.ui.HelperAddaApp
import com.example.ui.MainViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.HelperAddaTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelperAddaTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val languagePrefs = remember(context) { LanguagePreferences(context.applicationContext) }
    val selectedLanguage by languagePrefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.ENGLISH)

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            selectedLanguage = selectedLanguage,
            onLanguageChange = { lang ->
                scope.launch { languagePrefs.setLanguage(lang) }
            },
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = user.uid
        val displayName = user.displayName.orEmpty().ifBlank { "Helper User" }
        val email = user.email.orEmpty()
        val emailVerified = user.isEmailVerified

        key(uid) {
            val viewModel: MainViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        MainViewModel(
                            repository = HelperAddaRepository(db),
                            languagePrefs = LanguagePreferences(app),
                            currentUserId = uid,
                            currentUserName = displayName,
                            currentUserEmail = email,
                            isEmailVerified = emailVerified
                        )
                    }
                }
            )
            HelperAddaApp(viewModel = viewModel)
        }
    }
}
