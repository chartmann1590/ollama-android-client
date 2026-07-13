package com.charles.ollama.client.ui.settings

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.ads.AdConsentManager
import com.charles.ollama.client.ads.AdGate
import com.charles.ollama.client.data.auth.AuthRepository
import com.charles.ollama.client.data.billing.PremiumManager
import com.charles.ollama.client.data.litert.LitertPreferences
import com.charles.ollama.client.data.preferences.ThemeMode
import com.charles.ollama.client.data.preferences.UiPreferences
import com.charles.ollama.client.data.preferences.LocalBugReport
import com.charles.ollama.client.data.repository.GitHubFeedbackRepository
import com.charles.ollama.client.data.api.dto.GitHubCommentResponse
import com.charles.ollama.client.data.sync.RealtimeChatSyncRepository
import com.charles.ollama.client.data.sync.SyncPreferences
import com.charles.ollama.client.data.translation.AppLanguage
import com.charles.ollama.client.data.translation.TranslationRepository
import com.charles.ollama.client.data.translation.TranslationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SubmitState {
    object Idle : SubmitState
    object Loading : SubmitState
    data class Success(val report: LocalBugReport) : SubmitState
    data class Error(val message: String) : SubmitState
}

sealed interface CommentsLoadState {
    object Idle : CommentsLoadState
    object Loading : CommentsLoadState
    object Success : CommentsLoadState
    data class Error(val message: String) : CommentsLoadState
}

sealed interface CommentPostState {
    object Idle : CommentPostState
    object Posting : CommentPostState
    object Success : CommentPostState
    data class Error(val message: String) : CommentPostState
}

sealed interface AuthActionState {
    object Idle : AuthActionState
    object Loading : AuthActionState
    data class Success(val message: String) : AuthActionState
    data class Error(val message: String) : AuthActionState
}

data class AccountUiState(
    val uid: String? = null,
    val email: String? = null,
    val providerIds: List<String> = emptyList(),
    val syncEnabled: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastSyncError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val litertPreferences: LitertPreferences,
    private val adGate: AdGate,
    private val uiPreferences: UiPreferences,
    private val feedbackRepository: GitHubFeedbackRepository,
    private val authRepository: AuthRepository,
    private val syncPreferences: SyncPreferences,
    private val premiumManager: PremiumManager,
    private val realtimeChatSyncRepository: RealtimeChatSyncRepository,
    private val adConsentManager: AdConsentManager,
    private val translationRepository: TranslationRepository
) : ViewModel() {

    /** Whether to surface the "Ad privacy options" entry (UMP-required regions). */
    val adPrivacyOptionsRequired: Boolean
        get() = adConsentManager.isPrivacyOptionsRequired

    /** Re-open the UMP privacy options form so users can change ad consent. */
    fun showAdPrivacyOptions(activity: Activity) {
        adConsentManager.showPrivacyOptions(activity) { error ->
            if (error != null) {
                _authActionState.value = AuthActionState.Error(error)
            }
        }
    }

    private val _huggingFaceToken = MutableStateFlow(litertPreferences.getHuggingFaceToken().orEmpty())
    val huggingFaceToken: StateFlow<String> = _huggingFaceToken.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = uiPreferences.themeMode
    val dynamicColor: StateFlow<Boolean> = uiPreferences.dynamicColor
    val requestTimeoutSeconds: StateFlow<Int> = uiPreferences.requestTimeoutSeconds
    val languageTag: StateFlow<String> = translationRepository.languageTag
    val translationStatus: StateFlow<TranslationStatus> = translationRepository.status
    val supportedLanguages: List<AppLanguage> = translationRepository.supportedLanguages
    val isPremium: StateFlow<Boolean> = adGate.isPremium
    val isWebSyncPremium: StateFlow<Boolean> = premiumManager.isWebSyncPremium

    val accountUiState: StateFlow<AccountUiState> = combine(
        authRepository.currentUser,
        syncPreferences.syncEnabled,
        syncPreferences.lastSyncAt,
        syncPreferences.lastError
    ) { user, syncEnabled, lastSyncAt, lastError ->
        AccountUiState(
            uid = user?.uid,
            email = user?.email,
            providerIds = user?.providerData?.mapNotNull { it.providerId }?.distinct().orEmpty(),
            syncEnabled = syncEnabled && user != null,
            lastSyncAt = lastSyncAt,
            lastSyncError = lastError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    private val _authActionState = MutableStateFlow<AuthActionState>(AuthActionState.Idle)
    val authActionState: StateFlow<AuthActionState> = _authActionState.asStateFlow()

    // --- Feedback / Bug reporting states ---
    private val _localBugReports = MutableStateFlow<List<LocalBugReport>>(emptyList())
    val localBugReports: StateFlow<List<LocalBugReport>> = _localBugReports.asStateFlow()

    private val _selectedScreenshotUri = MutableStateFlow<Uri?>(null)
    val selectedScreenshotUri: StateFlow<Uri?> = _selectedScreenshotUri.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _activeComments = MutableStateFlow<List<GitHubCommentResponse>>(emptyList())
    val activeComments: StateFlow<List<GitHubCommentResponse>> = _activeComments.asStateFlow()

    private val _commentsLoadState = MutableStateFlow<CommentsLoadState>(CommentsLoadState.Idle)
    val commentsLoadState: StateFlow<CommentsLoadState> = _commentsLoadState.asStateFlow()

    private val _commentPostState = MutableStateFlow<CommentPostState>(CommentPostState.Idle)
    val commentPostState: StateFlow<CommentPostState> = _commentPostState.asStateFlow()

    private val _commentScreenshotUri = MutableStateFlow<Uri?>(null)
    val commentScreenshotUri: StateFlow<Uri?> = _commentScreenshotUri.asStateFlow()

    init {
        refreshOpenIssues()
    }

    fun loadLocalBugReports() {
        _localBugReports.value = feedbackRepository.getLocalBugReports()
    }

    fun refreshOpenIssues() {
        viewModelScope.launch {
            loadLocalBugReports()
            val openReports = _localBugReports.value.filter { it.status.equals("open", ignoreCase = true) }
            for (report in openReports) {
                try {
                    feedbackRepository.refreshIssueStatus(report.issueNumber)
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Failed to refresh issue #${report.issueNumber}", e)
                }
            }
            loadLocalBugReports()
        }
    }

    fun setSelectedScreenshot(uri: Uri?) {
        _selectedScreenshotUri.value = uri
    }

    fun submitFeedback(title: String, description: String, name: String, email: String, includeDiagnostics: Boolean) {
        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            try {
                val report = feedbackRepository.submitIssue(
                    title = title,
                    description = description,
                    name = name,
                    email = email,
                    screenshotUri = _selectedScreenshotUri.value,
                    includeDiagnostics = includeDiagnostics
                )
                _submitState.value = SubmitState.Success(report)
                _selectedScreenshotUri.value = null
                loadLocalBugReports()
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    fun resetSubmitState() {
        _submitState.value = SubmitState.Idle
    }

    fun loadComments(issueNumber: Int) {
        viewModelScope.launch {
            _commentsLoadState.value = CommentsLoadState.Loading
            try {
                val comments = feedbackRepository.getComments(issueNumber)
                _activeComments.value = comments
                _commentsLoadState.value = CommentsLoadState.Success
            } catch (e: Exception) {
                _commentsLoadState.value = CommentsLoadState.Error(e.localizedMessage ?: "Failed to load comments")
            }
        }
    }

    fun setCommentScreenshot(uri: Uri?) {
        _commentScreenshotUri.value = uri
    }

    fun postCommentReply(issueNumber: Int, text: String) {
        viewModelScope.launch {
            _commentPostState.value = CommentPostState.Posting
            try {
                feedbackRepository.postComment(issueNumber, text, _commentScreenshotUri.value)
                _commentPostState.value = CommentPostState.Success
                _commentScreenshotUri.value = null
                loadComments(issueNumber)
            } catch (e: Exception) {
                _commentPostState.value = CommentPostState.Error(e.localizedMessage ?: "Failed to post comment")
            }
        }
    }

    fun resetCommentPostState() {
        _commentPostState.value = CommentPostState.Idle
    }

    // --- End Feedback states ---

    fun setThemeMode(mode: ThemeMode) = uiPreferences.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = uiPreferences.setDynamicColor(enabled)

    fun setRequestTimeoutSeconds(seconds: Int) = uiPreferences.setRequestTimeoutSeconds(seconds)

    fun setLanguage(languageTag: String) {
        viewModelScope.launch {
            translationRepository.selectLanguage(languageTag)
        }
    }

    fun updateHuggingFaceToken(token: String) {
        _huggingFaceToken.value = token
        litertPreferences.setHuggingFaceToken(token)
    }

    fun clearHuggingFaceToken() {
        _huggingFaceToken.value = ""
        litertPreferences.setHuggingFaceToken(null)
    }

    fun requestSetupTutorialAgain() {
        adGate.requestSetupTutorialReplay()
    }

    fun createAccount(email: String, password: String) {
        runAuthAction("Account created") {
            authRepository.createAccount(email, password)
            syncPreferences.setSyncEnabled(true)
        }
    }

    fun signInWithEmail(email: String, password: String) {
        runAuthAction("Signed in") {
            authRepository.signInWithEmail(email, password)
        }
    }

    fun signInWithGoogle(activity: Activity) {
        runAuthAction("Signed in with Google") {
            authRepository.signInWithGoogle(activity)
        }
    }

    fun sendPasswordReset(email: String) {
        runAuthAction("Password reset email sent") {
            authRepository.sendPasswordReset(email)
        }
    }

    fun signOut() {
        authRepository.signOut()
        syncPreferences.setSyncEnabled(false)
        syncPreferences.setCurrentUid(null)
        _authActionState.value = AuthActionState.Success("Signed out")
    }

    /**
     * Permanently delete the user's account and all associated data, satisfying
     * Google Play's account-deletion requirement. Order matters: revoke billing
     * and wipe synced data while still authenticated, then delete the Firebase
     * user, then clear local sync state.
     */
    fun deleteAccount(activity: Activity) {
        val uid = authRepository.currentUser.value?.uid
        runAuthAction("Account deleted") {
            runCatching { premiumManager.deleteRemoteEntitlements() }
            if (uid != null) {
                runCatching { realtimeChatSyncRepository.deleteAllUserData(uid) }
            }
            authRepository.deleteAccount(activity)
            syncPreferences.setSyncEnabled(false)
            syncPreferences.setCurrentUid(null)
        }
    }

    fun setWebSyncEnabled(enabled: Boolean) {
        if (authRepository.currentUser.value == null) {
            _authActionState.value = AuthActionState.Error("Sign in before enabling web sync.")
            syncPreferences.setSyncEnabled(false)
            return
        }
        syncPreferences.setSyncEnabled(enabled)
    }

    fun resetAuthActionState() {
        _authActionState.value = AuthActionState.Idle
    }

    private fun runAuthAction(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _authActionState.value = AuthActionState.Loading
            try {
                block()
                _authActionState.value = AuthActionState.Success(successMessage)
            } catch (e: Exception) {
                _authActionState.value = AuthActionState.Error(e.localizedMessage ?: "Authentication failed")
            }
        }
    }
}
