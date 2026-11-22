package com.ay.briefyou.domain.model.article

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.ay.briefyou.domain.model.feed.Feed

@Entity(
    tableName = "archived_article",
    foreignKeys = [ForeignKey(
        entity = Feed::class,
        parentColumns = ["id"],
        childColumns = ["feedId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )],
    indices = [androidx.room.Index(value = ["feedId"])], // Brief You: a feed's notes are found without reading them all
)
data class ArchivedArticle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val feedId: String,
    val link: String
)