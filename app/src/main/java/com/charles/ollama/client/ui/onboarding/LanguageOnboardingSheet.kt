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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.preferences.UiPreferences
import com.charles.ollama.client.data.translation.AppLanguage
import com.charles.ollama.client.data.translation.AppLanguages
import com.charles.ollama.client.data.translation.TranslationRepository
import com.charles.ollama.client.data.translation.TranslationStatus
import com.charles.ollama.client.ui.localization.translated
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageOnboardingViewModel @Inject constructor(
    private val uiPreferences: UiPreferences,
    private val translationRepository: TranslationRepository
) : ViewModel() {
    val complete = uiPreferences.languageOnboardingComplete
    val languageTag = translationRepository.languageTag
    val status = translationRepository.status
    val supportedLanguages: List<AppLanguage> = translationRepository.supportedLanguages

    fun selectLanguage(languageTag: String) {
        viewModelScope.launch {
            translationRepository.selectLanguage(languageTag)
        }
    }

    fun finish() {
        translationRepository.completeLanguageOnboarding()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageOnboardingSheet(
    viewModel: LanguageOnboardingViewModel = hiltViewModel()
) {
    val complete by viewModel.complete.collectAsState()
    if (complete) return

    val languageTag by viewModel.languageTag.collectAsState()
    val status by viewModel.status.collectAsState()
    val selected = AppLanguages.find(languageTag)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = viewModel::finish,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("language_onboarding_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = translated("Choose your language"),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            LanguageSelector(
                languages = viewModel.supportedLanguages,
                selected = selected,
                onSelect = viewModel::selectLanguage,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text(
                        text = translated("Translations are generated on this device and may not always be accurate. Be careful with important information."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TranslationStatusText(status)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = viewModel::finish,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_onboarding_continue"),
                enabled = status !is TranslationStatus.Downloading,
            ) {
                Text(translated("Continue"))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    languages: List<AppLanguage>,
    selected: AppLanguage,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (selected.displayName == selected.nativeName) {
                selected.displayName
            } else {
                "${selected.displayName} - ${selected.nativeName}"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(translated("Language")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (language.displayName == language.nativeName) {
                                language.displayName
                            } else {
                                "${language.displayName} - ${language.nativeName}"
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(language.tag)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
fun TranslationStatusText(status: TranslationStatus) {
    when (status) {
        TranslationStatus.Ready -> Text(
            text = translated("Language ready"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TranslationStatus.Downloading -> Text(
            text = translated("Downloading language model for offline use..."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TranslationStatus.Translating -> Text(
            text = translated("Preparing translated app text..."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is TranslationStatus.Error -> Text(
            text = translated("Translation unavailable: %s", status.message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
