package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.order.SortPosition

/** The user's own order of groups and feeds (see [SortPosition]). */
@Dao
interface SortPositionDao {

    @Query("SELECT * FROM sort_position WHERE accountId = :accountId")
    fun queryAllAsFlow(accountId: Int): Flow<List<SortPosition>>

    @Query("SELECT * FROM sort_position WHERE accountId = :accountId")
    suspend fun queryAll(accountId: Int): List<SortPosition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(positions: List<SortPosition>)

    @Query("DELETE FROM sort_position WHERE accountId = :accountId AND kind = :kind")
    suspend fun deleteKind(accountId: Int, kind: Int)

    @Query("DELETE FROM sort_position WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: Int)

    /** Replaces the whole order of one kind (e.g. all groups) with [itemIds], in one go. */
    @Transaction
    suspend fun replaceOrder(accountId: Int, kind: Int, itemIds: List<String>) {
        deleteKind(accountId, kind)
        setOrder(accountId, kind, itemIds)
    }

    /** Puts [itemIds] (all of one kind, e.g. the feeds of one group) in this order: 0, 1, 2, … */
    @Transaction
    suspend fun setOrder(accountId: Int, kind: Int, itemIds: List<String>) {
        insertAll(
            itemIds.mapIndexed { index, id ->
                SortPosition(kind = kind, itemId = id, accountId = accountId, position = index)
            }
        )
    }
}
