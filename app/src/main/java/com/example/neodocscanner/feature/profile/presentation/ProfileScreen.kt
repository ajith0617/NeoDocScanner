package com.example.neodocscanner.feature.profile.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.neodocscanner.core.domain.model.UserProfile

/**
 * User profile screen.
 *
 * iOS equivalent: ProfileView.swift — sheet presented from ApplicationHubView.
 *
 * Features:
 *  - Avatar circle with gradient background and initials (or person icon if no name)
 *  - View / Edit mode for: Full Name, Designation, Organisation, Email
 *  - Session section with Sign Out (+ confirmation dialog)
 *
 * UX adaptations:
 *  - TopAppBar with back navigation instead of iOS sheet "Done" button
 *  - Edit/Save as TextButton in the AppBar actions
 *  - Material 3 Cards instead of iOS grouped list style
 *  - OutlinedTextField in edit mode for each field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Logout confirmation dialog ─────────────────────────────────────────────
    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLogoutConfirm,
            title = { Text("Sign Out") },
            text  = {
                Text("You will be returned to the login screen. Your local data will remain on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissLogoutConfirm()
                        viewModel.logout()
                        onLogout()
                    }
                ) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLogoutConfirm) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (state.isEditing) viewModel.saveProfile() else viewModel.startEditing()
                        }
                    ) {
                        Text(
                            text       = if (state.isEditing) "Save" else "Edit",
                            fontWeight = if (state.isEditing) FontWeight.SemiBold else FontWeight.Normal,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Profile avatar + name ──────────────────────────────────────────
            ProfileHeader(
                profile  = state.profile,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
            )

            // ── Account info / edit ────────────────────────────────────────────
            AnimatedContent(
                targetState = state.isEditing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "profile_edit_anim"
            ) { editing ->
                if (editing) {
                    ProfileEditSection(state = state, viewModel = viewModel)
                } else {
                    ProfileViewSection(profile = state.profile)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Session / Sign Out ─────────────────────────────────────────────
            SessionSection(
                onSignOut = viewModel::showLogoutConfirm,
                modifier  = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ── Profile Header ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    profile: UserProfile,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar circle
        Box(
            modifier         = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.65f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (profile.initials.isEmpty()) {
                Icon(
                    imageVector        = Icons.Default.Person,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(42.dp)
                )
            } else {
                Text(
                    text       = profile.initials,
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Name
        Text(
            text       = profile.name.ifEmpty { "Your Name" },
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = if (profile.name.isEmpty())
                             MaterialTheme.colorScheme.onSurfaceVariant
                         else
                             MaterialTheme.colorScheme.onSurface
        )

        // Designation · Organisation
        val subtitle = listOf(profile.designation, profile.organisation)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── View mode ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileViewSection(profile: UserProfile) {
    ProfileSectionCard(
        title   = "Account",
        icon    = Icons.Default.AccountCircle,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        ProfileInfoRow(label = "Full Name",    value = profile.name,         icon = Icons.Default.Person)
        HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
        ProfileInfoRow(label = "Designation",  value = profile.designation,  icon = Icons.Outlined.Badge)
        HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
        ProfileInfoRow(label = "Organisation", value = profile.organisation, icon = Icons.Outlined.Business)
        HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
        ProfileInfoRow(label = "Email",        value = profile.email,        icon = Icons.Default.Email)
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text       = value.ifEmpty { "Not set" },
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (value.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                color      = if (value.isEmpty())
                                 MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                             else
                                 MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Edit mode ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileEditSection(
    state: ProfileUiState,
    viewModel: ProfileViewModel
) {
    ProfileSectionCard(
        title    = "Account",
        icon     = Icons.Default.AccountCircle,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ProfileTextField(
                label       = "Full Name",
                value       = state.draftName,
                placeholder = "Enter your name",
                onValueChange = viewModel::onDraftNameChange,
                keyboardType  = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(
                label       = "Designation",
                value       = state.draftDesignation,
                placeholder = "e.g. Loan Officer",
                onValueChange = viewModel::onDraftDesignationChange,
                keyboardType  = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(
                label       = "Organisation",
                value       = state.draftOrganisation,
                placeholder = "e.g. HDFC Bank",
                onValueChange = viewModel::onDraftOrganisationChange,
                keyboardType  = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(
                label       = "Email",
                value       = state.draftEmail,
                placeholder = "you@example.com",
                onValueChange = viewModel::onDraftEmailChange,
                keyboardType  = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None
            )
        }
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    capitalization: KeyboardCapitalization
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        placeholder   = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        singleLine    = true,
        keyboardOptions = KeyboardOptions(
            keyboardType   = keyboardType,
            capitalization = capitalization,
            imeAction      = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ── Session section ────────────────────────────────────────────────────────────

@Composable
private fun SessionSection(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProfileSectionCard(
        title    = "Session",
        icon     = Icons.Outlined.Security,
        modifier = modifier
    ) {
        TextButton(
            onClick  = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.error,
                    modifier           = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text       = "Sign Out",
                    color      = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    style      = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ── Section Card wrapper ───────────────────────────────────────────────────────

@Composable
private fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header label
        Row(
            modifier          = Modifier.padding(start = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text       = title,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }

        // Card body
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            content()
        }
    }
}
