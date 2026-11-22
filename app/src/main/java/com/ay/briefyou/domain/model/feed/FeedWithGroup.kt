package com.ay.briefyou.domain.model.feed

import androidx.room.Embedded
import androidx.room.Relation
import com.ay.briefyou.domain.model.group.Group

/**
 * A [feed] contains a [group].
 */
data class FeedWithGroup(
    @Embedded
    var feed: Feed,
    @Relation(parentColumn = "groupId", entityColumn = "id")
    var group: Group,
)
