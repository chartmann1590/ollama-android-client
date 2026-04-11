package com.charles.ollama.client.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.update.UpdateChecker
import com.charles.ollama.client.data.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()

    private var hasRunInitialCheck = false

    fun checkOnce() {
        if (hasRunInitialCheck) return
        hasRunInitialCheck = true
        viewModelScope.launch {
            _availableUpdate.value = updateChecker.checkForUpdate(force = false)
        }
    }

    fun dismiss() {
        val current = _availableUpdate.value ?: return
        updateChecker.dismiss(current.tag)
        _availableUpdate.value = null
    }

    fun clear() {
        _availableUpdate.value = null
    }
}
