package com.charles.ollama.client.ui.settings

import android.graphics.BitmapFactory
import android.net.Uri
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.R
import com.charles.ollama.client.data.preferences.ThemeMode
import com.charles.ollama.client.data.preferences.LocalBugReport
import com.charles.ollama.client.ui.components.BannerAd
import com.charles.ollama.client.util.PerformanceMonitor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val screenTrace = remember { PerformanceMonitor.startScreenTrace("SettingsScreen") }
    DisposableEffect(Unit) {
        onDispose { PerformanceMonitor.stopTrace(screenTrace) }
    }

    val huggingFaceToken by viewModel.huggingFaceToken.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val isWebSyncPremium by viewModel.isWebSyncPremium.collectAsState()
    val accountUiState by viewModel.accountUiState.collectAsState()
    val authActionState by viewModel.authActionState.collectAsState()
    val context = LocalContext.current

    // --- Feedback States ---
    val localBugReports by viewModel.localBugReports.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val screenshotUri by viewModel.selectedScreenshotUri.collectAsState()
    val activeComments by viewModel.activeComments.collectAsState()
    val commentsLoadState by viewModel.commentsLoadState.collectAsState()
    val commentPostState by viewModel.commentPostState.collectAsState()
    val commentScreenshotUri by viewModel.commentScreenshotUri.collectAsState()

    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReportForDetail by remember { mutableStateOf<LocalBugReport?>(null) }

    var titleText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var emailText by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.loadLocalBugReports()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { BannerAd() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium
            )
            ThemeModeSelector(
                selected = themeMode,
                onSelect = viewModel::setThemeMode
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Material You colors",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Use your wallpaper's colors instead of the app's brand palette (Android 12+).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            AccountSyncSection(
                accountUiState = accountUiState,
                authActionState = authActionState,
                isWebSyncPremium = isWebSyncPremium,
                onCreateAccount = viewModel::createAccount,
                onSignInEmail = viewModel::signInWithEmail,
                onPasswordReset = viewModel::sendPasswordReset,
                onGoogleSignIn = {
                    (context as? Activity)?.let(viewModel::signInWithGoogle)
                },
                onSignOut = viewModel::signOut,
                onSyncEnabledChange = viewModel::setWebSyncEnabled,
                onDismissAuthMessage = viewModel::resetAuthActionState,
                onNavigateToPaywall = onNavigateToPaywall
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Premium",
                style = MaterialTheme.typography.titleMedium
            )
            if (isPremium) {
                Text(
                    text = "Premium active ✓ — all ads are removed. Thanks for your support!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Remove every ad in the app with a subscription or a one-time lifetime purchase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onNavigateToPaywall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Ads / Go Premium")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "On-device LiteRT (Gemma)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Gemma 4 bundles from litert-community are public and don't need a token. This field is only required for gated Hugging Face repos — paste a read-only token from huggingface.co/settings/tokens if you hit a 401/403 on download.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = huggingFaceToken,
                onValueChange = viewModel::updateHuggingFaceToken,
                label = { Text("Hugging Face token") },
                placeholder = { Text("hf_…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    if (huggingFaceToken.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearHuggingFaceToken) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear token")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (huggingFaceToken.isBlank()) {
                    "No token set — gated models will fail to download."
                } else {
                    "Token saved on this device only."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Help",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Open the same tips you see on first launch: remote Ollama vs on-device LiteRT.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = viewModel::requestSetupTutorialAgain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_show_setup_tutorial_again))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_about_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onNavigateToAbout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_about_open_button))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Support & Feedback",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = viewModel::refreshOpenIssues) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Found a bug or have feedback?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Submit detailed bug reports directly to GitHub. You can attach screenshots and track progress on your issue right inside the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showReportDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Report a Problem")
                    }
                }
            }
            
            if (localBugReports.isNotEmpty()) {
                Text(
                    text = "My Submitted Reports",
                    style = MaterialTheme.typography.titleSmall
                )
                localBugReports.forEach { report ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReportForDetail = report },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = report.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Issue #${report.issueNumber} • ${report.createdAt.substringBefore("T")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val isOpen = report.status.equals("open", ignoreCase = true)
                            val badgeColor = if (isOpen) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFFC62828)
                            Surface(
                                color = badgeColor,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isOpen) "Open" else "Closed",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // --- Dialogs ---

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = {
                showReportDialog = false
                viewModel.resetSubmitState()
            },
            title = { Text("Report a Problem") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Disclaimer: Everything submitted, including diagnostics and screenshots, will be public on GitHub.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Title / Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text("Description") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emailText,
                        onValueChange = { emailText = it },
                        label = { Text("Email (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeDiagnostics = !includeDiagnostics }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeDiagnostics,
                            onCheckedChange = { includeDiagnostics = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Include system diagnostics & models list",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val photoPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia(),
                        onResult = { uri -> viewModel.setSelectedScreenshot(uri) }
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Screenshot:", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick Image")
                        }
                    }
                    
                    screenshotUri?.let { uri ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UriImagePreview(
                                uri = uri,
                                modifier = Modifier
                                    .size(80.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            )
                            IconButton(onClick = { viewModel.setSelectedScreenshot(null) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove screenshot", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    
                    when (val state = submitState) {
                        is SubmitState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        is SubmitState.Success -> {
                            Text("Created successfully! Issue #${state.report.issueNumber}", color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
                            LaunchedEffect(state) {
                                kotlinx.coroutines.delay(1500)
                                showReportDialog = false
                                titleText = ""
                                descriptionText = ""
                                nameText = ""
                                emailText = ""
                                viewModel.resetSubmitState()
                            }
                        }
                        is SubmitState.Error -> {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleText.isNotBlank() && descriptionText.isNotBlank()) {
                            viewModel.submitFeedback(titleText, descriptionText, nameText, emailText, includeDiagnostics)
                        }
                    },
                    enabled = titleText.isNotBlank() && descriptionText.isNotBlank() && submitState !is SubmitState.Loading
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReportDialog = false
                        viewModel.resetSubmitState()
                    },
                    enabled = submitState !is SubmitState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedReportForDetail?.let { report ->
        LaunchedEffect(report) {
            viewModel.loadComments(report.issueNumber)
        }
        
        var replyText by remember { mutableStateOf("") }
        val uriHandler = LocalUriHandler.current
        
        AlertDialog(
            onDismissRequest = {
                selectedReportForDetail = null
                viewModel.resetCommentPostState()
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2
                        )
                        Text(
                            text = "Issue #${report.issueNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { uriHandler.openUri(report.htmlUrl) }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "View on GitHub")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(4.dp)
                    ) {
                        when (val loadState = commentsLoadState) {
                            is CommentsLoadState.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            is CommentsLoadState.Error -> {
                                Text(
                                    text = loadState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            else -> {
                                if (activeComments.isEmpty()) {
                                    Text(
                                        text = "No comments yet.",
                                        modifier = Modifier.align(Alignment.Center),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(activeComments) { comment ->
                                            val isUserReply = comment.body.startsWith("**[User Reply from App]**")
                                            val cleanBody = if (isUserReply) {
                                                comment.body.removePrefix("**[User Reply from App]**").trim()
                                            } else {
                                                comment.body
                                            }
                                            
                                            val bubbleColor = if (isUserReply) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                            
                                            val textColor = if (isUserReply) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = if (isUserReply) Alignment.End else Alignment.Start
                                            ) {
                                                Text(
                                                    text = if (isUserReply) "You" else comment.user.login,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                                Surface(
                                                    color = bubbleColor,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(
                                                        text = cleanBody,
                                                        color = textColor,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Write a reply...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (replyText.isNotBlank()) {
                                        viewModel.postCommentReply(report.issueNumber, replyText)
                                        replyText = ""
                                    }
                                },
                                enabled = replyText.isNotBlank() && commentPostState !is CommentPostState.Posting
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send")
                            }
                        }
                    )
                    
                    val commentPhotoPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia(),
                        onResult = { uri -> viewModel.setCommentScreenshot(uri) }
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Attach image:", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = {
                                commentPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick Image")
                        }
                    }
                    
                    commentScreenshotUri?.let { uri ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UriImagePreview(
                                uri = uri,
                                modifier = Modifier
                                    .size(50.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            )
                            IconButton(onClick = { viewModel.setCommentScreenshot(null) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    
                    if (commentPostState is CommentPostState.Posting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (commentPostState is CommentPostState.Error) {
                        Text(
                            text = (commentPostState as CommentPostState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedReportForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun AccountSyncSection(
    accountUiState: AccountUiState,
    authActionState: AuthActionState,
    isWebSyncPremium: Boolean,
    onCreateAccount: (String, String) -> Unit,
    onSignInEmail: (String, String) -> Unit,
    onPasswordReset: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onDismissAuthMessage: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        text = "Account & web sync",
        style = MaterialTheme.typography.titleMedium
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (accountUiState.uid == null) {
                Text(
                    text = "Sign in to sync chats with the future web interface.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = authActionState !is AuthActionState.Loading
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue with Google")
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSignInEmail(email, password) },
                        enabled = email.isNotBlank() && password.isNotBlank() && authActionState !is AuthActionState.Loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sign in")
                    }
                    OutlinedButton(
                        onClick = { onCreateAccount(email, password) },
                        enabled = email.isNotBlank() && password.length >= 6 && authActionState !is AuthActionState.Loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create")
                    }
                }
                TextButton(
                    onClick = { onPasswordReset(email) },
                    enabled = email.isNotBlank() && authActionState !is AuthActionState.Loading
                ) {
                    Text("Send password reset")
                }
            } else {
                Text(
                    text = accountUiState.email ?: "Signed in",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Providers: ${accountUiState.providerIds.joinToString().ifBlank { "Firebase" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Web sync", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (accountUiState.syncEnabled) {
                                "Chats sync through Firebase Realtime Database."
                            } else {
                                "Off. Chats stay on this device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = accountUiState.syncEnabled,
                        onCheckedChange = onSyncEnabledChange
                    )
                }
                if (accountUiState.syncEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isWebSyncPremium) {
                            Text(
                                text = "Unlimited web messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "3 free web messages / day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = onNavigateToPaywall,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "Upgrade",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                if (accountUiState.lastSyncAt > 0L) {
                    Text(
                        text = "Last sync: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(accountUiState.lastSyncAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                accountUiState.lastSyncError?.let { error ->
                    Text(
                        text = "Sync error: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign out")
                }
            }

            when (authActionState) {
                AuthActionState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is AuthActionState.Success -> AssistChip(
                    onClick = onDismissAuthMessage,
                    label = { Text(authActionState.message) },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                is AuthActionState.Error -> Text(
                    text = authActionState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                AuthActionState.Idle -> Unit
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UriImagePreview(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageBitmap = remember(uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Image preview",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Preview failed", style = MaterialTheme.typography.bodySmall)
        }
    }
}
