package com.ay.briefyou.domain.service

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.account.Account
import com.ay.briefyou.domain.model.account.AccountType
import com.ay.briefyou.domain.model.group.Group
import com.ay.briefyou.domain.repository.AccountDao
import com.ay.briefyou.domain.repository.ArticleDao
import com.ay.briefyou.domain.repository.FeedDao
import com.ay.briefyou.domain.repository.GroupDao
import com.ay.briefyou.infrastructure.di.ApplicationScope
import com.ay.briefyou.infrastructure.preference.SettingsProvider
import com.ay.briefyou.ui.ext.DataStoreKey
import com.ay.briefyou.ui.ext.dataStore
import com.ay.briefyou.ui.ext.getDefaultGroupId
import com.ay.briefyou.ui.ext.put
import com.ay.briefyou.ui.ext.showToast

class AccountService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    settingsProvider: SettingsProvider,
) {

    private val accountIdKey = intPreferencesKey(DataStoreKey.currentAccountId)

    val currentAccountIdFlow =
        settingsProvider.preferencesFlow
            .map { it[accountIdKey] }
            .stateIn(scope = coroutineScope, started = SharingStarted.Eagerly, initialValue = null)

    val currentAccountFlow =
        currentAccountIdFlow
            .combine(getAccounts()) { id, accounts ->
                id?.let { accounts.firstOrNull { it.id == id } }
            }
            .stateIn(scope = coroutineScope, SharingStarted.Eagerly, initialValue = null)

    fun getAccounts(): Flow<List<Account>> = accountDao.queryAllAsFlow()

    fun getAccountFlowById(accountId: Int): Flow<Account?> = accountDao.queryAccount(accountId)

    suspend fun getAccountById(accountId: Int): Account? = accountDao.queryById(accountId)

    fun getCurrentAccount(): Account = runBlocking {
        currentAccountFlow.first { it != null } as Account
    }

    fun getCurrentAccountId(): Int = runBlocking {
        currentAccountIdFlow.first { it != null } as Int
    }

    suspend fun isNoAccount(): Boolean = accountDao.queryAll().isEmpty()

    suspend fun addAccount(account: Account): Account {
        val id = accountDao.insert(account).toInt()
        return account.copy(id = id).also {
            when (it.type) {
                AccountType.Local -> {
                    groupDao.insert(
                        Group(
                            id = it.id!!.getDefaultGroupId(),
                            name = context.getString(R.string.defaults),
                            accountId = it.id,
                        )
                    )
                }
            }
            context.dataStore.put(DataStoreKey.currentAccountId, it.id!!)
            context.dataStore.put(DataStoreKey.currentAccountType, it.type.id)
        }
    }

    private fun getDefaultAccount(): Account =
        Account(type = AccountType.Local, name = context.getString(R.string.app_display_name))

    private suspend fun addDefaultAccount(): Account = addAccount(getDefaultAccount())

    // Brief You: a new account starts empty (no starter feed is added).
    suspend fun initWithDefaultAccount() {
        addDefaultAccount()
    }

    fun getDefaultGroup(): Group =
        getCurrentAccountId().let {
            Group(
                id = it.getDefaultGroupId(),
                name = context.getString(R.string.defaults),
                accountId = it,
            )
        }

    suspend fun update(accountId: Int, block: Account.() -> Account) {
        accountDao.queryById(accountId)?.let { accountDao.update(it.run(block)) }
    }

    suspend fun update(account: Account) = accountDao.update(account)

    suspend fun delete(accountId: Int) {
        if (accountDao.queryAll().size == 1) {
            Looper.myLooper() ?: Looper.prepare()
            context.showToast(context.getString(R.string.must_have_an_account))
            Looper.loop()
            return
        }
        accountDao.queryById(accountId)?.let {
            articleDao.deleteByAccountId(accountId)
            feedDao.deleteByAccountId(accountId)
            groupDao.deleteByAccountId(accountId)
            accountDao.delete(it)
            accountDao.queryAll().getOrNull(0)?.let {
                context.dataStore.put(DataStoreKey.currentAccountId, it.id!!)
                context.dataStore.put(DataStoreKey.currentAccountType, it.type.id)
            }
        }
    }

    suspend fun switch(account: Account) {
        context.dataStore.put(DataStoreKey.currentAccountId, account.id!!)
        context.dataStore.put(DataStoreKey.currentAccountType, account.type.id)
    }
}
