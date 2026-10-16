package me.ash.reader.domain.service

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.domain.repository.GroupDao

/** How groups, or the feeds inside a group, are ordered. */
@Serializable
enum class SortMode {
    /** The order they were added in. */
    Default,
    NameAsc,
    NameDesc,
    /** The most unread (or starred, or total, following the filter you are viewing) first. */
    MostUnread,
    /** The order you set by dragging. */
    Manual,
}

/**
 * How one account's groups and feeds are ordered.
 *
 * The manual orders are lists of ids; items that are not in a list yet (new groups and feeds) come
 * after the ones that are, in their default order.
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
        get() = groupMode == SortMode.Default && feedMode == SortMode.Default
}

/**
 * The order in a form that works on any phone: ids change when a backup is restored on another
 * device, so groups are named and feeds are identified by their URL.
 */
@Serializable
data class OrderBackup(
    val groupMode: SortMode = SortMode.Default,
    val feedMode: SortMode = SortMode.Default,
    /** Group names, in order. */
    val groups: List<String> = emptyList(),
    /** Group name to the URLs of its feeds, in order. */
    val feeds: Map<String, List<String>> = emptyMap(),
)

/**
 * Brief You's order for groups and feeds: sort by name, by count, or by hand, kept separately for
 * every account. It is only an ordering on top of the data (nothing in the database changes), and
 * it is written into OPML exports and backups.
 */
@Singleton
class FeedOrdering
@Inject
constructor(
    @ApplicationContext context: Context,
    private val accountService: AccountService,
    private val groupDao: GroupDao,
) {
    private val prefs = context.getSharedPreferences("feed_order", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _version = MutableStateFlow(0)

    /** Changes every time the order changes, so lists can re-sort themselves. */
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
                                manual = settings.feedOrder[item.group.id].orEmpty(),
                                id = { it.id },
                                name = { it.name },
                                count = { it.important },
                            )
                            .toMutableList()
                )
            }
        return order(
            items = withSortedFeeds,
            mode = settings.groupMode,
            manual = settings.groupOrder,
            id = { it.group.id },
            name = { it.group.name },
            count = { group -> group.feeds.sumOf { it.important } },
        )
    }

    private fun <T> order(
        items: List<T>,
        mode: SortMode,
        manual: List<String>,
        id: (T) -> String,
        name: (T) -> String,
        count: (T) -> Int,
    ): List<T> =
        when (mode) {
            SortMode.Default -> items
            SortMode.NameAsc -> items.sortedWith(Comparator { a, b -> name(a).compareTo(name(b), true) })
            SortMode.NameDesc -> items.sortedWith(Comparator { a, b -> name(b).compareTo(name(a), true) })
            // Stable, so ties keep their default order.
            SortMode.MostUnread -> items.sortedByDescending(count)
            SortMode.Manual -> {
                val position = manual.withIndex().associate { it.value to it.index }
                // Stable: anything not in the list yet keeps its default order, after the rest.
                items.sortedBy { position[id(it)] ?: Int.MAX_VALUE }
            }
        }

    /** The order as it can be saved in a backup, or null while everything is in default order. */
    suspend fun export(accountId: Int = accountService.getCurrentAccountId()): OrderBackup? {
        val settings = settings(accountId)
        if (settings.isDefault) return null
        val groups = groupDao.queryAllGroupWithFeed(accountId)
        val groupName = groups.associate { it.group.id to it.group.name }
        val feedUrl = groups.flatMap { it.feeds }.associate { it.id to it.url }
        return OrderBackup(
            groupMode = settings.groupMode,
            feedMode = settings.feedMode,
            groups = settings.groupOrder.mapNotNull { groupName[it] },
            feeds =
                settings.feedOrder
                    .mapNotNull { (groupId, feedIds) ->
                        val name = groupName[groupId] ?: return@mapNotNull null
                        name to feedIds.mapNotNull { feedUrl[it] }
                    }
                    .toMap(),
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
        update(accountId) {
            it.copy(
                groupMode = backup.groupMode,
                feedMode = backup.feedMode,
                groupOrder = backup.groups.mapNotNull { name -> groupId[name.trim().lowercase()] },
                feedOrder =
                    backup.feeds
                        .mapNotNull { (name, urls) ->
                            val id = groupId[name.trim().lowercase()] ?: return@mapNotNull null
                            id to urls.mapNotNull { url -> feedId[url] }
                        }
                        .toMap(),
            )
        }
    }
}
