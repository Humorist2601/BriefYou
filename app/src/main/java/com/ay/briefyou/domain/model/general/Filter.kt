package com.ay.briefyou.domain.model.general

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.AllInbox
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.general.Filter.Companion.All
import com.ay.briefyou.domain.model.general.Filter.Companion.Starred
import com.ay.briefyou.domain.model.general.Filter.Companion.Unread

/**
 * Indicates filter conditions.
 *
 * - [All]: all items
 * - [Unread]: unread items
 * - [Starred]: starred items
 */
class Filter private constructor(
    val index: Int,
    val iconOutline: ImageVector,
    val iconFilled: ImageVector,
) {

    fun isStarred(): Boolean = this == Starred
    fun isUnread(): Boolean = this == Unread
    fun isAll(): Boolean = this == All

    @Stable
    @Composable
    fun toName(): String = when (this) {
        Unread -> stringResource(R.string.unread)
        Starred -> stringResource(R.string.starred)
        else -> stringResource(R.string.all)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Stable
    @Composable
    fun toDesc(important: Int): String = when (this) {
        Starred -> pluralStringResource(R.plurals.starred_desc, important, important)
        Unread -> pluralStringResource(R.plurals.unread_desc, important, important)
        else -> pluralStringResource(R.plurals.all_desc, important, important)
    }

    companion object {

        val Starred = Filter(
            index = 0,
            iconOutline = Icons.Rounded.StarOutline,
            iconFilled = Icons.Rounded.Star,
        )
        val Unread = Filter(
            index = 1,
            // Brief You: Unread is the inbox, All the stacked inboxes
            iconOutline = Icons.Outlined.Inbox,
            iconFilled = Icons.Rounded.Inbox,
        )
        val All = Filter(
            index = 2,
            iconOutline = Icons.Outlined.AllInbox,
            iconFilled = Icons.Rounded.AllInbox,
        )
        val values = listOf(Starred, Unread, All)
    }
}
