package com.charles.ollama.client.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.charles.ollama.client.domain.model.Server
import com.charles.ollama.client.ui.navigation.InterstitialAdManagerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

/**
 * One-time bottom sheet introducing the Personas feature. Shows once per install
 * (via [com.charles.ollama.client.ads.AdGate.hasSeenPersonasIntro]) after the
 * setup tutorial and rewards sheet have had time to settle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewPersonasSheet(
    defaultServer: Server?,
    setupTutorialFinishedSignal: Int,
) {
    val context = LocalContext.current
    val adGate = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterstitialAdManagerEntryPoint::class.java
        ).adGate()
    }

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(defaultServer, setupTutorialFinishedSignal) {
        if (adGate.hasSeenPersonasIntro()) return@LaunchedEffect
        if (!adGate.hasSeenSetupTutorial() && defaultServer == null) return@LaunchedEffect
        // Delay longer than WhatsNewRewardsSheet (900ms) so sheets don't stack
        delay(1500)
        if (!adGate.hasSeenPersonasIntro()) {
            visible = true
        }
    }

    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dismiss: () -> Unit = {
        adGate.markPersonasIntroSeen()
        visible = false
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PersonaHeroBadge()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Meet Chat Personas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Give your AI a personality. Chat with a Best Friend, Therapist, " +
                    "Interview Coach, and more.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            PersonaFeatureBullet(
                icon = Icons.Default.Add,
                title = "Pick one when you start a chat",
                body = "Tap the + button and choose a persona — it sets the AI's personality for that conversation."
            )
            Spacer(modifier = Modifier.height(10.dp))
            PersonaFeatureBullet(
                icon = Icons.Default.People,
                title = "10 built-in characters",
                body = "Best Friend, Therapist, Interview Coach, Romantic Partner, Mentor, Comedian, and more."
            )
            Spacer(modifier = Modifier.height(10.dp))
            PersonaFeatureBullet(
                icon = Icons.Default.Tune,
                title = "Make your own",
                body = "Open a chat's settings and tap \"Browse prompt library\" to create custom personas."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = dismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Face, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Got it!", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PersonaHeroBadge() {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    )
    Box(
        modifier = Modifier
            .size(76.dp)
            .background(gradient, shape = RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Face,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun PersonaFeatureBullet(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
