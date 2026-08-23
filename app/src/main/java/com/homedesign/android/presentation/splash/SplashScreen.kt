package com.homedesign.android.presentation.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.homedesign.android.core.ui.components.BrandMark
import com.homedesign.android.core.ui.theme.HdSerif
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SplashDurationMs = 1400L

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    suspend fun hasOnboarded(): Boolean =
        settingsRepository.settings.first().hasOnboarded
}

@Composable
fun SplashScreen(
    onFinished: (hasOnboarded: Boolean) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val mutex = remember { Mutex() }
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (finished) return
        scope.launch {
            mutex.withLock {
                if (finished) return@withLock
                finished = true
                val onboarded = viewModel.hasOnboarded()
                onFinished(onboarded)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(SplashDurationMs)
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .clickable { finish() },
    ) {
        // Warm bloom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HdTheme.colors.tintWarm.copy(alpha = 0.35f)),
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(size = 72.dp)
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(1.dp)
                    .background(HdTheme.colors.hairline),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = buildAnnotatedString {
                    append("Home ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = HdSerif)) {
                        append("Design")
                    }
                },
                style = HdTheme.typography.headlineLarge,
                color = HdTheme.colors.ink,
            )
        }

        Text(
            text = "№ 01",
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.stone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
        )
    }
}
