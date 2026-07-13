package com.charles.ollama.client.ui.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charles.ollama.client.domain.model.Server
import com.charles.ollama.client.ui.localization.translated
import com.charles.ollama.client.ui.navigation.InterstitialAdManagerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

/**
 * Bottom sheet that introduces the rewards system on first launch. Shows
 * once per install (persisted in [com.charles.ollama.client.ads.AdGate]),
 * after a brief delay so the underlying screen has time to settle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewRewardsSheet(
    defaultServer: Server?,
    setupTutorialFinishedSignal: Int,
    onNavigateToRewards: () -> Unit,
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
        if (adGate.hasSeenWhatsNew()) return@LaunchedEffect
        if (!adGate.hasSeenSetupTutorial() && defaultServer == null) return@LaunchedEffect
        delay(900)
        if (!adGate.hasSeenWhatsNew()) {
            visible = true
        }
    }

    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dismiss: () -> Unit = {
        adGate.markWhatsNewSeen()
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
            HeroBadge()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = translated("New: Reward credits"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = translated("Earn credits by watching short ads, then spend them to mute every other ad in the app."),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            FeatureBullet(
                icon = Icons.Default.MovieFilter,
                title = translated("Watch a quick ad"),
                body = translated("Each rewarded video earns you 1 credit.")
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureBullet(
                icon = Icons.Default.Bolt,
                title = translated("Spend on quiet time"),
                body = translated("1 credit = 30 minutes ad-free, or 3 credits = 2 hours. Earn and redeem up to 3 credits per day.")
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureBullet(
                icon = Icons.Default.Timer,
                title = translated("Enjoy quiet time"),
                body = translated("Banners, native ads, interstitials, and app-open ads all stay hidden until your time runs out.")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    dismiss()
                    onNavigateToRewards()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.CardGiftcard, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(translated("Try it now"), fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                Text(translated("Maybe later"))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeroBadge() {
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
            imageVector = Icons.Default.CardGiftcard,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun FeatureBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
