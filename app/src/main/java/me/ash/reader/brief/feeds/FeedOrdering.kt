package me.ash.reader.brief.feeds

import me.ash.reader.domain.service.AccountService

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.group.GroupWithFeed

/** How groups, or the feeds inside a group, are ordered. */
@Serializable
enum class SortMode {
    /** The order they were added in. */
    Default,
    NameAsc,
    NameDesc,
    /** The most unread (or starred, or total, following the filter you are viewing) first. */
    MostUnread,
    /** The fewest unread (or starred, or total) first. */
    FewestUnread,
    /** From older versions, which had a manual order. Shown and handled as [Default]. */
    Manual;

    val normalized: SortMode
        get() = if (this == Manual) Default else this
}

/**
 * Which sort is chosen for one account's groups and for the feeds inside them. Settings saved by
 * older versions may hold more fields (a manual order); they are ignored.
 */
@Serializable
data class OrderSettings(
    val groupMode: SortMode = SortMode.Default,
    val feedMode: SortMode = SortMode.Default,
) {
    val isDefault: Boolean
        get() = groupMode.normalized == SortMode.Default && feedMode.normalized == SortMode.Default
}

/**
 * The sort as it is saved in a backup. Backups from older versions may also list a manual order;
 * it is ignored.
 */
@Serializable
data class OrderBackup(
    val groupMode: SortMode = SortMode.Default,
    val feedMode: SortMode = SortMode.Default,
)

/**
 * Brief You's sorting of groups and feeds, kept separately for every account: the order they were
 * added in, by name, or by count. It only sorts what is shown; nothing in the database changes.
 */
@Singleton
class FeedOrdering
@Inject
constructor(
    @ApplicationContext context: Context,
    private val accountService: AccountService,
) {
    private val prefs = context.getSharedPreferences("feed_order", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _version = MutableStateFlow(0)

    /** Changes every time the sort changes, so lists can re-sort themselves. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun key(accountId: Int) = "order.$accountId"

    fun settings(accountId: Int = accountService.getCurrentAccountId()): OrderSettings =
        prefs.getString(key(accountId), null)?.let {
            runCatching { json.decodeFromString<OrderSettings>(it) }.getOrNull()
        } ?: OrderSettings()

    fun update(
        accountId: Int = accountService.getCurrentAccountId(),
        transform: (OrderSettings) -> OrderSettings,
    ) {
        val next = transform(settings(accountId))
        prefs.edit {
            if (next == OrderSettings()) remove(key(accountId))
            else putString(key(accountId), json.encodeToString(next))
        }
        _version.value++
    }

    /**
     * [source] in the order of the account's settings, sorted again whenever the order changes.
     * Used by Read You's GroupWithFeedsListUseCase, so every list of groups follows the order.
     */
    fun sorted(
        source: Flow<List<GroupWithFeed>>,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        started: SharingStarted = SharingStarted.Eagerly,
    ): StateFlow<List<GroupWithFeed>> =
        combine(source, version) { groups, _ -> sort(groups) }
            .flowOn(dispatcher)
            .stateIn(scope, started, emptyList())

    /** Puts [groups] (and the feeds inside them) in the order of the account's settings. */
    fun sort(
        groups: List<GroupWithFeed>,
        accountId: Int = accountService.getCurrentAccountId(),
    ): List<GroupWithFeed> = sort(groups, settings(accountId))

    fun sort(groups: List<GroupWithFeed>, settings: OrderSettings): List<GroupWithFeed> {
        if (settings.isDefault) return groups
        val withSortedFeeds =
            groups.map { item ->
                item.copy(
                    feeds =
                        order(
                                items = item.feeds,
                                mode = settings.feedMode,
                                name = { it.name },
                                count = { it.important },
                            )
                            .toMutableList()
                )
            }
        return order(
            items = withSortedFeeds,
            mode = settings.groupMode,
            name = { it.group.name },
            count = { group -> group.feeds.sumOf { it.important } },
        )
    }

    private fun <T> order(
        items: List<T>,
        mode: SortMode,
        name: (T) -> String,
        count: (T) -> Int,
    ): List<T> =
        // The sorts are stable, so ties keep the order they were added in.
        when (mode.normalized) {
            SortMode.NameAsc ->
                items.sortedWith(Comparator { a, b -> name(a).compareTo(name(b), true) })
            SortMode.NameDesc ->
                items.sortedWith(Comparator { a, b -> name(b).compareTo(name(a), true) })
            SortMode.MostUnread -> items.sortedByDescending(count)
            SortMode.FewestUnread -> items.sortedBy(count)
            else -> items
        }

    /** The sort as it can be saved in a backup, or null while nothing is sorted. */
    fun export(accountId: Int = accountService.getCurrentAccountId()): OrderBackup? {
        val settings = settings(accountId)
        if (settings.isDefault) return null
        return OrderBackup(
            groupMode = settings.groupMode.normalized,
            feedMode = settings.feedMode.normalized,
        )
    }

    /** Applies the sort from a backup. */
    fun import(backup: OrderBackup, accountId: Int = accountService.getCurrentAccountId()) {
        update(accountId) {
            it.copy(groupMode = backup.groupMode.normalized, feedMode = backup.feedMode.normalized)
        }
    }
}
