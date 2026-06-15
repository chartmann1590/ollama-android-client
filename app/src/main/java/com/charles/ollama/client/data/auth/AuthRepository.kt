package com.charles.ollama.client.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.charles.ollama.client.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {
    private val auth = FirebaseAuth.getInstance()
    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _currentUser.value = firebaseAuth.currentUser
    }

    init {
        auth.addAuthStateListener(listener)
    }

    suspend fun createAccount(email: String, password: String): FirebaseUser {
        return auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Firebase did not return a user.")
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        return auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Firebase did not return a user.")
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signInWithGoogle(activity: Activity): FirebaseUser {
        val credentialManager = CredentialManager.create(activity)
        val firstAttempt = runCatching {
            requestGoogleIdToken(activity, credentialManager, filterAuthorized = true)
        }
        val idToken = firstAttempt.getOrElse {
            requestGoogleIdToken(activity, credentialManager, filterAuthorized = false)
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(credential).await().user
            ?: error("Firebase did not return a user.")
    }

    fun signOut() {
        auth.signOut()
    }

    private suspend fun requestGoogleIdToken(
        activity: Activity,
        credentialManager: CredentialManager,
        filterAuthorized: Boolean
    ): String {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            .takeIf { it.isNotBlank() }
            ?: error("Google web client ID is not configured. Replace google-services.json after enabling Google sign-in, or set GOOGLE_WEB_CLIENT_ID/google.webClientId.")
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterAuthorized)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(activity, request)
        return GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    }
}
