package com.example.ui.nexus

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.NexusToolAndRedactor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

@Composable
fun ChatComposer(
    promptText: String,
    activeModelId: String,
    currentCwd: String,
    isDirectShellInput: Boolean,
    isRunning: Boolean,
    isAnalyzing: Boolean,
    autocompleteSuggestions: List<NexusToolAndRedactor.AutocompleteSuggestion>,
    onPromptChange: (String) -> Unit,
    onSelectSuggestion: (NexusToolAndRedactor.AutocompleteSuggestion) -> Unit,
    onToggleInputMode: () -> Unit,
    onOpenModelSwitcher: () -> Unit,
    onOpenDirectoryPicker: () -> Unit,
    onHistoryPrev: () -> Unit,
    onHistoryNext: () -> Unit,
    onEnhancePrompt: () -> Unit,
    onExecuteQuickCommand: () -> Unit = {},
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPlusMenu by remember { mutableStateOf(false) }
    var attachedFileLabel by remember { mutableStateOf<String?>(null) }

    // File attachment picker via Storage Access Framework
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "attached_file"
                val contentSnippet = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText().take(2400)
                }.orEmpty()
                attachedFileLabel = fileName
                val appended = if (contentSnippet.isNotBlank()) {
                    val prefix = if (promptText.isBlank()) "Analyze attached file `$fileName`:\n" else "$promptText\n\n"
                    "$prefix```\n$contentSnippet\n```"
                } else {
                    if (promptText.isBlank()) "Inspect file `$fileName` in $currentCwd" else "$promptText ($fileName)"
                }
                onPromptChange(appended)
            }
        }
    }

    // Speech-to-text voice launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                val combined = if (promptText.isBlank()) spoken else "$promptText $spoken"
                onPromptChange(combined)
            }
        }
    }

    val launchVoiceInput: () -> Unit = {
        runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak directive or shell command…")
            }
            speechRecognizerLauncher.launch(intent)
        }.onFailure {
            if (promptText.isBlank()) {
                onPromptChange("Run sh ./system-check.sh and summarize the system health.")
            }
        }
    }

    val shortModelLabel = remember(activeModelId) {
        formatShortModelName(activeModelId)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Dedicated Quick Command action chip & Auto-complete suggestion chips above the composer card
        AnimatedVisibility(
            visible = !isRunning,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("terminal_autocomplete_bar"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dedicated Quick Command action chip (Part A: explicit shortcut, labeled "Quick command")
                Surface(
                    onClick = {
                        if (promptText.isBlank()) {
                            onPromptChange("/quick create a folder named ")
                        } else {
                            onExecuteQuickCommand()
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = NexusAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("composer_quick_command_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Quick command",
                            tint = NexusAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Quick command (/quick)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = NexusAccent
                        )
                    }
                }

                autocompleteSuggestions.forEachIndexed { idx, item ->
                    val accentColor = when (item.kind) {
                        "history" -> NexusAccent2
                        "shell" -> NexusOk
                        else -> NexusAccent
                    }

                    Surface(
                        onClick = { onSelectSuggestion(item) },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.testTag("autocomplete_chip_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (item.kind) {
                                    "history" -> Icons.Default.History
                                    "shell" -> Icons.Default.Terminal
                                    else -> Icons.Default.AutoAwesome
                                },
                                contentDescription = item.badge,
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = if (item.kind == "shell") JetBrainsMonoFontFamily else MaterialTheme.typography.labelMedium.fontFamily,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 240.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.NorthWest,
                                contentDescription = "Complete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating 24.dp radius Composer Card with 1.dp hairline border
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_composer_card"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active context / mode indicators if Direct Shell mode or attached file is active
                if (isDirectShellInput || attachedFileLabel != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isDirectShellInput) {
                            Surface(
                                onClick = onToggleInputMode,
                                shape = RoundedCornerShape(999.dp),
                                color = NexusOk.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, NexusOk.copy(alpha = 0.35f)),
                                modifier = Modifier.testTag("composer_shell_mode_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = "Direct Shell Mode",
                                        tint = NexusOk,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "sh > Direct Shell (${NexusToolAndRedactor.shortenPath(currentCwd, 20)})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.5.sp
                                        ),
                                        color = NexusOk
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Switch back to AI mode",
                                        tint = NexusOk,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        if (attachedFileLabel != null) {
                            Surface(
                                onClick = { attachedFileLabel = null },
                                shape = RoundedCornerShape(999.dp),
                                color = NexusAccent.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, NexusAccent.copy(alpha = 0.30f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attached file",
                                        tint = NexusAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = attachedFileLabel!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove attachment",
                                        tint = NexusAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Multiline Text Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp, max = 132.dp)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (promptText.isEmpty()) {
                        Text(
                            text = if (isDirectShellInput) {
                                "Run shell command in ${NexusToolAndRedactor.shortenPath(currentCwd, 26)}…"
                            } else {
                                "How can I help you today? (Use /quick for workspace shortcuts)"
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = if (isDirectShellInput) JetBrainsMonoFontFamily else MaterialTheme.typography.bodyLarge.fontFamily,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    BasicTextField(
                        value = promptText,
                        onValueChange = onPromptChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = if (isDirectShellInput) JetBrainsMonoFontFamily else MaterialTheme.typography.bodyLarge.fontFamily,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(NexusAccent),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (!isRunning && promptText.isNotBlank()) {
                                    onSubmit()
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("directive_input_field")
                    )
                }

                // Bottom Row Inside the Card: Left "+" button | Right Model Switcher chip, Mic button, Circular Send/Stop button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: "+" Button (Attach file, Pick working directory, Quick command, Toggle ai $ / sh > mode, History, Enhance)
                    Box {
                        Surface(
                            onClick = { showPlusMenu = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("composer_plus_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Composer tools & attachments",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPlusMenu,
                            onDismissRequest = { showPlusMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Quick command (/quick)",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = "Run workspace shortcut without model inference",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = NexusAccent
                                    )
                                },
                                onClick = {
                                    showPlusMenu = false
                                    if (promptText.isBlank()) {
                                        onPromptChange("/quick create a folder named ")
                                    } else {
                                        onExecuteQuickCommand()
                                    }
                                },
                                modifier = Modifier.testTag("menu_quick_command")
                            )

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Attach file",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = "Include file content in prompt",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = NexusAccent
                                    )
                                },
                                onClick = {
                                    showPlusMenu = false
                                    filePickerLauncher.launch("*/*")
                                },
                                modifier = Modifier.testTag("menu_attach_file")
                            )

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Working directory",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = NexusToolAndRedactor.shortenPath(currentCwd, 28),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = NexusAccent
                                    )
                                },
                                onClick = {
                                    showPlusMenu = false
                                    onOpenDirectoryPicker()
                                },
                                modifier = Modifier.testTag("menu_pick_directory")
                            )

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = if (isDirectShellInput) "Switch to AI mode (ai $)" else "Switch to Shell mode (sh >)",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = if (isDirectShellInput) "Use autonomous AI agent loop" else "Execute bash commands directly",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = if (isDirectShellInput) NexusAccent else NexusOk
                                    )
                                },
                                onClick = {
                                    showPlusMenu = false
                                    onToggleInputMode()
                                },
                                modifier = Modifier.testTag("prompt_mode_toggle_button")
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Previous command in history",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            showPlusMenu = false
                                            onHistoryNext()
                                        },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("history_next_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Next in history",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    showPlusMenu = false
                                    onHistoryPrev()
                                },
                                modifier = Modifier.testTag("history_prev_button")
                            )

                            if (promptText.isNotBlank() && !isRunning && !isDirectShellInput) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Refine prompt",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NexusAccent
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = NexusAccent
                                        )
                                    },
                                    onClick = {
                                        showPlusMenu = false
                                        if (!isAnalyzing) onEnhancePrompt()
                                    },
                                    modifier = Modifier.testTag("enhance_prompt_button")
                                )
                            }
                        }
                    }

                    // Right: Model Switcher chip + Mic button + Primary circular Send/Stop button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Model Switcher Chip (model short name + chevron)
                        Surface(
                            onClick = onOpenModelSwitcher,
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Transparent,
                            modifier = Modifier.testTag("composer_model_switcher_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = shortModelLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch AI model",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Microphone button
                        IconButton(
                            onClick = launchVoiceInput,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("composer_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice input",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Primary circular Send / Waveform button (or Stop button while streaming/running)
                        if (isRunning) {
                            Surface(
                                onClick = onStop,
                                shape = CircleShape,
                                color = NexusDanger,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("stop_agent_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop generation",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        } else {
                            val hasPrompt = promptText.isNotBlank()
                            val sendBgBrush = if (hasPrompt) {
                                Brush.linearGradient(listOf(NexusAccent, NexusAccent2))
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.onSurface,
                                        MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Surface(
                                onClick = {
                                    if (hasPrompt) {
                                        attachedFileLabel = null
                                        onSubmit()
                                    } else {
                                        launchVoiceInput()
                                    }
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(sendBgBrush)
                                    .testTag("run_directive_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (hasPrompt) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send prompt",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        WaveformVectorIcon(
                                            tint = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.size(17.dp)
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

@Composable
private fun WaveformVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        val xs = listOf(0.18f, 0.39f, 0.60f, 0.81f)
        val heights = listOf(0.36f, 0.78f, 0.56f, 0.34f)
        xs.forEachIndexed { idx, xFrac ->
            val barH = h * heights[idx]
            val x = w * xFrac
            val topY = (h - barH) / 2f
            val botY = topY + barH
            drawLine(
                color = tint,
                start = Offset(x, topY),
                end = Offset(x, botY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
