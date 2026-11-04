package com.example.ui.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.BuildConfig
import com.example.R
import com.example.ui.nexus.NexusViewModel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusDangerSoft
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusOkSoft
import com.example.ui.theme.NexusWarn
import com.example.ui.theme.PoppinsFontFamily
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "NexusAuth"

internal fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

internal fun mapAuthError(throwable: Throwable, isDebug: Boolean): String {
    val raw = throwable.message.orEmpty()
    val lower = raw.lowercase()
    if (throwable is FirebaseAuthException) {
        return when (throwable.errorCode) {
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL" ->
                "Incorrect email or password. Please check your credentials."
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "An account with this email already exists. Try signing in instead."
            "ERROR_WEAK_PASSWORD" ->
                "Password should be at least 6 characters."
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "Network connection failed. Please check your internet connection."
            "ERROR_TOO_MANY_REQUESTS" ->
                "Too many attempts. Please try again in a few minutes."
            else -> "Authentication failed: ${throwable.localizedMessage ?: "Unknown error"}"
        }
    }
    return when {
        lower.contains("network") || lower.contains("timeout") || lower.contains("unable to resolve host") ->
            "Network error. Please check your internet connection."
        lower.contains("developer_error") || lower.contains("10:") || lower.contains("16:") || lower.contains("client_id") -> {
            if (isDebug) {
                "Google Sign-In configuration error: SHA-1 or Web Client ID mismatch in Firebase Console (see docs/FIREBASE_SETUP.md)."
            } else {
                "Google Sign-In is temporarily unavailable. Please sign in with Email or continue as Guest."
            }
        }
        else -> "Sign-in failed. Please try again."
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun signOutFromNexus(
    context: Context,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    val credentialManager = CredentialManager.create(context)
    Firebase.auth.signOut()
    scope.launch {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to clear credential state", e)
            }
        } finally {
            onSignOutComplete()
        }
    }
}

enum class AuthStage {
    IDLE,
    OPENING_GOOGLE,
    SIGNING_IN,
    WELCOME,
    TIMEOUT_WARNING,
    OFFLINE_ERROR,
    ERROR
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val credentialManager = remember { CredentialManager.create(context) }
    val isDark = isSystemInDarkTheme()

    // Editorial Palette: Warm off-white (#F7F6F3) in light, near-black (#0B0D11) in dark
    val bgColor = if (isDark) Color(0xFF0B0D11) else Color(0xFFF7F6F3)
    val cardBg = if (isDark) Color(0xFF13161D) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF141519)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF5A5D68)
    val borderHairline = if (isDark) Color(0xFF232834) else Color(0xFFE4E2DA)

    var stage by rememberSaveable { mutableStateOf(AuthStage.IDLE) }
    var welcomeName by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var emailMode by rememberSaveable { mutableStateOf(false) } // false = hidden, true = expanded
    var isCreateAccount by rememberSaveable { mutableStateOf(false) }

    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var emailBlurred by rememberSaveable { mutableStateOf(false) }
    var passwordBlurred by rememberSaveable { mutableStateOf(false) }
    var statusNotification by rememberSaveable { mutableStateOf<String?>(null) }

    val emailValid = remember(emailInput) {
        emailInput.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput.trim()).matches()
    }
    val passwordValid = remember(passwordInput) {
        passwordInput.length >= 6
    }
    val formValid = emailValid && passwordValid

    // Auto sign-in attempt on first load
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    // 20-second timeout detector for async flows
    LaunchedEffect(stage) {
        if (stage == AuthStage.OPENING_GOOGLE || stage == AuthStage.SIGNING_IN) {
            delay(20_000L)
            if (stage == AuthStage.OPENING_GOOGLE || stage == AuthStage.SIGNING_IN) {
                stage = AuthStage.TIMEOUT_WARNING
            }
        }
    }

    // Google Sign-In button handler with official Android Credential Manager flow
    fun startGoogleSignIn() {
        if (stage != AuthStage.IDLE && stage != AuthStage.ERROR && stage != AuthStage.TIMEOUT_WARNING) return
        if (!isNetworkAvailable(context)) {
            stage = AuthStage.OFFLINE_ERROR
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            errorMessage = if (BuildConfig.DEBUG) {
                "Google Sign-In configuration missing: default_web_client_id not found in google-services.json"
            } else {
                "Google Sign-In is unavailable. Please sign in with email."
            }
            stage = AuthStage.ERROR
            return
        }

        stage = AuthStage.OPENING_GOOGLE
        errorMessage = null
        com.example.domain.AuthDiagnostics.clear()

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        scope.launch {
            try {
                val activityContext = context as? Activity ?: context
                val result = credentialManager.getCredential(activityContext, request)
                com.example.domain.AuthDiagnostics.recordSuccess(com.example.domain.AuthStep.CREDENTIAL_REQUEST)
                stage = AuthStage.SIGNING_IN

                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    com.example.domain.AuthDiagnostics.recordSuccess(com.example.domain.AuthStep.ID_TOKEN_RECEIVED)
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    com.example.domain.AuthDiagnostics.recordSuccess(com.example.domain.AuthStep.FIREBASE_SIGN_IN)
                    val user = authResult.user
                    val name = user?.displayName?.split(" ")?.firstOrNull() ?: "there"
                    welcomeName = name
                    stage = AuthStage.WELCOME
                    com.example.domain.AuthDiagnostics.recordSuccess(com.example.domain.AuthStep.POST_LOGIN_BOOTSTRAP)
                    delay(1000L)
                    com.example.domain.AuthDiagnostics.recordSuccess(com.example.domain.AuthStep.NAVIGATION)
                    onAuthSuccess()
                } else {
                    val formatErr = "Unexpected credential format (${credential.javaClass.simpleName})"
                    com.example.domain.AuthDiagnostics.recordFailure(
                        com.example.domain.AuthStep.ID_TOKEN_RECEIVED,
                        null,
                        code = "INVALID_CREDENTIAL_TYPE",
                        detail = formatErr
                    )
                    errorMessage = "Unexpected credential format. Please try again."
                    stage = AuthStage.ERROR
                }
            } catch (e: GetCredentialCancellationException) {
                // User dismissed Google picker quietly
                com.example.domain.AuthDiagnostics.recordFailure(
                    com.example.domain.AuthStep.CREDENTIAL_REQUEST,
                    e,
                    code = "USER_CANCELLATION",
                    detail = "User dismissed Google picker"
                )
                stage = AuthStage.IDLE
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Google Sign-in failed: ${e.message}")
                }
                val step = if (stage == AuthStage.OPENING_GOOGLE) com.example.domain.AuthStep.CREDENTIAL_REQUEST else com.example.domain.AuthStep.FIREBASE_SIGN_IN
                com.example.domain.AuthDiagnostics.recordFailure(
                    step,
                    e,
                    code = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode ?: e.javaClass.simpleName,
                    detail = e.message
                )
                errorMessage = mapAuthError(e, BuildConfig.DEBUG)
                stage = AuthStage.ERROR
            }
        }
    }

    // Email/Password handler
    fun submitEmailAuth() {
        if (!formValid || stage == AuthStage.SIGNING_IN) return
        if (!isNetworkAvailable(context)) {
            stage = AuthStage.OFFLINE_ERROR
            return
        }

        focusManager.clearFocus()
        stage = AuthStage.SIGNING_IN
        errorMessage = null

        scope.launch {
            try {
                if (isCreateAccount) {
                    val res = Firebase.auth.createUserWithEmailAndPassword(emailInput.trim(), passwordInput).await()
                    res.user?.sendEmailVerification()
                    val name = res.user?.email?.substringBefore('@') ?: "there"
                    welcomeName = name
                    stage = AuthStage.WELCOME
                    delay(1000L)
                    onAuthSuccess()
                } else {
                    val res = Firebase.auth.signInWithEmailAndPassword(emailInput.trim(), passwordInput).await()
                    val name = res.user?.displayName?.split(" ")?.firstOrNull()
                        ?: res.user?.email?.substringBefore('@') ?: "there"
                    welcomeName = name
                    stage = AuthStage.WELCOME
                    delay(1000L)
                    onAuthSuccess()
                }
            } catch (e: Exception) {
                errorMessage = mapAuthError(e, BuildConfig.DEBUG)
                stage = AuthStage.ERROR
            }
        }
    }

    // Password reset email
    fun sendPasswordReset() {
        if (!emailValid) {
            errorMessage = "Please enter a valid email address first to reset password."
            stage = AuthStage.ERROR
            return
        }
        scope.launch {
            try {
                Firebase.auth.sendPasswordResetEmail(emailInput.trim()).await()
                statusNotification = "Password reset instructions sent to ${emailInput.trim()}."
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = mapAuthError(e, BuildConfig.DEBUG)
                stage = AuthStage.ERROR
            }
        }
    }

    val isControlsDisabled = stage == AuthStage.OPENING_GOOGLE || stage == AuthStage.SIGNING_IN || stage == AuthStage.WELCOME

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = bgColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
                    initialOffsetY = { 20 },
                    animationSpec = tween(200)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Cover Photo Banner & Headline
                    val coverRes = if (isDark) R.drawable.zelkai_cover_dark else R.drawable.zelkai_cover_light
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, borderHairline),
                        shadowElevation = 2.dp
                    ) {
                        Image(
                            painter = painterResource(id = coverRes),
                            contentDescription = "ZELKAI The AI Coding Assistant",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ZELKAI",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                letterSpacing = 1.sp
                            ),
                            color = textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "The AI Coding Assistant",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp
                            ),
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    // 2. Feature Rows (3 compact cards with ImageVectors)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeatureRowItem(
                            icon = Icons.Default.SmartToy,
                            title = "Any model",
                            description = "Gemini, Claude, OpenAI, or models running locally on your phone",
                            cardBg = cardBg,
                            borderColor = borderHairline,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        FeatureRowItem(
                            icon = Icons.Default.Security,
                            title = "You stay in control",
                            description = "Approve or deny every file write, network request, or risky shell command",
                            cardBg = cardBg,
                            borderColor = borderHairline,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        FeatureRowItem(
                            icon = Icons.Default.Lock,
                            title = "Private by design",
                            description = "Keys stay on your device, secrets are redacted before transmission",
                            cardBg = cardBg,
                            borderColor = borderHairline,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }

                    // Status Banners (Offline, Timeout, Error, Status message)
                    if (stage == AuthStage.OFFLINE_ERROR) {
                        InlineAuthAlert(
                            icon = Icons.Default.WifiOff,
                            title = "You're offline",
                            message = "Connect to the internet to sign in and synchronize your projects.",
                            actionLabel = "Retry",
                            onAction = {
                                stage = AuthStage.IDLE
                                startGoogleSignIn()
                            },
                            accentColor = NexusWarn
                        )
                    } else if (stage == AuthStage.TIMEOUT_WARNING) {
                        InlineAuthAlert(
                            icon = Icons.Default.ErrorOutline,
                            title = "Taking longer than usual",
                            message = "The sign-in request is taking longer than expected. You can retry or cancel.",
                            actionLabel = "Retry",
                            onAction = {
                                stage = AuthStage.IDLE
                                startGoogleSignIn()
                            },
                            secondaryActionLabel = "Cancel",
                            onSecondaryAction = { stage = AuthStage.IDLE },
                            accentColor = NexusWarn
                        )
                    } else if (stage == AuthStage.ERROR && errorMessage != null) {
                        InlineAuthAlert(
                            icon = Icons.Default.ErrorOutline,
                            title = "Sign-in issue",
                            message = errorMessage!!,
                            actionLabel = "Try again",
                            onAction = {
                                stage = AuthStage.IDLE
                                errorMessage = null
                            },
                            secondaryActionLabel = "Copy diagnostics",
                            onSecondaryAction = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Nexus Auth Diagnostics", com.example.domain.AuthDiagnostics.exportRedactedSummary())
                                cm?.setPrimaryClip(clip)
                                statusNotification = "Diagnostics copied to clipboard (all secrets redacted)."
                            },
                            accentColor = NexusDanger
                        )
                    } else if (statusNotification != null) {
                        InlineAuthAlert(
                            icon = Icons.Default.Check,
                            title = "Notice",
                            message = statusNotification!!,
                            actionLabel = "OK",
                            onAction = { statusNotification = null },
                            accentColor = NexusOk
                        )
                    }

                    // Welcome Banner
                    if (stage == AuthStage.WELCOME) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = NexusOkSoft,
                            border = BorderStroke(1.dp, NexusOk)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = NexusOk
                                )
                                Column {
                                    Text(
                                        text = "Welcome, $welcomeName",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = NexusOk
                                    )
                                    Text(
                                        text = "Syncing your chats and settings…",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 12.sp,
                                        color = NexusOk.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Primary Button: Continue with Google (Official branding guidelines)
                    Surface(
                        onClick = { startGoogleSignIn() },
                        enabled = !isControlsDisabled,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, if (isDark) borderHairline else Color(0xFFD2D0C8)),
                        shadowElevation = if (isDark) 0.dp else 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("continue_with_google_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (stage == AuthStage.OPENING_GOOGLE) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = NexusAccent
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Opening Google…",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF1F1F1F)
                                )
                            } else if (stage == AuthStage.SIGNING_IN && !emailMode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = NexusAccent
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Signing you in…",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF1F1F1F)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google Logo",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continue with Google",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF1F1F1F)
                                )
                            }
                        }
                    }

                    // 4. Secondary Button: Continue with email
                    if (!emailMode) {
                        OutlinedButton(
                            onClick = { emailMode = true },
                            enabled = !isControlsDisabled,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, borderHairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("continue_with_email_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Continue with email",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = textPrimary
                            )
                        }
                    } else {
                        // Expanded Email/Password inline form
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderHairline)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isCreateAccount) "Create Account" else "Sign In with Email",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = textPrimary
                                    )
                                    TextButton(onClick = { isCreateAccount = !isCreateAccount }) {
                                        Text(
                                            text = if (isCreateAccount) "Sign in instead" else "Create account",
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = NexusAccent
                                        )
                                    }
                                }

                                // Email Input
                                OutlinedTextField(
                                    value = emailInput,
                                    onValueChange = { emailInput = it },
                                    label = { Text("Email address") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    isError = emailBlurred && !emailValid,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { if (it.isFocused) emailBlurred = true }
                                        .testTag("auth_email_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NexusAccent,
                                        unfocusedBorderColor = borderHairline
                                    )
                                )
                                if (emailBlurred && !emailValid && emailInput.isNotEmpty()) {
                                    Text(
                                        text = "Please enter a valid email address",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        color = NexusDanger
                                    )
                                }

                                // Password Input
                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                                tint = textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { submitEmailAuth() }),
                                    isError = passwordBlurred && !passwordValid,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { if (it.isFocused) passwordBlurred = true }
                                        .testTag("auth_password_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NexusAccent,
                                        unfocusedBorderColor = borderHairline
                                    )
                                )
                                if (passwordBlurred && !passwordValid && passwordInput.isNotEmpty()) {
                                    Text(
                                        text = "Password must be at least 6 characters",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 11.sp,
                                        color = NexusDanger
                                    )
                                }

                                if (!isCreateAccount) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { sendPasswordReset() }) {
                                            Text(
                                                text = "Forgot password?",
                                                fontFamily = PoppinsFontFamily,
                                                fontSize = 12.sp,
                                                color = textSecondary
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { emailMode = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel", fontFamily = PoppinsFontFamily, color = textSecondary)
                                    }
                                    Button(
                                        onClick = { submitEmailAuth() },
                                        enabled = formValid && !isControlsDisabled,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NexusAccent,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(2f)
                                            .height(46.dp)
                                            .testTag("auth_email_submit_button")
                                    ) {
                                        if (stage == AuthStage.SIGNING_IN && emailMode) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                        } else {
                                            Text(
                                                text = if (isCreateAccount) "Create Account" else "Sign In",
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Tertiary Button: Continue as guest
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onContinueAsGuest,
                            enabled = !isControlsDisabled,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, borderHairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("continue_as_guest_button")
                        ) {
                            Text(
                                text = "Continue as guest",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = textPrimary
                            )
                        }

                        // Plain note built from real constants (never hardcoded)
                        Text(
                            text = "${NexusViewModel.GUEST_DIRECTIVES_LIMIT} cloud AI prompts. On-device models and direct shell commands don't count. No cloud sync.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    // 6. Sign in to sync caption
                    Text(
                        text = "Sign in to sync chats and projects across your devices.",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        color = textSecondary.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRowItem(
    icon: ImageVector,
    title: String,
    description: String,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NexusAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NexusAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = textSecondary
                )
            }
        }
    }
}

@Composable
private fun InlineAuthAlert(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAction,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(actionLabel, fontFamily = PoppinsFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        TextButton(
                            onClick = onSecondaryAction,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(secondaryActionLabel, fontFamily = PoppinsFontFamily, fontSize = 11.5.sp, color = accentColor)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared Zelkai emblem icon used across Auth, EmptyState, Sidebar, and Settings.
 */
@Composable
fun NexusLogoIcon(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val logoRes = if (isDark) R.drawable.zelkai_logo_dark else R.drawable.zelkai_logo_light
    Image(
        painter = painterResource(id = logoRes),
        contentDescription = "ZELKAI Emblem",
        modifier = modifier.clip(RoundedCornerShape(8.dp))
    )
}

/**
 * Global helper for starting Google Sign-in flow from anywhere (e.g. guest upgrade).
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit = {},
    onAuthError: (String) -> Unit = {},
    scope: kotlinx.coroutines.CoroutineScope? = null,
    onAuthCancelled: () -> Unit = {}
) {
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("Activity context not found")
        return
    }
    val webClientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        ""
    }
    if (webClientId.isBlank()) {
        onAuthError("Web Client ID not configured. See docs/FIREBASE_SETUP.md")
        return
    }

    val coroutineScope = scope ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    coroutineScope.launch {
        try {
            val googleIdOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
                .build()
            val request = androidx.credentials.GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val firebaseCredential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                com.google.firebase.Firebase.auth.signInWithCredential(firebaseCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type: ${credential.type}")
            }
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            onAuthCancelled()
        } catch (e: Exception) {
            onAuthError(e.message ?: "Google sign in failed")
        }
    }
}

