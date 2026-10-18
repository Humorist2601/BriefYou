package me.ash.reader.domain.service

import me.ash.reader.brief.feeds.FeedIconOverrides // Brief You
import me.ash.reader.brief.feeds.FeedOrdering

import android.content.Context
import be.ceau.opml.OpmlWriter
import be.ceau.opml.entity.Body
import be.ceau.opml.entity.Head
import be.ceau.opml.entity.Opml
import be.ceau.opml.entity.Outline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.OPMLDataSource
import me.ash.reader.ui.ext.currentAccountId
import me.ash.reader.ui.ext.getDefaultGroupId
import java.io.InputStream
import java.util.*
import javax.inject.Inject

/**
 * Supports import and export from OPML files.
 */
class OpmlService @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val accountService: AccountService,
    private val rssService: RssService,
    private val OPMLDataSource: OPMLDataSource,
    private val iconOverrides: FeedIconOverrides, // Brief You
    private val feedOrdering: FeedOrdering,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Imports OPML file.
     *
     * @param [inputStream] input stream of OPML file
     */
    @Throws(Exception::class)
    suspend fun saveToDatabase(inputStream: InputStream) {
        withContext(ioDispatcher) {
            val defaultGroup = groupDao.queryById(getDefaultGroupId(context.currentAccountId))!!
            val groupWithFeedList =
                OPMLDataSource.parseFileInputStream(inputStream, defaultGroup, context.currentAccountId)
            // Groups you already have are reused (matched by name) instead of being created again, // Brief You
            // so restoring a backup puts feeds back into your existing groups.
            val knownGroups =
                groupDao
                    .queryAll(context.currentAccountId)
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()
            groupWithFeedList.forEach { groupWithFeed ->
                var group = groupWithFeed.group
                if (group != defaultGroup) {
                    val key = group.name.trim().lowercase()
                    val existing = knownGroups[key]
                    if (existing != null) {
                        group = existing
                    } else {
                        groupDao.insert(group)
                        knownGroups[key] = group
                    }
                }
                val repeatList = mutableListOf<Feed>()
                groupWithFeed.feeds.forEach {
                    it.groupId = group.id
                    if (rssService.get().isFeedExist(it.url)) {
                        repeatList.add(it)
                    }
                }
                val toInsert = (groupWithFeed.feeds subtract repeatList.toSet()).toList()
                feedDao.insertList(toInsert)
                // Custom icons from the file's additional info (emoji or embedded image).
                toInsert.forEach { feed -> feed.icon?.let { iconOverrides.adopt(feed.id, it) } }
            }
        }
    }

    /**
     * Exports OPML file.
     */
    @Throws(Exception::class)
    suspend fun saveToString(accountId: Int, attachInfo: Boolean): String {
        val defaultGroup = groupDao.queryById(getDefaultGroupId(accountId))
        return OpmlWriter().write(
            Opml(
                "2.0",
                Head(
                    accountService.getCurrentAccount().name,
                    Date().toString(), null, null, null,
                    null, null, null, null,
                    null, null, null, null,
                ),
                // In the user's order (see FeedOrdering), so the file keeps it too. // Brief You
                Body(feedOrdering.sort(groupDao.queryAllGroupWithFeed(accountId), accountId).map {
                    Outline(
                        mutableMapOf(
                            "text" to it.group.name,
                            "title" to it.group.name,
                        ).apply {
                            if (attachInfo) {
                                put("isDefault", (it.group.id == defaultGroup?.id).toString())
                            }
                        },
                        it.feeds.map { feed ->
                            Outline(
                                mutableMapOf(
                                    "text" to feed.name,
                                    "title" to feed.name,
                                    "xmlUrl" to feed.url,
                                    "htmlUrl" to feed.url
                                ).apply {
                                    if (attachInfo) {
                                        put("isNotification", feed.isNotification.toString())
                                        put("isFullContent", feed.isFullContent.toString())
                                        put("isBrowser", feed.isBrowser.toString())
                                        iconOverrides.exportValue(feed.id)?.let { // Brief You
                                            put("customIcon", it)
                                        }
                                    }
                                },
                                listOf()
                            )
                        }
                    )
                })
            )
        )!!
    }

    private fun getDefaultGroupId(accountId: Int): String = accountId.getDefaultGroupId()
}
