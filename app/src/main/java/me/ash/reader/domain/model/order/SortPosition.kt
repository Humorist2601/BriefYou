package me.ash.reader.domain.model.order

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Where a group, or a feed inside its group, sits in the user's own order ("Your order" in the sort
 * sheet). Kept in its own table rather than on the group and feed rows, because syncing with a
 * server rewrites those rows and would lose the order.
 *
 * Groups and feeds without a position come after the ones that have one, in the order they were
 * added, so new subscriptions land at the end.
 */
@Entity(
    tableName = "sort_position",
    primaryKeys = ["kind", "itemId"],
    indices = [Index(value = ["accountId"])],
)
data class SortPosition(
    /** [KIND_GROUP] or [KIND_FEED]. */
    @ColumnInfo val kind: Int,
    /** The group's or feed's id. */
    @ColumnInfo val itemId: String,
    @ColumnInfo val accountId: Int,
    /** 0 is first. Groups are ordered among all groups, feeds among the feeds of their group. */
    @ColumnInfo val position: Int,
) {
    companion object {
        const val KIND_GROUP = 0
        const val KIND_FEED = 1
    }
}
