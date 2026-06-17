package com.charles.ollama.client.ui.premium

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.R
import com.charles.ollama.client.data.billing.PremiumPlan
import com.charles.ollama.client.data.billing.PremiumProducts
import com.charles.ollama.client.ui.theme.BrandGradientEnd
import com.charles.ollama.client.ui.theme.BrandGradientStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isPremium by viewModel.isPremium.collectAsState()
    val isWebSyncPremium by viewModel.isWebSyncPremium.collectAsState()
    val details by viewModel.productDetails.collectAsState()
    val options = viewModel.planOptions(details)
    val requiresSignIn = viewModel.requiresSignInToPurchase

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Go Premium") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd))
                    )
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        isWebSyncPremium -> "Web Sync Premium Active"
                        isPremium -> "You're Ad-Free"
                        else -> "Unlock Premium Features"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        isWebSyncPremium -> "Unlimited web messages and no ads. Thanks for your support!"
                        isPremium -> "Thanks for your support — every ad is now disabled."
                        else -> "Unlimited web chat, no banners, no interstitials, no app-open ads."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            if (isPremium || isWebSyncPremium) {
                PremiumBenefits(isWebSyncPremium = isWebSyncPremium)
            } else {
                val webSyncOptions = options.filter { it.plan.productId in PremiumProducts.webSyncIds }
                val adFreeOptions  = options.filter { it.plan.productId !in PremiumProducts.webSyncIds }

                if (requiresSignIn) {
                    SignInRequiredNote()
                    Spacer(Modifier.height(20.dp))
                }

                Text(
                    text = "WEB SYNC + AD FREE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                webSyncOptions.forEach { option ->
                    PlanCard(
                        option = option,
                        onClick = { activity?.let { viewModel.purchase(it, option.plan) } }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "AD FREE ONLY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                adFreeOptions.forEach { option ->
                    PlanCard(
                        option = option,
                        onClick = { activity?.let { viewModel.purchase(it, option.plan) } }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { activity?.let { viewModel.restore(it) } }) {
                    Text("Restore purchase")
                }
                Text(
                    text = "Subscriptions renew automatically until cancelled. Lifetime is a one-time payment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun PlanCard(option: PlanOption, onClick: () -> Unit) {
    val container = if (option.highlight) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = container
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (option.highlight) {
                    Text(
                        text = "POPULAR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = option.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                val displayPrice = option.price ?: option.fallbackPrice
                Text(displayPrice?.let { "Subscribe — $it" } ?: "Continue")
            }
        }
    }
}

@Composable
private fun SignInRequiredNote() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = "Sign in to purchase",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // Flavor-specific: the GitHub flavor explains Stripe; the Play
                    // flavor never renders this note (requiresSignInToPurchase=false)
                    // and its string omits any external-payment wording so the Play
                    // binary contains no such reference.
                    text = stringResource(R.string.paywall_signin_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PremiumBenefits(isWebSyncPremium: Boolean) {
    val benefits = if (isWebSyncPremium) {
        listOf(
            "Unlimited web chat messages",
            "All ads removed across the app",
            "Support ongoing development",
            "Faster, cleaner reading experience"
        )
    } else {
        listOf(
            "All ads removed across the app",
            "Support ongoing development",
            "Faster, cleaner reading experience"
        )
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            benefits.forEach { benefit ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(benefit, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
