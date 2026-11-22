package com.ay.briefyou.ui.ext

infix fun Int.spacerDollar(str: Any): String = "$this$$str"

fun String.dollarLast(): String = split("$").last()

/** The part after the `$` in an account's default group id. */
const val DEFAULT_GROUP_SALT = "default_group"

/** What it was before 0.16.2.176 (the old app name); [com.ay.briefyou.brief.app.GroupIdMigration] moves old data over. */
const val OLD_DEFAULT_GROUP_SALT = "read_you_app_default_group"

fun Int.getDefaultGroupId() = this.spacerDollar(DEFAULT_GROUP_SALT)

fun Int.toBoolean(): Boolean = this != 0
