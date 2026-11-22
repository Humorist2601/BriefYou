package com.ay.briefyou.ui.page.settings.tips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.ay.briefyou.domain.service.AppService
import com.ay.briefyou.infrastructure.net.Download
import com.ay.briefyou.ui.ext.isGitHub
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appService: AppService,
) : ViewModel() {

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    var updateJob: Job? = null

    fun checkUpdate(
        preProcessor: suspend () -> Unit = {},
        postProcessor: suspend (Boolean) -> Unit = {},
    ) {
        if (!isGitHub) return
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            preProcessor()
            appService.checkUpdate().let {
                it?.let {
                    if (it) {
                        showDialog()
                    } else {
                        hideDialog()
                    }
                    postProcessor(it)
                }
            }
        }
    }

    fun showDialog() {
        _updateUiState.update {
            it.copy(
                updateDialogVisible = true
            )
        }
    }

    fun hideDialog() {
        _updateUiState.update {
            it.copy(
                updateDialogVisible = false
            )
        }
    }

}

data class UpdateUiState(
    val updateDialogVisible: Boolean = false,
    val downloadFlow: Flow<Download> = emptyFlow(),
)
