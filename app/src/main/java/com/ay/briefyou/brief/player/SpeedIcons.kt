package com.ay.briefyou.brief.player

import com.ay.briefyou.R

/**
 * The media notifications' speed button shows the speed as a number for every speed we offer
 * (Android's own icons only have some of them; the rest showed a plain speedometer). Made from
 * Poppins Bold, all at one size. Used by the podcast player and read aloud. Brief You.
 */
object SpeedIcons {
    fun of(speed: Float): Int =
        when (Math.round(speed * 10)) {
            8 -> R.drawable.ic_brief_speed_0_8
            10 -> R.drawable.ic_brief_speed_1
            12 -> R.drawable.ic_brief_speed_1_2
            14 -> R.drawable.ic_brief_speed_1_4
            16 -> R.drawable.ic_brief_speed_1_6
            18 -> R.drawable.ic_brief_speed_1_8
            20 -> R.drawable.ic_brief_speed_2
            25 -> R.drawable.ic_brief_speed_2_5
            30 -> R.drawable.ic_brief_speed_3
            else -> R.drawable.ic_brief_speed_1
        }
}
