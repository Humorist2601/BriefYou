package com.ay.briefyou.ui.ext

import android.annotation.SuppressLint
import android.content.Context
import com.ay.briefyou.R
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@SuppressLint("SimpleDateFormat")
object DateFormat {
    val YYYY_MM_DD_HH_MM_SS = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val YYYY_MM_DD_DASH_HH_MM_SS = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss")
    val YYYY_MM_DD_DASH_HH_MM_SS_DASH = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
}

fun Date.toString(format: SimpleDateFormat): String {
    return format.format(this)
}

fun Date.formatAsString(
    context: Context,
    onlyHourMinute: Boolean? = false,
    atHourMinute: Boolean? = false,
): String {
    val locale = Locale.getDefault()
    val df = DateFormat.getDateInstance(DateFormat.FULL, locale)
    return when {
        onlyHourMinute == true -> {
            this.toTimeString(context = context)
        }

        atHourMinute == true -> {
            context.getString(
                R.string.date_at_time,
                df.format(this),
                this.toTimeString(context = context),
            )
        }

        else -> {
            df.format(this).run {
                when (this) {
                    df.format(Date()) -> context.getString(R.string.today)
                    df.format(
                        Calendar.getInstance().apply {
                            time = Date()
                            add(Calendar.DAY_OF_MONTH, -1)
                        }.time
                    ),
                    -> context.getString(R.string.yesterday)

                    else -> this
                }
            }
        }
    }
}

private fun Date.toTimeString(context: Context): String =
    android.text.format.DateFormat.getTimeFormat(context).format(this)


fun Date.isFuture(staticDate: Date = Date()): Boolean = this.time > staticDate.time
