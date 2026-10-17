package me.ash.reader.domain.service

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.domain.model.order.SortPosition
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.repository.SortPositionDao
import me.ash.reader.infrastructure.di.ApplicationScope

/** How groups, or the feeds inside a group, are ordered. */
@Serializable
enum class SortMode {
    /**
     * "Your order": the order you set by dragging, stored in the database. Groups and feeds you
     * have not placed yet come after, in the order they were added.
     */
    Default,
    NameAsc,
    NameDesc,
    /** The most unread (or starred, or total, following the filter you are viewing) first. */
    MostUnread,
    /** The fewest unread (or starred, or total) first. */
    FewestUnread,
    /**
     * From older versions, where dragging was a separate mode. It is now the same as [Default]:
     * dragging changes your order itself.
     */
    Manual;

    /** [Manual] is shown and handled as [Default]. */
    val normalized: SortMode
        get() = if (this == Manual) Default else this
}

/**
 * Which sort is chosen for one account's groups and for the feeds inside them. The order itself
 * ("Your order") is in the database (see [SortPosition]).
 *
 * [groupOrder] and [feedOrder] are only read once, to move an order made with an older version
 * into the database.
 */
@Serializable
data class OrderSettings(
    val groupMode: SortMode = SortMode.Default,
    val feedMode: SortMode = SortMode.Default,
    val groupOrder: List<String> = emptyList(),
    /** Group id to the feed ids of that group, in order. */
    val feedOrder: Map<String, List<String>> = emptyMap(),
) {
    val isDefault: Boolean
        get() = groupMode.normalized == SortMode.Default && feedMode.normalized == SortMode.Default
}

/**
 * The order in a form that works on any phone: ids change when a backup is restored on another
 * device, so groups are named and feeds are identified by their URL.
 */
@Serializable
data class OrderBackup(
    val groupMode: SortMode = SortMode.Default,
    val feedMode: SortMode = SortMode.Default,
    /** Group names, in your order. */
    val groups: List<String> = emptyList(),
    /** Group name to the URLs of its feeds, in your order. */
    val feeds: Map<String, List<String>> = emptyMap(),
)

/**
 * Brief You's order for groups and feeds, kept separately for every account.
 *
 * "Your order" (the default) lives in the database: dragging writes it, and it is the order the
 * whole app uses (the Feeds page, Triage's next group, OPML exports and backups). Sorting by name or
 * by count is a view on top of it, remembered per account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FeedOrdering
@Inject
constructor(
    @ApplicationContext context: Context,
    private val accountService: AccountService,
    private val groupDao: GroupDao,
    private val sortPositionDao: SortPositionDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("feed_order", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _version = MutableStateFlow(0)

    /** Changes every time the order or the sort changes, so lists can re-sort themselves. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** The current account's positions, kept in memory so sorting is quick. */
    @Volatile private var cachedAccountId: Int? = null
    @Volatile private var cachedPositions: Positions = Positions.EMPTY

    init {
        applicationScope.launch {
            accountService.currentAccountIdFlow.filterNotNull().collectLatest { accountId ->
                migrateOldManualOrder(accountId)
                sortPositionDao.queryAllAsFlow(accountId).collect { rows ->
                    cachedPositions = Positions.of(rows)
                    cachedAccountId = accountId
                    _version.value++
                }
            }
        }
    }

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

    private suspend fun positions(accountId: Int): Positions =
        if (accountId == cachedAccountId) cachedPositions
        else Positions.of(sortPositionDao.queryAll(accountId))

    /** Writes your order of the groups (all of them, in this order). */
    suspend fun setGroupOrder(
        groupIds: List<String>,
        accountId: Int = accountService.getCurrentAccountId(),
    ) {
        sortPositionDao.replaceOrder(accountId, SortPosition.KIND_GROUP, groupIds)
    }

    /** Writes your order of the feeds inside one group. */
    suspend fun setFeedOrder(
        feedIds: List<String>,
        accountId: Int = accountService.getCurrentAccountId(),
    ) {
        sortPositionDao.setOrder(accountId, SortPosition.KIND_FEED, feedIds)
    }

    /** Forgets your order: everything goes back to the order it was added in. */
    suspend fun clearOrder(accountId: Int = accountService.getCurrentAccountId()) {
        sortPositionDao.deleteByAccountId(accountId)
    }

    /** Puts [groups] (and the feeds inside them) in the order of the account's settings. */
    suspend fun sort(
        groups: List<GroupWithFeed>,
        accountId: Int = accountService.getCurrentAccountId(),
    ): List<GroupWithFeed> = sort(groups, settings(accountId), accountId)

    suspend fun sort(
        groups: List<GroupWithFeed>,
        settings: OrderSettings,
        accountId: Int = accountService.getCurrentAccountId(),
    ): List<GroupWithFeed> {
        val positions = positions(accountId)
        if (settings.isDefault && positions.isEmpty) return groups
        val withSortedFeeds =
            groups.map { item ->
                item.copy(
                    feeds =
                        order(
                                items = item.feeds,
                                mode = settings.feedMode,
                                position = { positions.feeds[it.id] },
                                name = { it.name },
                                count = { it.important },
                            )
                            .toMutableList()
                )
            }
        return order(
            items = withSortedFeeds,
            mode = settings.groupMode,
            position = { positions.groups[it.group.id] },
            name = { it.group.name },
            count = { group -> group.feeds.sumOf { it.important } },
        )
    }

    private fun <T> order(
        items: List<T>,
        mode: SortMode,
        position: (T) -> Int?,
        name: (T) -> String,
        count: (T) -> Int,
    ): List<T> {
        // Your order first; the other sorts are stable, so ties keep your order.
        val yours = items.sortedBy { position(it) ?: Int.MAX_VALUE }
        return when (mode.normalized) {
            SortMode.NameAsc ->
                yours.sortedWith(Comparator { a, b -> name(a).compareTo(name(b), true) })
            SortMode.NameDesc ->
                yours.sortedWith(Comparator { a, b -> name(b).compareTo(name(a), true) })
            SortMode.MostUnread -> yours.sortedByDescending(count)
            SortMode.FewestUnread -> yours.sortedBy(count)
            else -> yours
        }
    }

    /**
     * Older versions kept a dragged order as a separate "manual" mode in the settings. Moves it into
     * the database once, so it becomes your order.
     */
    private suspend fun migrateOldManualOrder(accountId: Int) {
        val old = settings(accountId)
        if (old.groupOrder.isEmpty() && old.feedOrder.isEmpty()) return
        if (old.groupOrder.isNotEmpty()) setGroupOrder(old.groupOrder, accountId)
        old.feedOrder.values.forEach { setFeedOrder(it, accountId) }
        update(accountId) {
            it.copy(
                groupMode = it.groupMode.normalized,
                feedMode = it.feedMode.normalized,
                groupOrder = emptyList(),
                feedOrder = emptyMap(),
            )
        }
    }

    /** The order as it can be saved in a backup, or null while nothing has been ordered. */
    suspend fun export(accountId: Int = accountService.getCurrentAccountId()): OrderBackup? {
        val settings = settings(accountId)
        val positions = positions(accountId)
        if (settings.isDefault && positions.isEmpty) return null
        // Your order (whatever sort is chosen), so it comes back the same on restore.
        val yours = sort(groupDao.queryAllGroupWithFeed(accountId), OrderSettings(), accountId)
        return OrderBackup(
            groupMode = settings.groupMode.normalized,
            feedMode = settings.feedMode.normalized,
            groups = yours.map { it.group.name },
            feeds = yours.associate { item -> item.group.name to item.feeds.map { it.url } },
        )
    }

    /**
     * Applies an order from a backup. Groups are matched by name and feeds by URL, so this works
     * after the subscriptions have been restored, whatever ids they got.
     */
    suspend fun import(backup: OrderBackup, accountId: Int = accountService.getCurrentAccountId()) {
        val groups = groupDao.queryAllGroupWithFeed(accountId)
        val groupId = groups.associate { it.group.name.trim().lowercase() to it.group.id }
        val feedId = groups.flatMap { it.feeds }.associate { it.url to it.id }
        val groupIds = backup.groups.mapNotNull { name -> groupId[name.trim().lowercase()] }
        if (groupIds.isNotEmpty()) setGroupOrder(groupIds, accountId)
        backup.feeds.values.forEach { urls ->
            val ids = urls.mapNotNull { url -> feedId[url] }
            if (ids.isNotEmpty()) setFeedOrder(ids, accountId)
        }
        update(accountId) {
            it.copy(groupMode = backup.groupMode.normalized, feedMode = backup.feedMode.normalized)
        }
    }

    /** One account's positions, by id. */
    private class Positions(val groups: Map<String, Int>, val feeds: Map<String, Int>) {
        val isEmpty: Boolean
            get() = groups.isEmpty() && feeds.isEmpty()

        companion object {
            val EMPTY = Positions(emptyMap(), emptyMap())

            fun of(rows: List<SortPosition>): Positions =
                Positions(
                    groups =
                        rows.filter { it.kind == SortPosition.KIND_GROUP }
                            .associate { it.itemId to it.position },
                    feeds =
                        rows.filter { it.kind == SortPosition.KIND_FEED }
                            .associate { it.itemId to it.position },
                )
        }
    }
}
