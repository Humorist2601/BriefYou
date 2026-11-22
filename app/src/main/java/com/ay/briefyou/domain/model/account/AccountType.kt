package com.ay.briefyou.domain.model.account

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.res.painterResource
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import com.ay.briefyou.R

/**
 * Each account will specify its local or third-party API type.
 */
class AccountType(val id: Int) {

    /**
     * Make sure the constructed object is valid.
     */
    init {
        if (id < 1 || id > 4) {
            throw IllegalArgumentException("Account type id is not valid.")
        }
    }

    fun toDesc(context: Context): String =
        when (this.id) {
            1 -> context.getString(R.string.local)
            2 -> context.getString(R.string.fever)
            3 -> context.getString(R.string.google_reader)
            4 -> context.getString(R.string.fresh_rss)
            else -> context.getString(R.string.unknown)
        }

    @Stable
    @Composable
    fun toIcon(): Any =
        when (this.id) {
            1 -> Icons.Rounded.RssFeed
            2 -> painterResource(id = R.drawable.ic_fever)
            3 -> Icons.Rounded.RssFeed
            4 -> painterResource(id = R.drawable.ic_freshrss)
            else -> Icons.Rounded.RssFeed
        }

    /**
     * Type of account currently supported.
     */
    companion object {

        val Local = AccountType(1)
        val Fever = AccountType(2)
        val GoogleReader = AccountType(3)
        val FreshRSS = AccountType(4)
    }
}

/**
 * Provide [TypeConverter] of [AccountType] for [RoomDatabase].
 */
class AccountTypeConverters {

    @TypeConverter
    fun toAccountType(id: Int): AccountType {
        return AccountType(id)
    }

    @TypeConverter
    fun fromAccountType(accountType: AccountType): Int {
        return accountType.id
    }
}
