package com.charles.ollama.client.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.ollama.client.R
import com.charles.ollama.client.domain.model.Server
import com.charles.ollama.client.ui.localization.translated
import com.charles.ollama.client.ui.navigation.InterstitialAdManagerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * One-time getting-started sheet for users with no default server, plus replay from Settings
 * via [com.charles.ollama.client.ads.AdGate.requestSetupTutorialReplay].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRunSetupTutorialSheet(
    defaultServer: Server?,
    onFirstRunTutorialFinished: () -> Unit,
) {
    val context = LocalContext.current
    val adGate = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterstitialAdManagerEntryPoint::class.java,
        ).adGate()
    }

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(defaultServer) {
        if (defaultServer == null && !adGate.hasSeenSetupTutorial()) {
            delay(500)
            if (!adGate.hasSeenSetupTutorial()) {
                visible = true
            }
        }
    }

    LaunchedEffect(Unit) {
        adGate.setupTutorialReplayRequests.collectLatest {
            visible = true
        }
    }

    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val litertLabel = translated(stringResource(R.string.servers_add_litert))

    val dismiss: () -> Unit = {
        val wasUnseen = !adGate.hasSeenSetupTutorial()
        adGate.markSetupTutorialSeen()
        visible = false
        if (wasUnseen) onFirstRunTutorialFinished()
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("setup_tutorial_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = translated(stringResource(R.string.setup_tutorial_title)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = translated(stringResource(R.string.setup_tutorial_remote_heading)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = translated(stringResource(R.string.setup_tutorial_remote_body)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = translated(stringResource(R.string.setup_tutorial_ondevice_heading)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = translated(stringResource(R.string.setup_tutorial_ondevice_body, litertLabel)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = dismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_tutorial_got_it"),
            ) {
                Text(translated(stringResource(R.string.setup_tutorial_got_it)))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
