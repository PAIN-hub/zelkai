package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.domain.EngineProvider
import com.example.domain.LocalLlmManager
import com.example.domain.NexusToolAndRedactor
import com.example.ui.auth.AuthScreen
import com.example.ui.nexus.NexusMainScreen
import com.example.ui.theme.NexusTheme
import com.example.ui.theme.NexusThemeMode
import com.example.ui.theme.NexusThemeTokens
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 0: Install redacted uncaught exception handler (Part E) so crashes never leak API keys or raw stack traces
        installRedactedUncaughtExceptionHandler()

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
        runCatching { configureAppCheck(this, intent) }

        // Initialize on-device inference runtime asynchronously (falls back to StubEngine on non-ARM64/emulator)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { EngineProvider.initialize(applicationContext) }
        }

        enableEdgeToEdge()

        // STEP 2: Initialize UI with persistent Light / Dark / System theme mode switch
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("nexus_theme_prefs", Context.MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()

            var themeMode by remember {
                val savedMode = prefs.getString("theme_mode", null)
                val initial = if (savedMode != null) {
                    NexusThemeMode.fromId(savedMode)
                } else if (prefs.contains("dark_mode")) {
                    if (prefs.getBoolean("dark_mode", false)) NexusThemeMode.DARK else NexusThemeMode.LIGHT
                } else {
                    NexusThemeMode.LIGHT
                }
                mutableStateOf(initial)
            }

            var recoveredCrashMessage by remember {
                val msg = prefs.getString("last_redacted_crash_summary", null)
                if (msg != null) {
                    prefs.edit().remove("last_redacted_crash_summary").apply()
                }
                mutableStateOf(msg)
            }

            val isDarkTheme = when (themeMode) {
                NexusThemeMode.DARK -> true
                NexusThemeMode.LIGHT -> false
                NexusThemeMode.SYSTEM -> systemDark
            }

            // Re-apply edge-to-edge whenever in-app theme changes
            LaunchedEffect(isDarkTheme) {
                val statusBarStyle = if (isDarkTheme) {
                    androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                val navigationBarStyle = if (isDarkTheme) {
                    androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = statusBarStyle,
                    navigationBarStyle = navigationBarStyle
                )
            }

            var fontScale by remember {
                mutableStateOf(prefs.getFloat("font_scale", 1.0f))
            }

            val setThemeMode: (NexusThemeMode) -> Unit = { nextMode ->
                themeMode = nextMode
                prefs.edit()
                    .putString("theme_mode", nextMode.id)
                    .putBoolean("dark_mode", nextMode == NexusThemeMode.DARK)
                    .apply()
            }

            val setFontScale: (Float) -> Unit = { nextScale ->
                fontScale = nextScale
                prefs.edit()
                    .putFloat("font_scale", nextScale)
                    .apply()
            }

            val toggleTheme: () -> Unit = {
                val nextMode = if (isDarkTheme) NexusThemeMode.LIGHT else NexusThemeMode.DARK
                setThemeMode(nextMode)
            }

            val feedbackController = remember { com.example.ui.nexus.FeedbackController() }

            NexusTheme(
                darkTheme = isDarkTheme,
                fontScale = fontScale
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.ui.nexus.LocalFeedbackController provides feedbackController
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = NexusThemeTokens.colors.background
                    ) {
                        if (recoveredCrashMessage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(NexusThemeTokens.spacing.xl),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = NexusThemeTokens.spacing.readingMeasureCompact),
                                    shape = MaterialTheme.shapes.large,
                                    color = NexusThemeTokens.colors.surface,
                                    border = BorderStroke(1.dp, NexusThemeTokens.colors.outline),
                                    tonalElevation = NexusThemeTokens.elevation.subtle
                                ) {
                                    Column(
                                        modifier = Modifier.padding(NexusThemeTokens.spacing.xl),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "Zelkai Recovered Safely",
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = NexusThemeTokens.colors.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(NexusThemeTokens.spacing.sm))
                                        Text(
                                            text = recoveredCrashMessage.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = NexusThemeTokens.colors.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(NexusThemeTokens.spacing.lg))
                                        Button(
                                            onClick = { recoveredCrashMessage = null },
                                            modifier = Modifier.heightIn(min = NexusThemeTokens.spacing.minTouchTarget),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text("Continue to Workspace")
                                        }
                                    }
                                }
                            }
                        } else {
                            AppNavigation(
                                isDarkTheme = isDarkTheme,
                                themeMode = themeMode,
                                onToggleTheme = toggleTheme,
                                onSelectThemeMode = setThemeMode,
                                onSelectFontScale = setFontScale
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runCatching {
            LocalLlmManager.getInstance(applicationContext).onTrimMemory(level)
        }
    }

    private fun installRedactedUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val safeMessage = NexusToolAndRedactor.redactSecrets(
                    throwable.message ?: throwable.javaClass.simpleName
                ).text.take(240)
                if (BuildConfig.DEBUG) {
                    Log.e("NexusCrashGuard", "Uncaught exception on ${thread.name}: $safeMessage")
                }
                getSharedPreferences("nexus_theme_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(
                        "last_redacted_crash_summary",
                        "An unexpected runtime error occurred ($safeMessage). All credentials and session state remain protected."
                    )
                    .commit()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean = false,
    themeMode: NexusThemeMode = if (isDarkTheme) NexusThemeMode.DARK else NexusThemeMode.LIGHT,
    onToggleTheme: () -> Unit = {},
    onSelectThemeMode: (NexusThemeMode) -> Unit = {},
    onSelectFontScale: (Float) -> Unit = {},
    auth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()
) {
    val currentUser by (auth?.authStateFlow() ?: flowOf(null))
        .collectAsStateWithLifecycle(initialValue = auth?.currentUser)
    var isGuestMode by remember { mutableStateOf(auth == null) }
    val user = currentUser

    if (user == null && !isGuestMode) {
        AuthScreen(
            onAuthSuccess = { isGuestMode = false },
            onContinueAsGuest = { isGuestMode = true }
        )
    } else {
        val activeUid = user?.uid ?: "guest_local_session"
        NexusMainScreen(
            currentUserId = activeUid,
            isGuestMode = (user == null),
            isDarkTheme = isDarkTheme,
            themeMode = themeMode,
            onToggleTheme = onToggleTheme,
            onSelectThemeMode = onSelectThemeMode,
            onSelectFontScale = onSelectFontScale,
            onSignedOut = { isGuestMode = false }
        )
    }
}
