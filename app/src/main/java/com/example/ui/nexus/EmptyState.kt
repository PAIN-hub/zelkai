package com.example.ui.nexus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.NexusLogoIcon
import com.example.ui.theme.EditorialSerifFontFamily
import com.example.ui.theme.NexusAccent
import java.util.Calendar

private data class EmptyStateSuggestion(
    val label: String,
    val directive: String,
    val icon: ImageVector
)

private val DEFAULT_SUGGESTIONS = listOf(
    EmptyStateSuggestion(
        label = "Inspect logs",
        directive = "Inspect logs/agent-audit.log and summarize any warnings or masked tokens.",
        icon = Icons.Outlined.Description
    ),
    EmptyStateSuggestion(
        label = "Run telemetry",
        directive = "Run sh ./system-check.sh and summarize the system health.",
        icon = Icons.Outlined.Speed
    ),
    EmptyStateSuggestion(
        label = "Audit command",
        directive = "List all files in the current working directory and check disk space.",
        icon = Icons.Outlined.Terminal
    ),
    EmptyStateSuggestion(
        label = "Security check",
        directive = "Check current permissions and environment variables safely.",
        icon = Icons.Outlined.Security
    )
)

fun computeTimeAwareGreeting(firstName: String?, isGuestMode: Boolean): String {
    if (isGuestMode || firstName.isNullOrBlank()) {
        return "Welcome"
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val salutation = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return "$salutation, $firstName"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyState(
    firstName: String?,
    isGuestMode: Boolean,
    isTemporaryChat: Boolean,
    enabled: Boolean,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasApiKeyOrLocalModel: Boolean = true,
    hasConfiguredEnvironment: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onOpenEnvironmentSetup: () -> Unit = {},
    onSignIn: () -> Unit = {}
) {
    // Collapsed by default and dismissible per design specification
    var suggestionsExpanded by rememberSaveable { mutableStateOf(false) }
    var suggestionsDismissed by rememberSaveable { mutableStateOf(false) }
    var checklistDismissed by rememberSaveable { mutableStateOf(false) }

    val greeting = remember(firstName, isGuestMode) {
        computeTimeAwareGreeting(firstName = firstName, isGuestMode = isGuestMode)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .testTag("chat_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Centered Nexus emblem (uploaded icon1 emblem)
            NexusLogoIcon(
                modifier = Modifier
                    .size(56.dp)
                    .testTag("empty_state_logo")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Editorial greeting underneath logo
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = EditorialSerifFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 27.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("empty_state_greeting")
            )

            if (isTemporaryChat) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Temporary session · Messages & shell outputs won't be saved to history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (!checklistDismissed && (isGuestMode || !hasApiKeyOrLocalModel || !hasConfiguredEnvironment)) {
                GetStartedChecklistCard(
                    hasApiKeyOrLocalModel = hasApiKeyOrLocalModel,
                    hasConfiguredEnvironment = hasConfiguredEnvironment,
                    isSignedIn = !isGuestMode,
                    onOpenSettings = onOpenSettings,
                    onOpenEnvironmentSetup = onOpenEnvironmentSetup,
                    onSignIn = onSignIn,
                    onDismiss = { checklistDismissed = true }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Optional 3–4 suggestion chips: collapsed by default and dismissible
            AnimatedVisibility(
                visible = !suggestionsDismissed,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { suggestionsExpanded = !suggestionsExpanded },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.testTag("toggle_suggestions_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = "Suggestions",
                                    tint = NexusAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (suggestionsExpanded) "Hide suggestions" else "Suggestions",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (suggestionsExpanded) {
                                        Icons.Default.KeyboardArrowUp
                                    } else {
                                        Icons.Default.KeyboardArrowDown
                                    },
                                    contentDescription = if (suggestionsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        if (suggestionsExpanded) {
                            IconButton(
                                onClick = { suggestionsDismissed = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("dismiss_suggestions_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss suggestions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = suggestionsExpanded,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(150))
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DEFAULT_SUGGESTIONS.forEachIndexed { index, item ->
                                Surface(
                                    onClick = { if (enabled) onSelectSuggestion(item.directive) },
                                    enabled = enabled,
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.testTag("empty_state_suggestion_$index")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
