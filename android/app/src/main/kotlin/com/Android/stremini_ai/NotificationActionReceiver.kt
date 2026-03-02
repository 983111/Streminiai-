package com.Android.stremini_ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Static BroadcastReceiver for notification action buttons.
 * Delegates action forwarding to [OverlayServiceGateway].
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        OverlayServiceGateway(context).forwardOverlayAction(intent?.action)
    }
}
