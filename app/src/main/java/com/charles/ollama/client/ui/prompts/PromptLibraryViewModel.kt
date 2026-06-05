package com.charles.ollama.client.ui.prompts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.repository.PromptRepository
import com.charles.ollama.client.domain.prompt.PromptPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptLibraryViewModel @Inject constructor(
    private val promptRepository: PromptRepository,
    private val chatThreadDao: ChatThreadDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Thread to apply a chosen preset to; -1 means "manage only". */
    val threadId: Long = savedStateHandle.get<Long>("threadId") ?: -1L

    val builtIns: List<PromptPreset> = promptRepository.builtIns

    val customPresets: StateFlow<List<PromptPreset>> =
        promptRepository.customPresets.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun applyToThread(preset: PromptPreset, onApplied: () -> Unit) {
        if (threadId <= 0L) {
            _toast.value = "Open a chat to apply a persona"
            return
        }
        viewModelScope.launch {
            chatThreadDao.setSystemPrompt(threadId, preset.text)
            _toast.value = "Applied \"${preset.title}\" to this chat"
            onApplied()
        }
    }

    fun saveCustom(title: String, text: String, existingId: String? = null) {
        viewModelScope.launch {
            promptRepository.saveCustom(title, text, existingId)
        }
    }

    fun deleteCustom(id: String) {
        viewModelScope.launch { promptRepository.deleteCustom(id) }
    }

    fun clearToast() { _toast.value = null }
}
