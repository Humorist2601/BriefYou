package me.ash.reader.brief.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Brief You Nightly only (registered in the nightly build's manifest): posts a made-up digest so
 * its look can be checked on a phone or emulator without waiting for a sync:
 * `adb shell am broadcast -f 32 -n <app id>/me.ash.reader.brief.notifications.DigestPreviewReceiver`.
 */
@AndroidEntryPoint
class DigestPreviewReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        notificationHelper.postSample()
    }
}
