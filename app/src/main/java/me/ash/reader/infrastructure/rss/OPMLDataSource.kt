package me.ash.reader.infrastructure.rss

import android.content.Context
import be.ceau.opml.OpmlParser
import be.ceau.opml.entity.Outline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.brief.feeds.FeedIconOverrides // Brief You
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.component.EMOJI_PREFIX
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.spacerDollar
import java.io.InputStream
import java.util.*
import javax.inject.Inject

class OPMLDataSource @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Throws(Exception::class)
    suspend fun parseFileInputStream(
        inputStream: InputStream,
        defaultGroup: Group,
        targetAccountId: Int,
    ): List<GroupWithFeed> {
        val opml = OpmlParser().parse(inputStream)
        val groupWithFeedList = mutableListOf<GroupWithFeed>().also {
            it.addGroup(defaultGroup)
        }

        for (outline in opml.body.outlines) {
            // Only feeds
            if (outline.subElements.isEmpty()) {
                // It's a empty group
                if (!outline.attributes.containsKey("xmlUrl")) {
                    if (!outline.isDefaultGroup()) {
                        groupWithFeedList.addGroup(
                            Group(
                                id = targetAccountId.spacerDollar(UUID.randomUUID().toString()),
                                name = outline.extractName(),
                                accountId = targetAccountId,
                            )
                        )
                    }
                } else {
                    groupWithFeedList.addFeedToDefault(
                        Feed(
                            id = targetAccountId.spacerDollar(UUID.randomUUID().toString()),
                            name = outline.extractName(),
                            url = outline.extractUrl() ?: continue,
                            groupId = defaultGroup.id,
                            accountId = targetAccountId,
                            isNotification = outline.extractPresetNotification(),
                            isFullContent = outline.extractPresetFullContent(),
                            isBrowser = outline.extractPresetBrowser(),
                            icon = outline.extractCustomIcon(), // Brief You
                        )
                    )
                }
            } else {
                var groupId = defaultGroup.id
                if (!outline.isDefaultGroup()) {
                    groupId = targetAccountId.spacerDollar(UUID.randomUUID().toString())
                    groupWithFeedList.addGroup(
                        Group(
                            id = groupId,
                            name = outline.extractName(),
                            accountId = targetAccountId,
                        )
                    )
                }
                for (subOutline in outline.subElements) {
                    if (subOutline != null && subOutline.attributes != null) {
                        groupWithFeedList.addFeed(
                            Feed(
                                id = targetAccountId.spacerDollar(UUID.randomUUID().toString()),
                                name = subOutline.extractName(),
                                url = subOutline.extractUrl() ?: continue,
                                groupId = groupId,
                                accountId = targetAccountId,
                                isNotification = subOutline.extractPresetNotification(),
                                isFullContent = subOutline.extractPresetFullContent(),
                                isBrowser = subOutline.extractPresetBrowser(),
                                icon = subOutline.extractCustomIcon(), // Brief You
                            )
                        )
                    }
                }
            }
        }
        return groupWithFeedList
    }

    private fun MutableList<GroupWithFeed>.addGroup(group: Group) {
        add(GroupWithFeed(group = group, feeds = mutableListOf()))
    }

    private fun MutableList<GroupWithFeed>.addFeed(feed: Feed) {
        last().feeds.add(feed)
    }

    private fun MutableList<GroupWithFeed>.addFeedToDefault(feed: Feed) {
        first().feeds.add(feed)
    }

    private fun Outline?.extractName(): String {
        if (this == null) return ""
        return attributes.getOrDefault("title", null)
            ?: text
            ?: attributes.getOrDefault("xmlUrl", null).extractDomain()
            ?: attributes.getOrDefault("htmlUrl", null).extractDomain()
            ?: attributes.getOrDefault("url", null).extractDomain()
            ?: ""
    }

    private fun Outline?.extractUrl(): String? {
        if (this == null) return null
        val url = attributes.getOrDefault("xmlUrl", null)
            ?: attributes.getOrDefault("url", null)
        return if (url.isNullOrBlank()) null else url
    }

    private fun Outline?.extractPresetNotification(): Boolean =
        this?.attributes?.getOrDefault("isNotification", null).toBoolean()

    private fun Outline?.extractPresetFullContent(): Boolean =
        this?.attributes?.getOrDefault("isFullContent", null).toBoolean()

    private fun Outline?.extractPresetBrowser(): Boolean =
        this?.attributes?.getOrDefault("isBrowser", null).toBoolean()

    /**
     * Custom icon written by Brief You's "include additional info" export. Only the two known
     * formats are accepted, so an OPML file can't smuggle in an arbitrary icon URL.
     */
    private fun Outline?.extractCustomIcon(): String? {
        val value = this?.attributes?.getOrDefault("customIcon", null) ?: return null
        return when {
            value.startsWith(EMOJI_PREFIX) && value.length <= 40 -> value
            value.length <= FeedIconOverrides.MAX_IMPORT_ICON_CHARS &&
                FeedIconOverrides.IMAGE_VALUE.matches(value) -> value
            else -> null
        }
    }

    private fun Outline?.isDefaultGroup(): Boolean =
        this?.attributes?.getOrDefault("isDefault", null).toBoolean()
}
