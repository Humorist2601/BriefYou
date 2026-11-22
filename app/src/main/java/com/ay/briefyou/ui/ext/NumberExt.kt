package com.ay.briefyou.ui.ext

infix fun Int.spacerDollar(str: Any): String = "$this$$str"

fun String.dollarLast(): String = split("$").last()

/** The part after the `$` in an account's default group id. */
const val DEFAULT_GROUP_SALT = "default_group"

fun Int.getDefaultGroupId() = this.spacerDollar(DEFAULT_GROUP_SALT)

fun Int.toBoolean(): Boolean = this != 0
