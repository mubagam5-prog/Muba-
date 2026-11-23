package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.core.MubaNotificationHelper
import com.example.core.MubaStrings
import com.example.data.model.AppLanguage
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.ui.theme.MubaPureWhite
import com.example.ui.theme.MubaPurple
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    strings: MubaStrings,
    userProfileState: UiState<UserProfile?>,
    currentLanguageCode: String,
    isDarkMode: Boolean,
    onLanguageChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onReplayTutorial: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authUser = Firebase.auth.currentUser
    val profile = (userProfileState as? UiState.Success)?.data
    val displayName = profile?.displayName ?: authUser?.displayName ?: "MUBA Creator"
    val email = profile?.email ?: authUser?.email ?: ""
    val photoUrl = profile?.photoUrl ?: authUser?.photoUrl?.toString()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(MubaNotificationHelper.areNotificationsEnabled(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = MubaNotificationHelper.areNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            notificationsEnabled = MubaNotificationHelper.areNotificationsEnabled(context)
        }
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = strings.navSettings,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            // 1. User Profile Card
            Text(
                text = strings.profile,
                style = MaterialTheme.typography.labelLarge,
                color = MubaPurple
            )
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MubaPurple.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = displayName,
                                    tint = MubaPurple,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (email.isNotBlank()) {
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Google Verified Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MubaPurple
                            )
                        }

                        IconButton(
                            onClick = { showEditNameDialog = true },
                            modifier = Modifier
                                .testTag("settings_edit_name_button")
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit display name",
                                tint = MubaPurple
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            AuthRepository.signOut(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = onSignedOut
                            )
                        },
                        modifier = Modifier
                            .testTag("settings_sign_out_button")
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.signOut)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // 2. Appearance (Dark Mode)
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = MubaPurple
            )
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = MubaPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.darkMode,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isDarkMode) "Obsidian black & purple studio theme"
                                else "Minimalist white & purple studio theme",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = onDarkModeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MubaPureWhite,
                            checkedTrackColor = MubaPurple
                        ),
                        modifier = Modifier.testTag("settings_dark_mode_switch")
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // 2b. Notifications (Completion Alerts)
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.labelLarge,
                color = MubaPurple
            )
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (notificationsEnabled) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsOff
                            },
                            contentDescription = null,
                            tint = MubaPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Video Completion Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (notificationsEnabled) {
                                    "Enabled • Alerts you when AI videos finish in background"
                                } else {
                                    "Off • Allow alerts so you can leave MUBA AI while rendering"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    OutlinedButton(
                        onClick = {
                            if (!notificationsEnabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !MubaNotificationHelper.hasPostNotificationsPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                MubaNotificationHelper.openNotificationSettings(context)
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_notifications_button")
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (notificationsEnabled) "Manage" else "Enable")
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // 3. Language Selection
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MubaPurple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = strings.language,
                    style = MaterialTheme.typography.labelLarge,
                    color = MubaPurple
                )
            }
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                FlowRow(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val selected = lang.code.equals(currentLanguageCode, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .testTag("settings_lang_${lang.code}")
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MubaPurple else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (selected) MubaPurple else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onLanguageChange(lang.code) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lang.nativeName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MubaPureWhite else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // 4. Help & Support
            Text(
                text = strings.helpAndSupport,
                style = MaterialTheme.typography.labelLarge,
                color = MubaPurple
            )
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .testTag("settings_replay_tutorial_button")
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable(onClick = onReplayTutorial)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = MubaPurple
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Replay Interactive Onboarding Tutorial",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Step-by-step walkthrough of AI Video & Image tools",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )

                    Row(
                        modifier = Modifier
                            .testTag("settings_help_info_button")
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { showHelpDialog = true }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = null,
                            tint = MubaPurple
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AI Engine, Backend & Support",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Firebase AI Logic • App Check • Contact Support",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showEditNameDialog) {
        var nameInput by remember(displayName) { mutableStateOf(displayName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Update Display Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("Display Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            onDisplayNameChange(nameInput.trim())
                            showEditNameDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MubaPurple)
            },
            title = { Text("MUBA AI Help & Architecture") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• AI Image & Keyframe Studio: Powered by Firebase AI Logic (gemini-nano-banana-2.1) & Gemini 3.8 Flash secured via Firebase App Check — no manual API key required.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• AI Video Engine: Combines Gemini motion direction + AI keyframe synthesis + hardware H.264 MP4 encoding and on-device Trim/Filter/Music mixing.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Cloud Account Sync: Your profile, language, dark mode, and creation library sync securely per Google account via Cloud Firestore.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Support: mubarakgamachu78@gmail.com",
                        style = MaterialTheme.typography.labelMedium,
                        color = MubaPurple
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("Got it") }
            }
        )
    }
}
