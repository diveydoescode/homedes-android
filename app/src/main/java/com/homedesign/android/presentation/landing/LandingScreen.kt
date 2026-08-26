package com.homedesign.android.presentation.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homedesign.android.core.ui.components.InkPillButton
import com.homedesign.android.core.ui.theme.HdMono
import com.homedesign.android.core.ui.theme.HdSerif
import com.homedesign.android.core.ui.theme.HdTheme

@Composable
fun LandingScreen(
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/textures/floors/wood-walnut.jpg")
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.White.copy(alpha = 0.82f),
                        0.72f to Color.White,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LandingChip(dot = true, text = "Home Design")
            LandingChip(text = "№ 01 · Welcome")
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 30.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf("Sketch", "3D walkthrough", "AR").forEachIndexed { i, label ->
                    if (i > 0) {
                        Box(
                            Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(HdTheme.colors.stone),
                        )
                    }
                    Text(
                        label.uppercase(),
                        fontFamily = HdMono,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.6.sp,
                        color = HdTheme.colors.stone,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = buildAnnotatedString {
                    append("Sketch a room.\n")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = HdSerif)) {
                        append("Walk through it.")
                    }
                },
                style = HdTheme.typography.displayLarge.copy(
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.7).sp,
                ),
                color = HdTheme.colors.ink,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Draw a floor plan, step inside it in 3D, then place it in your real room with AR.",
                style = HdTheme.typography.bodyMedium,
                color = HdTheme.colors.graphite,
            )
            Spacer(Modifier.height(24.dp))
            InkPillButton(
                label = "Get started",
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Have an account? ",
                    style = HdTheme.typography.bodyMedium,
                    color = HdTheme.colors.graphite,
                )
                Text(
                    text = "Sign in",
                    style = HdTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = HdTheme.colors.ink,
                    modifier = Modifier.clickable(onClick = onSignIn),
                )
            }
        }
    }
}

@Composable
private fun LandingChip(text: String, dot: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .border(0.5.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (dot) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(HdTheme.colors.terracotta),
            )
        }
        Text(
            text.uppercase(),
            fontFamily = HdMono,
            fontSize = 9.5.sp,
            letterSpacing = 1.6.sp,
            color = HdTheme.colors.graphite,
        )
    }
}
