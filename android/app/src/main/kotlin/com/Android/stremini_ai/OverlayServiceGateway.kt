package com.Android.stremini_ai

import android.content.Context
import android.content.Intent
import android.os.Build

class OverlayServiceGateway(private val context: Context) {
    fun startOverlayService() {
        val intent = Intent(context, ChatOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }

    fun stopOverlayService() {
        context.stopService(Intent(context, ChatOverlayService::class.java))
    }

    fun forwardOverlayAction(action: String?) {
        val serviceIntent = Intent(context, ChatOverlayService::class.java).apply { this.action = action }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)
    }

    fun startScreenScan() {
        context.startService(Intent(context, ScreenReaderService::class.java).apply {
            action = ScreenReaderService.ACTION_START_SCAN
        })
    }

    fun stopScreenScan() {
        context.startService(Intent(context, ScreenReaderService::class.java).apply {
            action = ScreenReaderService.ACTION_STOP_SCAN
        })
    }
}
