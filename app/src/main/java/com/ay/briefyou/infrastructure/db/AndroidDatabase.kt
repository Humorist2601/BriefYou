package com.ay.briefyou.infrastructure.db

import android.content.Context
import androidx.room.*
import com.ay.briefyou.domain.model.account.*
import com.ay.briefyou.domain.model.article.ArchivedArticle
import com.ay.briefyou.domain.model.article.Article
import com.ay.briefyou.domain.model.feed.Feed
import com.ay.briefyou.domain.model.group.Group
import com.ay.briefyou.domain.repository.AccountDao
import com.ay.briefyou.domain.repository.ArticleDao
import com.ay.briefyou.domain.repository.FeedDao
import com.ay.briefyou.domain.repository.GroupDao
import com.ay.briefyou.infrastructure.preference.*
import java.util.*

@Database(
    entities = [Account::class, Feed::class, Article::class, Group::class, ArchivedArticle::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    AndroidDatabase.DateConverters::class,
    AccountTypeConverters::class,
    SyncIntervalConverters::class,
    SyncOnStartConverters::class,
    SyncOnlyOnWiFiConverters::class,
    SyncOnlyWhenChargingConverters::class,
    KeepArchivedConverters::class,
    SyncBlockListConverters::class,
)
abstract class AndroidDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun groupDao(): GroupDao
    abstract fun briefArticleDao(): com.ay.briefyou.brief.data.BriefArticleDao // Brief You: queries only

    companion object {

        private var instance: AndroidDatabase? = null

        const val DATABASE_NAME = "BriefYou"

        fun getInstance(context: Context): AndroidDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AndroidDatabase::class.java,
                    DATABASE_NAME
                ).build().also {
                    instance = it
                }
            }
        }
    }

    class DateConverters {

        @TypeConverter
        fun toDate(dateLong: Long?): Date? {
            return dateLong?.let { Date(it) }
        }

        @TypeConverter
        fun fromDate(date: Date?): Long? {
            return date?.time
        }
    }
}
