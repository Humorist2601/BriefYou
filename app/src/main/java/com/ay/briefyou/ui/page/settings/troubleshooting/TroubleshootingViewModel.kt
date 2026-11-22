package com.ay.briefyou.ui.page.settings.troubleshooting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ay.briefyou.domain.data.Log
import com.ay.briefyou.domain.data.SyncLogger
import com.ay.briefyou.domain.service.AccountService
import com.ay.briefyou.domain.service.OpmlService
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.infrastructure.di.ApplicationScope
import com.ay.briefyou.infrastructure.di.DefaultDispatcher
import com.ay.briefyou.infrastructure.di.IODispatcher
import com.ay.briefyou.infrastructure.di.MainDispatcher

@HiltViewModel
class TroubleshootingViewModel
@Inject
constructor(
    private val accountService: AccountService,
    private val rssService: RssService,
    private val opmlService: OpmlService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    val workManager: WorkManager,
    private val syncLogger: SyncLogger,
) : ViewModel() {

    suspend fun getSyncLogs(): List<Log> = syncLogger.list()

    fun clearSyncLogs() = viewModelScope.launch { syncLogger.clear() }
}
