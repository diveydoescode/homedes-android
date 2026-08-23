package com.homedesign.android.presentation.landing

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.components.InkPillButton
import com.homedesign.android.core.ui.components.JourneyEyebrow
import com.homedesign.android.core.ui.theme.HdSerif
import com.homedesign.android.core.ui.theme.HdTheme

@Composable
fun LandingScreen(
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .background(HdTheme.colors.highlight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HdTheme.colors.tintWarm.copy(alpha = 0.45f)),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp),
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("Home ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = HdSerif)) {
                            append("Design")
                        }
                    },
                    style = HdTheme.typography.titleLarge,
                    color = HdTheme.colors.ink,
                )
                Spacer(Modifier.height(6.dp))
                JourneyEyebrow("№ 01 · Welcome")
            }
        }

        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                JourneyEyebrow("Sketch · 3D walkthrough · AR")
                Spacer(Modifier.height(16.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Sketch a room.\n")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = HdSerif)) {
                            append("Walk through it.")
                        }
                    },
                    style = HdTheme.typography.displayMedium,
                    color = HdTheme.colors.ink,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Draw a floor plan, step inside it in 3D, then place it in your real room with AR.",
                    style = HdTheme.typography.bodyMedium,
                    color = HdTheme.colors.stone,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                InkPillButton(
                    label = "Get started",
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Have an account? ",
                        style = HdTheme.typography.bodyMedium,
                        color = HdTheme.colors.stone,
                    )
                    Text(
                        text = "Sign in",
                        style = HdTheme.typography.bodyMedium,
                        color = HdTheme.colors.terracotta,
                        modifier = Modifier.clickable(onClick = onSignIn),
                    )
                }
            }
        }
    }
}
