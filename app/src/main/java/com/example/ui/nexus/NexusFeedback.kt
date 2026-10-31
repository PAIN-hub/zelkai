package com.example.ui.nexus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusWarn
import com.example.ui.theme.PoppinsFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class UiMessageSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class UiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val severity: UiMessageSeverity = UiMessageSeverity.INFO,
    val title: String,
    val detail: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val isPersistent: Boolean = false,
    val durationMs: Long = if (actionLabel != null) 8000L else 4000L
)

class FeedbackController {
    val activeMessages = mutableStateListOf<UiMessage>()

    fun show(message: UiMessage) {
        // Deduplicate messages with same title
        if (activeMessages.any { it.title == message.title }) return
        activeMessages.add(message)
    }

    fun dismiss(id: String) {
        activeMessages.removeAll { it.id == id }
    }
}

val LocalFeedbackController = compositionLocalOf { FeedbackController() }

/**
 * Global feedback host rendering queued snackbars and persistent status banners politely to TalkBack.
 */
@Composable
fun FeedbackHost(
    controller: FeedbackController = LocalFeedbackController.current,
    modifier: Modifier = Modifier
) {
    val current = controller.activeMessages.firstOrNull()

    LaunchedEffect(current?.id) {
        if (current != null && !current.isPersistent) {
            delay(current.durationMs)
            controller.dismiss(current.id)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -40 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -40 })
        ) {
            if (current != null) {
                val accent = when (current.severity) {
                    UiMessageSeverity.INFO -> NexusAccent
                    UiMessageSeverity.SUCCESS -> NexusOk
                    UiMessageSeverity.WARNING -> NexusWarn
                    UiMessageSeverity.ERROR -> NexusDanger
                }
                val icon = when (current.severity) {
                    UiMessageSeverity.INFO -> Icons.Default.Info
                    UiMessageSeverity.SUCCESS -> Icons.Default.CheckCircle
                    UiMessageSeverity.WARNING -> Icons.Default.Warning
                    UiMessageSeverity.ERROR -> Icons.Default.ErrorOutline
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("feedback_host_message_${current.id}"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = current.severity.name,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = current.title,
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!current.detail.isNullOrBlank()) {
                                Text(
                                    text = current.detail,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (current.actionLabel != null && current.onAction != null) {
                            TextButton(
                                onClick = {
                                    current.onAction.invoke()
                                    controller.dismiss(current.id)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = current.actionLabel,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = accent
                                )
                            }
                        }
                        IconButton(
                            onClick = { controller.dismiss(current.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notification",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Get started" onboarding checklist card displayed on empty chat state for new operators.
 * Real state tracking: API key / model, execution environment, and cloud sync.
 */
@Composable
fun GetStartedChecklistCard(
    hasApiKeyOrLocalModel: Boolean,
    hasConfiguredEnvironment: Boolean,
    isSignedIn: Boolean,
    onOpenSettings: () -> Unit,
    onOpenEnvironmentSetup: () -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("get_started_checklist_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NexusAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NexusAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Get started with Nexus",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss checklist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "Complete these optional steps to get the most out of your command workspace.",
                fontFamily = PoppinsFontFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChecklistItem(
                    title = "Add an API key or download an on-device model",
                    description = "Enables unlimited chat and multi-model planning",
                    isCompleted = hasApiKeyOrLocalModel,
                    actionLabel = if (hasApiKeyOrLocalModel) "Configured" else "Configure",
                    onAction = onOpenSettings
                )
                ChecklistItem(
                    title = "Set up the execution environment (optional)",
                    description = "Choose Termux backend or built-in shell for package scripts",
                    isCompleted = hasConfiguredEnvironment,
                    actionLabel = if (hasConfiguredEnvironment) "Ready" else "Set up",
                    onAction = onOpenEnvironmentSetup
                )
                ChecklistItem(
                    title = "Sign in to sync across devices (optional)",
                    description = "Back up your sessions and settings securely to Firestore",
                    isCompleted = isSignedIn,
                    actionLabel = if (isSignedIn) "Signed in" else "Sign in",
                    onAction = onSignIn
                )
            }
        }
    }
}

@Composable
private fun ChecklistItem(
    title: String,
    description: String,
    isCompleted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        onClick = onAction,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isCompleted) "Completed" else "Not completed",
                tint = if (isCompleted) NexusOk else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = actionLabel,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                color = if (isCompleted) NexusOk else NexusAccent
            )
        }
    }
}
