package com.homedesign.android.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homedesign.android.core.ui.components.InkPillButton
import com.homedesign.android.core.ui.components.InkPlateButton
import com.homedesign.android.core.ui.components.JourneyBackButton
import com.homedesign.android.core.ui.components.JourneyEyebrow
import com.homedesign.android.core.ui.components.SerifHeadline
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

const val AuthNotConfiguredMessage = "Sign-in isn't configured in this build yet"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    fun complete(email: String? = null, onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.completeAuth(email = email?.ifBlank { null })
            onDone()
        }
    }
}

@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var email by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            JourneyBackButton(onClick = onBack)
            JourneyEyebrow("Account")
            Spacer(Modifier.padding(start = 48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp),
        ) {
            SerifHeadline(lead = "Welcome", italic = "back.")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Sign in to pick up where you left off.",
                style = HdTheme.typography.bodyMedium,
                color = HdTheme.colors.stone,
            )
            Spacer(Modifier.height(28.dp))

            InkPillButton(
                label = if (busy) "Working…" else "Continue with Google",
                enabled = !busy,
                onClick = {
                    notice = AuthNotConfiguredMessage
                },
                modifier = Modifier.fillMaxWidth(),
            )

            notice?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = HdTheme.typography.bodySmall,
                    color = HdTheme.colors.terracotta,
                )
            }

            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(HdTheme.colors.hairline),
                )
                Text(
                    text = "  Or with email  ",
                    style = HdTheme.typography.labelSmall,
                    color = HdTheme.colors.stone,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(HdTheme.colors.hairline),
                )
            }

            Spacer(Modifier.height(20.dp))
            JourneyEyebrow("Email")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("you@home.com", color = HdTheme.colors.stone.copy(alpha = 0.55f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HdTheme.colors.terracotta,
                    unfocusedBorderColor = HdTheme.colors.hairline,
                    focusedTextColor = HdTheme.colors.ink,
                    unfocusedTextColor = HdTheme.colors.ink,
                    cursorColor = HdTheme.colors.terracotta,
                ),
            )

            Spacer(Modifier.height(16.dp))
            InkPlateButton(
                label = "Continue",
                onClick = {
                    busy = true
                    viewModel.complete(email.trim()) {
                        busy = false
                        onDone()
                    }
                },
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Skip for now",
                style = HdTheme.typography.labelLarge,
                color = HdTheme.colors.stone,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        viewModel.complete(null, onDone)
                    }
                    .padding(12.dp),
            )
        }
    }
}
