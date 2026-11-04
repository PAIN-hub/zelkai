package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.domain.EngineProvider
import com.example.domain.LocalLlmManager
import com.example.domain.NexusToolAndRedactor
import com.example.ui.auth.AuthScreen
import com.example.ui.nexus.NexusMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NexusThemeMode
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

        // Initialize on-device inference runtime safely (falls back to StubEngine on non-ARM64/emulator)
        EngineProvider.initialize(applicationContext)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

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

            MyApplicationTheme(
                darkTheme = isDarkTheme,
                fontScale = fontScale
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.ui.nexus.LocalFeedbackController provides feedbackController
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF0B0D11) else androidx.compose.ui.graphics.Color(0xFFF7F6F3)
                    ) {
                        if (recoveredCrashMessage != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Nexus Agent Recovered Safely",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = recoveredCrashMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { recoveredCrashMessage = null }) {
                                    Text("Continue to Workspace")
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
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var isGuestMode by remember { mutableStateOf(false) }
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
