package com.example.ui.nexus

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.domain.DeviceProfile
import com.example.domain.EngineInfo
import com.example.domain.NexusFontSize
import com.example.domain.NexusPersonaPreset
import com.example.domain.NexusPersonalizationConfig
import com.example.domain.NexusPersonalizationStore
import com.example.domain.NexusToolAndRedactor
import com.example.domain.NexusResponseStyle
import com.example.domain.NexusSkill
import com.example.ui.auth.NexusLogoIcon
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NexusAccent
import com.example.ui.theme.NexusAccent2
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusOk
import com.example.ui.theme.NexusThemeMode
import com.example.ui.theme.NexusWarn
import java.util.Locale

// ============================================================================
// SECTION 1 — PROFILE SUB-PAGE
// ============================================================================

@Composable
fun ProfileSettingsSubpage(
    avatarBitmap: Bitmap?,
    displayName: String,
    userEmail: String?,
    isGuestMode: Boolean,
    onChangePhotoClick: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountAndCloudData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var nameDraft by remember(displayName) { mutableStateOf(displayName) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("settings_subpage_profile"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Avatar + Change Photo Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onChangePhotoClick)
                        .testTag("profile_avatar_tap_target"),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    NexusUserAvatar(
                        avatarBitmap = avatarBitmap,
                        displayName = nameDraft.ifBlank { displayName },
                        email = userEmail,
                        isGuestMode = isGuestMode,
                        size = 84.dp
                    )
                    Surface(
                        shape = CircleShape,
                        color = NexusAccent,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Change photo",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onChangePhotoClick,
                    modifier = Modifier.testTag("change_picture_button")
                ) {
                    Text(
                        text = "Change picture",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = NexusAccent
                    )
                }

                Text(
                    text = if (isGuestMode) {
                        "Stored locally on this device in Guest Mode."
                    } else {
                        "Cropped to 512×512 WebP and synced to your private cloud profile."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Editable Display Name & Account Identity Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Identity",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = {
                        nameDraft = it.take(80)
                        onUpdateDisplayName(nameDraft)
                    },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g., Alex Rivera") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onUpdateDisplayName(nameDraft)
                            focusManager.clearFocus()
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (!state.isFocused && nameDraft != displayName) {
                                onUpdateDisplayName(nameDraft)
                            }
                        }
                        .testTag("profile_display_name_input")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Read-only Email / Account Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Account Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isGuestMode) "Guest mode (Local preview)" else (userEmail ?: "Signed in with Google"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isGuestMode) NexusWarn.copy(alpha = 0.14f) else NexusOk.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, if (isGuestMode) NexusWarn else NexusOk)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isGuestMode) Icons.Default.Person else Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = if (isGuestMode) NexusWarn else NexusOk,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isGuestMode) "Guest" else "Google Verified",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isGuestMode) NexusWarn else NexusOk
                            )
                        }
                    }
                }
            }
        }

        // Account Actions Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Account Actions",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isGuestMode) {
                    Button(
                        onClick = onSignOutClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_guest_sign_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Sign in with Google",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google to Sync Cloud Data")
                    }
                } else {
                    OutlinedButton(
                        onClick = { showSignOutConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign out", color = MaterialTheme.colorScheme.onSurface)
                    }

                    OutlinedButton(
                        onClick = { showDeleteAccountDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NexusDanger.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusDanger),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete account & cloud data",
                            tint = NexusDanger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete account & cloud data", color = NexusDanger)
                    }
                }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Sign out of Nexus?") },
            text = {
                Text(
                    "Your cloud sessions remain synced in Firestore, and your encrypted API keys stay on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountTwoStepDialog(
            userEmail = userEmail,
            onConfirmDelete = {
                showDeleteAccountDialog = false
                onDeleteAccountAndCloudData()
            },
            onDismiss = { showDeleteAccountDialog = false }
        )
    }
}

// ============================================================================
// SECTION 2 — GENERAL SUB-PAGE (Appearance, Response Style, Personalization, Personas, Skills)
// ============================================================================

@Composable
fun GeneralSettingsSubpage(
    themeMode: NexusThemeMode,
    personalizationConfig: NexusPersonalizationConfig,
    currentWorkingDir: String,
    hasAllFilesAccess: Boolean,
    onSelectThemeMode: (NexusThemeMode) -> Unit,
    onSelectFontSize: (NexusFontSize) -> Unit,
    onSelectResponseStyle: (NexusResponseStyle) -> Unit,
    onSelectPersonaPreset: (String) -> Unit,
    onSaveCustomPersona: (name: String, prompt: String, existingId: String?) -> Unit,
    onDeleteCustomPersona: (String) -> Unit,
    onUpdatePersonalization: (aboutUser: String, responsePref: String, enableForNewChats: Boolean) -> Unit,
    onToggleSkill: (skillId: String, enabled: Boolean) -> Unit,
    onSaveCustomSkill: (id: String?, name: String, description: String, instructions: String, enabled: Boolean) -> Unit,
    onDeleteCustomSkill: (String) -> Unit,
    onUpdateSkillsTokenBudget: (Int) -> Unit,
    onImportSkillsContent: (String) -> Result<Unit>,
    onExportSkillsJson: () -> Unit,
    onOpenDirectoryChooser: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onSelectRecentWorkspace: (String) -> Unit,
    onRemoveRecentWorkspace: (String) -> Unit,
    executionEnvironmentSummary: String = "Termux / Android Shell",
    onOpenExecutionEnvironment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var aboutUserDraft by remember(personalizationConfig.aboutUser) { mutableStateOf(personalizationConfig.aboutUser) }
    var responsePrefDraft by remember(personalizationConfig.responsePreference) {
        mutableStateOf(personalizationConfig.responsePreference)
    }
    var tokenBudgetSlider by remember(personalizationConfig.skillsTokenBudget) {
        mutableFloatStateOf(personalizationConfig.skillsTokenBudget.toFloat())
    }

    var showCreatePersonaDialog by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<NexusSkill?>(null) }
    var showCreateSkillDialog by remember { mutableStateOf(false) }
    var viewingBuiltInSkill by remember { mutableStateOf<NexusSkill?>(null) }
    var importFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val skillFileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                onImportSkillsContent(text).fold(
                    onSuccess = { importFeedbackMessage = "Skill imported and enabled." },
                    onFailure = { err -> importFeedbackMessage = err.localizedMessage ?: "Failed to import skill." }
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 28.dp)
            .testTag("settings_subpage_general"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. APPEARANCE (Theme & Font Size)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Theme Segmented Control (Light / Dark / System)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val themeOptions = listOf(
                            Triple(NexusThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                            Triple(NexusThemeMode.DARK, "Dark", Icons.Default.DarkMode),
                            Triple(NexusThemeMode.SYSTEM, "System", Icons.Default.BrightnessAuto)
                        )
                        themeOptions.forEach { (mode, label, icon) ->
                            val selected = themeMode == mode
                            Surface(
                                onClick = { onSelectThemeMode(mode) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, NexusAccent) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("general_theme_${mode.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (selected) NexusAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Font Size Segmented Control (Small / Default / Large)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Font Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NexusFontSize.entries.forEach { option ->
                            val selected = personalizationConfig.fontSize == option
                            Surface(
                                onClick = { onSelectFontSize(option) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, NexusAccent) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("font_size_${option.id}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. RESPONSE STYLE PRESETS (Concise, Balanced, Detailed, Technical)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Response Style",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Controls how Nexus formats explanations, code blocks, and terminal commentary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexusResponseStyle.entries.forEach { style ->
                        val selected = personalizationConfig.responseStyle == style
                        Surface(
                            onClick = { onSelectResponseStyle(style) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) NexusAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (selected) NexusAccent else MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("response_style_${style.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = style.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (selected) NexusAccent else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = style.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = NexusAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. PERSONA PRESETS + CUSTOM PERSONAS
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Agent Persona",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active: ${personalizationConfig.activePersona.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusAccent
                        )
                    }
                    OutlinedButton(
                        onClick = { showCreatePersonaDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("create_custom_persona_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Custom", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    personalizationConfig.allPersonas.forEach { persona ->
                        val isSelected = personalizationConfig.personaPresetId == persona.id
                        Surface(
                            onClick = { onSelectPersonaPreset(persona.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) NexusAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isSelected) NexusAccent else MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("persona_card_${persona.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = persona.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) NexusAccent else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = persona.summary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!persona.isBuiltIn) {
                                        IconButton(
                                            onClick = { onDeleteCustomPersona(persona.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete custom persona",
                                                tint = NexusDanger,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Active persona",
                                            tint = NexusAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. PERSONALIZATION ("About you" & "How should Nexus respond?")
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Personalization (\"About you\")",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enable for new chats",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = personalizationConfig.enableForNewChats,
                        onCheckedChange = { enabled ->
                            onUpdatePersonalization(aboutUserDraft, responsePrefDraft, enabled)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexusAccent),
                        modifier = Modifier.testTag("personalization_enable_switch")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "What should Nexus know about you?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${aboutUserDraft.length}/${NexusPersonalizationStore.MAX_ABOUT_USER_CHARS}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = aboutUserDraft,
                        onValueChange = {
                            aboutUserDraft = it.take(NexusPersonalizationStore.MAX_ABOUT_USER_CHARS)
                            onUpdatePersonalization(
                                aboutUserDraft,
                                responsePrefDraft,
                                personalizationConfig.enableForNewChats
                            )
                        },
                        placeholder = {
                            Text("e.g., Android & Linux kernel developer working with Kotlin, Rust, and shell scripts.")
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("about_user_input")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "How should Nexus respond?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${responsePrefDraft.length}/${NexusPersonalizationStore.MAX_RESPONSE_PREF_CHARS}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = responsePrefDraft,
                        onValueChange = {
                            responsePrefDraft = it.take(NexusPersonalizationStore.MAX_RESPONSE_PREF_CHARS)
                            onUpdatePersonalization(
                                aboutUserDraft,
                                responsePrefDraft,
                                personalizationConfig.enableForNewChats
                            )
                        },
                        placeholder = {
                            Text("e.g., Always include POSIX-safe flags and explain non-obvious awk/sed pipelines.")
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("response_preference_input")
                    )
                }
            }
        }

        // 5. SKILLS (Reusable Instruction Packs + Token Budget Slider)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Skills (Instruction Packs)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${personalizationConfig.enabledSkills.size} enabled · ~${personalizationConfig.enabledSkillsTokens} / ${personalizationConfig.skillsTokenBudget} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (personalizationConfig.isOverSkillsTokenBudget) NexusWarn else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { skillFileImportLauncher.launch("*/*") },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("import_skill_file_button")
                        ) {
                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = "Import skill", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                editingSkill = null
                                showCreateSkillDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NexusAccent, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("add_custom_skill_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add skill", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Skill", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (!importFeedbackMessage.isNullOrBlank()) {
                    Text(
                        text = importFeedbackMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = NexusAccent
                    )
                }

                // Token Budget Slider (500 – 6000 tokens, default 2000)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Skills Token Budget",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${tokenBudgetSlider.toInt()} tokens",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusAccent
                        )
                    }
                    Slider(
                        value = tokenBudgetSlider,
                        onValueChange = { tokenBudgetSlider = it },
                        onValueChangeFinished = {
                            onUpdateSkillsTokenBudget(tokenBudgetSlider.toInt())
                        },
                        valueRange = NexusPersonalizationStore.MIN_SKILLS_TOKEN_BUDGET.toFloat()..NexusPersonalizationStore.MAX_SKILLS_TOKEN_BUDGET.toFloat(),
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = NexusAccent,
                            activeTrackColor = NexusAccent
                        ),
                        modifier = Modifier.testTag("skills_token_budget_slider")
                    )
                }

                // Warning banner if enabled skills exceed token budget
                if (personalizationConfig.isOverSkillsTokenBudget) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NexusWarn.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, NexusWarn),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("skills_token_budget_warning")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Token budget exceeded",
                                tint = NexusWarn,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Enabled skills (~${personalizationConfig.enabledSkillsTokens} tokens) exceed the ${personalizationConfig.skillsTokenBudget}-token budget. Skills beyond the budget will be truncated.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // List of Built-In & Custom Skills
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    personalizationConfig.skills.forEach { skill ->
                        Surface(
                            onClick = {
                                if (skill.isBuiltIn) {
                                    viewingBuiltInSkill = skill
                                } else {
                                    editingSkill = skill
                                    showCreateSkillDialog = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                if (skill.enabled) NexusAccent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("skill_row_${skill.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = skill.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (skill.isBuiltIn) {
                                                NexusAccent2.copy(alpha = 0.14f)
                                            } else {
                                                NexusAccent.copy(alpha = 0.14f)
                                            }
                                        ) {
                                            Text(
                                                text = if (skill.isBuiltIn) "Starter" else "Custom",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (skill.isBuiltIn) NexusAccent2 else NexusAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = "~${skill.estimatedTokens} tok",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (skill.description.isNotBlank()) {
                                        Text(
                                            text = skill.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!skill.isBuiltIn) {
                                        IconButton(
                                            onClick = {
                                                editingSkill = skill
                                                showCreateSkillDialog = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit skill",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteCustomSkill(skill.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete skill",
                                                tint = NexusDanger,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = skill.enabled,
                                        onCheckedChange = { onToggleSkill(skill.id, it) },
                                        colors = SwitchDefaults.colors(checkedTrackColor = NexusAccent),
                                        modifier = Modifier.testTag("skill_switch_${skill.id}")
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onExportSkillsJson,
                        modifier = Modifier.testTag("export_skills_json_button")
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Skills JSON")
                    }
                }

                // Immutable Safety Rule Notice
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Safety policy",
                            tint = NexusOk,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Safety rule: Custom instructions, personas, and skills never bypass the command approval gate, risk classifier, or secret redactor.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 6. WORKSPACE (Current Working Directory + All Files Access + Execution Environment + Recent Workspaces)
        WorkspaceDirectoryCard(
            currentWorkingDir = currentWorkingDir,
            hasAllFilesAccess = hasAllFilesAccess,
            recentWorkspaces = personalizationConfig.recentWorkspaces,
            executionEnvironmentSummary = executionEnvironmentSummary,
            onOpenDirectoryChooser = onOpenDirectoryChooser,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onSelectRecentWorkspace = onSelectRecentWorkspace,
            onRemoveRecentWorkspace = onRemoveRecentWorkspace,
            onOpenExecutionEnvironment = onOpenExecutionEnvironment
        )
    }

    if (showCreatePersonaDialog) {
        CreateCustomPersonaDialog(
            onSave = { name, prompt ->
                onSaveCustomPersona(name, prompt, null)
                showCreatePersonaDialog = false
            },
            onDismiss = { showCreatePersonaDialog = false }
        )
    }

    if (showCreateSkillDialog) {
        SkillEditorDialog(
            initialSkill = editingSkill,
            onSave = { id, name, desc, instr, enabled ->
                onSaveCustomSkill(id, name, desc, instr, enabled)
                showCreateSkillDialog = false
                editingSkill = null
            },
            onDismiss = {
                showCreateSkillDialog = false
                editingSkill = null
            }
        )
    }

    if (viewingBuiltInSkill != null) {
        val skill = viewingBuiltInSkill!!
        AlertDialog(
            onDismissRequest = { viewingBuiltInSkill = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(skill.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = skill.instructions,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingBuiltInSkill = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun WorkspaceDirectoryCard(
    currentWorkingDir: String,
    hasAllFilesAccess: Boolean,
    recentWorkspaces: List<String>,
    executionEnvironmentSummary: String = "Termux / Android Shell",
    onOpenDirectoryChooser: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onSelectRecentWorkspace: (String) -> Unit,
    onRemoveRecentWorkspace: (String) -> Unit,
    onOpenExecutionEnvironment: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Workspace Directory",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = NexusToolAndRedactor.shortenPathMiddle(currentWorkingDir, 38),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenDirectoryChooser,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("change_workspace_button")
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelSmall,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            // Single-row All Files Access Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hasAllFilesAccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasAllFilesAccess) NexusOk else NexusWarn,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (hasAllFilesAccess) "All Files Access: Granted" else "All Files Access: App storage only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!hasAllFilesAccess) {
                    Text(
                        text = "Grant",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = NexusAccent,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onRequestAllFilesAccess)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("settings_grant_all_files_button")
                    )
                }
            }

            // Execution Environment Row (Termux / Android Shell / Remote Bridge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .clickable(onClick = onOpenExecutionEnvironment)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("workspace_execution_environment_row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = NexusAccent,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Execution environment",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = executionEnvironmentSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenExecutionEnvironment,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("open_execution_environment_button")
                ) {
                    Text(
                        text = "Configure",
                        style = MaterialTheme.typography.labelSmall,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            if (recentWorkspaces.isNotEmpty()) {
                Text(
                    text = "Recent Workspaces",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    recentWorkspaces.take(5).forEach { path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { onSelectRecentWorkspace(path) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = NexusToolAndRedactor.shortenPathMiddle(path, 38),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemoveRecentWorkspace(path) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove recent workspace",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCustomPersonaDialog(
    onSave: (name: String, prompt: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Create Custom Persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text("Persona Name") },
                    placeholder = { Text("e.g., Rust Systems Architect") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it.take(1800) },
                    label = { Text("Persona Instructions") },
                    placeholder = { Text("Describe the expertise, tone, and priorities…") },
                    minLines = 4,
                    maxLines = 7,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, prompt) },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NexusAccent, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Persona")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SkillEditorDialog(
    initialSkill: NexusSkill?,
    onSave: (id: String?, name: String, description: String, instructions: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialSkill) { mutableStateOf(initialSkill?.name.orEmpty()) }
    var description by remember(initialSkill) { mutableStateOf(initialSkill?.description.orEmpty()) }
    var instructions by remember(initialSkill) { mutableStateOf(initialSkill?.instructions.orEmpty()) }
    var enabled by remember(initialSkill) { mutableStateOf(initialSkill?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (initialSkill == null) "Add Custom Skill" else "Edit Skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(NexusPersonalizationStore.MAX_SKILL_NAME_CHARS) },
                    label = { Text("Skill Name (${name.length}/${NexusPersonalizationStore.MAX_SKILL_NAME_CHARS})") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skill_editor_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(NexusPersonalizationStore.MAX_SKILL_DESC_CHARS) },
                    label = { Text("Short Description (${description.length}/${NexusPersonalizationStore.MAX_SKILL_DESC_CHARS})") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skill_editor_desc_input")
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it.take(NexusPersonalizationStore.MAX_SKILL_INSTRUCTIONS_CHARS) },
                    label = { Text("Instructions (${instructions.length}/${NexusPersonalizationStore.MAX_SKILL_INSTRUCTIONS_CHARS})") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skill_editor_instructions_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(initialSkill?.id, name, description, instructions, enabled)
                },
                enabled = name.isNotBlank() && instructions.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NexusAccent, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_skill_dialog_button")
            ) {
                Text("Save Skill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ============================================================================
// SECTION 4 — DATA & PRIVACY SUB-PAGE
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DataAndPrivacySettingsSubpage(
    isGuestMode: Boolean,
    isNetworkOnline: Boolean,
    downloadedLocalModelsCount: Int,
    totalDownloadedBytes: Long,
    currentWorkingDir: String,
    hasAllFilesAccess: Boolean,
    recentWorkspaces: List<String>,
    savedKeysCount: Int,
    onExportAllChatsJson: () -> Unit,
    onExportAllChatsMarkdown: () -> Unit,
    onClearAllChatHistory: () -> Unit,
    onOpenLocalLlmHub: () -> Unit,
    onOpenDirectoryChooser: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onSelectRecentWorkspace: (String) -> Unit,
    onRemoveRecentWorkspace: (String) -> Unit,
    onClearAllSavedApiKeys: () -> Unit,
    executionEnvironmentSummary: String = "Termux / Android Shell",
    onOpenExecutionEnvironment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fontScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showClearKeysConfirm by remember { mutableStateOf(false) }
    var showRedactionRulesSheet by remember { mutableStateOf(false) }

    val cacheDirBytes = remember {
        runCatching {
            context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp)
            .testTag("settings_subpage_data_privacy"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Cloud Sync & Chat History
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Cloud Sync & History",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isGuestMode) NexusWarn.copy(alpha = 0.14f) else NexusOk.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isGuestMode || !isNetworkOnline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (isGuestMode) NexusWarn else NexusOk,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isGuestMode) "Disabled in Guest Mode" else "Active (Firestore)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isGuestMode) NexusWarn else NexusOk,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (fontScale >= 1.5f) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportAllChatsJson,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_chats_json_button")
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON", style = MaterialTheme.typography.labelMedium, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onExportAllChatsMarkdown,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_chats_markdown_button")
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export MD", style = MaterialTheme.typography.labelMedium, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportAllChatsJson,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_chats_json_button")
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON", style = MaterialTheme.typography.labelMedium, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onExportAllChatsMarkdown,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_chats_markdown_button")
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export MD", style = MaterialTheme.typography.labelMedium, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showClearHistoryConfirm = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusDanger.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusDanger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_all_chat_history_button")
                ) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear all chat history", softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // 2. On-Device Storage Breakdown + Manage Models + Working Directory
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = NexusAccent, modifier = Modifier.size(18.dp))
                    Text(
                        text = "On-Device Storage",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val ggufMb = totalDownloadedBytes / (1024.0 * 1024.0)
                val cacheMb = cacheDirBytes / (1024.0 * 1024.0)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Downloaded GGUF models ($downloadedLocalModelsCount)",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(
                            String.format(Locale.US, "%.1f MB", ggufMb),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model catalog & runtime cache",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(
                            String.format(Locale.US, "%.2f MB", cacheMb),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Encrypted preferences & skills",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(
                            "< 0.2 MB",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenLocalLlmHub,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manage_downloaded_models_button")
                ) {
                    Icon(imageVector = Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manage downloaded models ($downloadedLocalModelsCount)", softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Agent Working Directory Card
        WorkspaceDirectoryCard(
            currentWorkingDir = currentWorkingDir,
            hasAllFilesAccess = hasAllFilesAccess,
            recentWorkspaces = recentWorkspaces,
            executionEnvironmentSummary = executionEnvironmentSummary,
            onOpenDirectoryChooser = onOpenDirectoryChooser,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onSelectRecentWorkspace = onSelectRecentWorkspace,
            onRemoveRecentWorkspace = onRemoveRecentWorkspace,
            onOpenExecutionEnvironment = onOpenExecutionEnvironment
        )

        // 3. Security & Secret Redaction Engine (Part 1 C1-C3 fixed)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isNarrowCard = maxWidth < 380.dp || fontScale >= 1.3f
                    if (isNarrowCard) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = NexusOk,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Secret Redaction Engine",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Always active · Masks API keys, tokens & PEM blocks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusOk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showRedactionRulesSheet = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 96.dp)
                                    .testTag("view_redaction_rules_button")
                            ) {
                                Text(
                                    text = "View rules",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = NexusAccent,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = NexusOk,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Secret Redaction Engine",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Always active · Masks API keys, tokens & PEM blocks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusOk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showRedactionRulesSheet = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 96.dp)
                                    .testTag("view_redaction_rules_button")
                            ) {
                                Text(
                                    text = "View rules",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = NexusAccent,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                OutlinedButton(
                    onClick = { showClearKeysConfirm = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusDanger.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusDanger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_all_api_keys_button")
                ) {
                    Icon(imageVector = Icons.Default.KeyOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear all saved API keys ($savedKeysCount stored)",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Clear all chat history?") },
            text = {
                Text(
                    text = "This permanently deletes all saved chat sessions and command transcripts. Your API keys and downloaded models are not affected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryConfirm = false
                        onClearAllChatHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_clear_all_history_button")
                ) {
                    Text("Clear All", softWrap = false, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", softWrap = false, maxLines = 1)
                }
            }
        )
    }

    if (showClearKeysConfirm) {
        AlertDialog(
            onDismissRequest = { showClearKeysConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = NexusDanger
                )
            },
            title = { Text("Clear all saved API keys?") },
            text = {
                Text(
                    text = "Warning: This action CANNOT be undone. All encrypted Gemini, Anthropic, OpenAI, and Hugging Face API keys stored in the Android Keystore will be permanently wiped from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearKeysConfirm = false
                        onClearAllSavedApiKeys()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_clear_all_keys_button")
                ) {
                    Text("Permanently Wipe Keys", softWrap = false, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearKeysConfirm = false }) {
                    Text("Cancel", softWrap = false, maxLines = 1)
                }
            }
        )
    }

    if (showRedactionRulesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRedactionRulesSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Secret Redaction Engine Rules",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Every shell stdout/stderr stream, crash report, and model feedback payload is scrubbed before leaving the local bridge:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val rulesList = listOf(
                    "Google / Gemini keys (`AIzaSy...`)",
                    "Anthropic keys (`sk-ant-...`) & OpenAI keys (`sk-...`, `sk-proj-...`)",
                    "GitHub tokens (`ghp_...`, `github_pat_...`) & Hugging Face tokens (`hf_...`)",
                    "AWS Access Keys (`AKIA...`) & Bearer Authorization headers",
                    "PEM private keys (`-----BEGIN ... PRIVATE KEY-----`)",
                    "Exact match redaction for all user keys stored in Android Keystore"
                )
                rulesList.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = NexusOk, modifier = Modifier.size(14.dp))
                        Text(text = item, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

// ============================================================================
// SECTION 5 — ABOUT SUB-PAGE
// ============================================================================

@Composable
fun AboutSettingsSubpage(
    deviceProfile: DeviceProfile,
    engineInfo: EngineInfo,
    modifier: Modifier = Modifier
) {
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("settings_subpage_about"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Brand & Version Hero Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NexusLogoIcon(modifier = Modifier.size(54.dp))
                Text(
                    text = "Nexus Agent v5",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "Debug" else "Release"} Build)",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Autonomous Linux Shell & Multi-Provider AI Workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Device & Runtime Summary Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Device & Runtime Telemetry",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                val ramGb = deviceProfile.totalRamGb
                val availRamGb = deviceProfile.availableRamGb

                val rows = listOf(
                    "Device Model" to deviceProfile.deviceName,
                    "SoC / ABI" to "${deviceProfile.socSummary} (${deviceProfile.primaryAbi})",
                    "CPU Topology" to deviceProfile.coreSummary,
                    "System RAM" to String.format(Locale.US, "%.1f GB total · %.1f GB free", ramGb, availRamGb),
                    "Local LLM Engine" to if (engineInfo.isRuntimeInstalled) {
                        "${engineInfo.name} (${engineInfo.llamaCppTag} · ${engineInfo.abi})"
                    } else {
                        "${engineInfo.name} (Unavailable · ${engineInfo.abi})"
                    }
                )

                rows.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Legal & Privacy Links
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showPrivacyDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_privacy_summary_button")
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy & Security Architecture")
                }

                OutlinedButton(
                    onClick = { showLicensesDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_licenses_button")
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open-Source Licenses")
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Privacy & Security Summary") },
            text = {
                Text(
                    text = "• Hardware-Backed Keys: Personal API keys are encrypted with AES-256-GCM inside the Android Keystore and never leave your device except in direct HTTPS requests to your chosen provider.\n\n" +
                        "• Command Approval Gate: Medium- and high-risk shell commands always pause for explicit user approval.\n\n" +
                        "• Secret Redaction: All command outputs and crash diagnostics are scrubbed by NexusToolAndRedactor before storage or model feedback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Open-Source Notices") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "• Android Jetpack Compose & Material 3 (Apache 2.0)\n" +
                            "• OkHttp & Kotlin Coroutines / Serialization (Apache 2.0)\n" +
                            "• Poppins & JetBrains Mono Typography (OFL 1.1)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "llama.cpp (Release Tag b12020) — MIT License\n" +
                            "Copyright (c) 2023-2026 The ggml authors\n" +
                            "Copyright (c) 2025-2026 Arm Limited (llama.android JNI wrapper)\n\n" +
                            "Permission is hereby granted, free of charge, to any person obtaining a copy " +
                            "of this software and associated documentation files (the \"Software\"), to deal " +
                            "in the Software without restriction, including without limitation the rights " +
                            "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell " +
                            "copies of the Software, and to permit persons to whom the Software is " +
                            "furnished to do so, subject to the following conditions:\n\n" +
                            "The above copyright notice and this permission notice shall be included in all " +
                            "copies or substantial portions of the Software.\n\n" +
                            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR " +
                            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, " +
                            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
