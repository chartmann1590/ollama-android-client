package com.charles.ollama.client.ui.rewards

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.ads.RewardTier
import com.charles.ollama.client.ads.RewardedAdState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    viewModel: RewardsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isPremium by viewModel.isPremium.collectAsState()
    val credits by viewModel.credits.collectAsState()
    val adFreeUntil by viewModel.adFreeUntilMs.collectAsState()
    val now by viewModel.nowMs.collectAsState()
    val rewardedState by viewModel.rewardedAdState.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val spentToday by viewModel.creditsSpentToday.collectAsState()
    val earnedToday by viewModel.creditsEarnedToday.collectAsState()
    val maxPerDay = viewModel.maxCreditsPerDay
    val redeemableToday = (maxPerDay - spentToday).coerceAtLeast(0)
    val earnableToday = (maxPerDay - earnedToday).coerceAtLeast(0)

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toast) {
        val message = toast
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearToast()
        }
    }

    // Make sure an ad is queued up when the screen opens.
    LaunchedEffect(activity) {
        if (activity != null) viewModel.rewardedManager().loadAd(activity)
    }

    val remainingMs = (adFreeUntil - now).coerceAtLeast(0L)
    val isAdFree = remainingMs > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CreditsHeroCard(
                credits = credits,
                earnedToday = earnedToday,
                spentToday = spentToday,
                maxPerDay = maxPerDay
            )

            if (!isPremium) {
                GoPremiumCard(onClick = onNavigateToPaywall)
            }

            AdFreeStatusCard(isActive = isAdFree, remainingMs = remainingMs)

            EarnSection(
                rewardedState = rewardedState,
                creditsPerAd = viewModel.creditsPerAd,
                earnableToday = earnableToday,
                maxPerDay = maxPerDay,
                onWatchAd = {
                    if (earnableToday <= 0) {
                        viewModel.showEarnLimitToast()
                    } else if (activity != null) {
                        viewModel.rewardedManager().showAd(
                            activity = activity,
                            onReward = {},
                            onClosed = {}
                        )
                    }
                }
            )

            RedeemSection(
                tiers = viewModel.tiers,
                credits = credits,
                redeemableToday = redeemableToday,
                maxPerDay = maxPerDay,
                onRedeem = viewModel::redeem
            )

            HowItWorksCard(maxPerDay = maxPerDay)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GoPremiumCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Stars,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Go ad-free forever",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Skip the watching — remove every ad with Premium.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            FilledTonalButton(onClick = onClick) {
                Text("Upgrade")
            }
        }
    }
}

@Composable
private fun CreditsHeroCard(
    credits: Int,
    earnedToday: Int,
    spentToday: Int,
    maxPerDay: Int
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val gradient = Brush.linearGradient(listOf(primary, tertiary))

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your reward credits",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = credits.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 56.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (credits == 1) "credit" else "credits",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Text(
                    text = "Watch a short ad to earn credits, then redeem them to mute every other ad in the app.",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DailyChip(label = "Earned today", value = "$earnedToday / $maxPerDay")
                    DailyChip(label = "Redeemed today", value = "$spentToday / $maxPerDay")
                }
            }
        }
    }
}

@Composable
private fun DailyChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AdFreeStatusCard(isActive: Boolean, remainingMs: Long) {
    val containerColor = if (isActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (isActive)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    val pulseAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.55f,
        label = "ad-free-alpha"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = onContainer.copy(alpha = pulseAlpha),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isActive) "Ads disabled" else "No ad-free time active",
                    color = onContainer,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isActive)
                        "Time remaining: ${formatRemaining(remainingMs)}"
                    else
                        "Redeem credits below to mute banners, native ads, and full-screen ads.",
                    color = onContainer.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun EarnSection(
    rewardedState: RewardedAdState,
    creditsPerAd: Int,
    earnableToday: Int,
    maxPerDay: Int,
    onWatchAd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(icon = Icons.Default.Bolt, title = "Earn credits")
            Text(
                text = "Watch a short rewarded video to earn $creditsPerAd credit. " +
                    "You can earn up to $maxPerDay credits per day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val isLoading = rewardedState == RewardedAdState.LOADING
            val isReady = rewardedState == RewardedAdState.READY
            val isFailed = rewardedState == RewardedAdState.FAILED
            val atDailyCap = earnableToday <= 0

            Button(
                onClick = onWatchAd,
                enabled = !atDailyCap && isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (atDailyCap) {
                    Icon(imageVector = Icons.Default.HourglassEmpty, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Daily limit reached", fontWeight = FontWeight.SemiBold)
                } else if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading ad…")
                } else {
                    Icon(imageVector = Icons.Default.Movie, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isReady) "Watch ad — earn $creditsPerAd credit" else "Ad unavailable — tap to retry",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (atDailyCap) {
                Text(
                    text = "You've hit the daily earn limit. Come back tomorrow to watch more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "$earnableToday more credit${if (earnableToday == 1) "" else "s"} you can earn today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isFailed && !atDailyCap,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = "Couldn't load an ad right now. Check your connection and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RedeemSection(
    tiers: List<RewardTier>,
    credits: Int,
    redeemableToday: Int,
    maxPerDay: Int,
    onRedeem: (RewardTier) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(icon = Icons.Outlined.LocalActivity, title = "Redeem")
            Text(
                text = "Spend credits to disable banners, native, interstitial, and app-open ads. " +
                    "You can redeem up to $maxPerDay credits per day; times stack on top of any time you have left.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (redeemableToday <= 0)
                    "Daily redeem limit reached — try again tomorrow."
                else
                    "$redeemableToday more credit${if (redeemableToday == 1) "" else "s"} you can redeem today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            tiers.forEach { tier ->
                TierRow(
                    tier = tier,
                    credits = credits,
                    redeemableToday = redeemableToday,
                    onRedeem = { onRedeem(tier) }
                )
            }
        }
    }
}

@Composable
private fun TierRow(
    tier: RewardTier,
    credits: Int,
    redeemableToday: Int,
    onRedeem: () -> Unit
) {
    val canAfford = credits >= tier.cost
    val withinDailyLimit = tier.cost <= redeemableToday
    val canRedeem = canAfford && withinDailyLimit

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (canRedeem)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tier.label + " ad-free",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${tier.cost} ${if (tier.cost == 1) "credit" else "credits"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!withinDailyLimit && canAfford) {
                    Text(
                        text = "Over today's limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Button(
                onClick = onRedeem,
                enabled = canRedeem,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when {
                        !canAfford -> "Need ${tier.cost - credits}"
                        !withinDailyLimit -> "Maxed today"
                        else -> "Redeem"
                    }
                )
            }
        }
    }
}

@Composable
private fun HowItWorksCard(maxPerDay: Int) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(icon = Icons.Default.Info, title = "How it works")
            BulletRow(text = "Tap “Watch ad” to view a short rewarded video.")
            BulletRow(text = "Earn 1 credit each time you finish watching.")
            BulletRow(text = "Daily limit: earn up to $maxPerDay credits per day and redeem up to $maxPerDay credits per day.")
            BulletRow(text = "Redeem credits below to disable ads for set periods.")
            BulletRow(text = "Active ad-free time keeps banners, native ads, interstitials, and app-open ads hidden until it expires.")
            BulletRow(text = "Redemptions stack on top of any time you already have left.")
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

internal fun formatRemaining(ms: Long): String {
    if (ms <= 0) return "0s"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return buildString {
        if (h > 0) append("${h}h ")
        if (h > 0 || m > 0) append("${m}m ")
        append("${s}s")
    }.trim()
}
