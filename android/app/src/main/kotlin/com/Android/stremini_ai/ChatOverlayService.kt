package com.Android.stremini_ai

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ChatOverlayService : Service(), View.OnTouchListener {

    companion object {
        const val ACTION_SEND_MESSAGE = "com.Android.stremini_ai.SEND_MESSAGE"
        const val EXTRA_MESSAGE = "message"
        const val ACTION_SCANNER_START = "com.Android.stremini_ai.SCANNER_START"
        const val ACTION_SCANNER_STOP = "com.Android.stremini_ai.SCANNER_STOP"
        const val ACTION_TOGGLE_BUBBLE = "com.Android.stremini_ai.TOGGLE_BUBBLE"
        const val ACTION_STOP_SERVICE = "com.Android.stremini_ai.STOP_SERVICE"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "chat_head_service"
        val NEON_BLUE: Int = android.graphics.Color.parseColor("#00D9FF")
        val WHITE: Int = android.graphics.Color.parseColor("#FFFFFF")
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams


    private lateinit var bubbleIcon: ImageView
    private lateinit var menuItems: List<ImageView>
    private var isMenuExpanded = false

    private lateinit var bubbleController: BubbleController
    private var isScannerActive = false
    private var isBubbleVisible = true
    private lateinit var inputMethodManager: InputMethodManager


    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var hasMoved = false

    private val bubbleSizeDp = 60f
    private val menuItemSizeDp = 50f
    private val radiusDp = 80f

    private var bubbleScreenX = 0
    private var bubbleScreenY = 0

    private var isMenuAnimating = false
    private var windowAnimator: ValueAnimator? = null
    private var isWindowResizing = false
    private var preventPositionUpdates = false

    // Idle shrink/fade state
    private var idleRunnable: Runnable? = null
    private val idleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isBubbleIdle = false
    private var idleAnimator: ValueAnimator? = null
    private var preIdleX = 0
    private lateinit var idleAnimationController: IdleAnimationController
    private val IDLE_TIMEOUT_MS = 3000L
    private val IDLE_SCALE = 0.6f
    private val IDLE_ALPHA = 0.4f
    private val IDLE_ANIM_DURATION = 400L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SEND_MESSAGE -> {
                    val message = intent.getStringExtra(EXTRA_MESSAGE)
                    if (message != null) addMessageToChatbot(message, isUser = false)
                }
                ACTION_SCANNER_START -> {
                    isScannerActive = true; updateMenuItemsColor()
                    Toast.makeText(context, "Screen Detection Started", Toast.LENGTH_SHORT).show()
                }
                ACTION_SCANNER_STOP -> {
                    isScannerActive = false; updateMenuItemsColor()
                    Toast.makeText(context, "Screen Detection Stopped", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        aiBackendClient = AIBackendClient(client)
        deviceCommandRouter = DeviceCommandRouter()
        startForegroundService()
        setupOverlay()
        floatingChatController = FloatingChatController(this, windowManager, serviceScope, aiBackendClient, deviceCommandRouter)
        voiceController = VoiceController(this, windowManager, serviceScope, deviceCommandRouter, aiBackendClient)

        val filter = IntentFilter().apply {
            addAction(ACTION_SEND_MESSAGE)
            addAction(ACTION_SCANNER_START)
            addAction(ACTION_SCANNER_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_BUBBLE -> {
                if (isBubbleVisible) hideBubble() else showBubble()
            }
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun hideBubble() {
        if (!isBubbleVisible) return
        // Collapse menu first if expanded
        if (isMenuExpanded) collapseMenu()
        // Cancel idle timer
        idleRunnable?.let { idleHandler.removeCallbacks(it) }
        if (::idleAnimationController.isInitialized) idleAnimationController.clear()
        overlayView.visibility = View.GONE
        isBubbleVisible = false
        updateNotification()
    }

    private fun showBubble() {
        if (isBubbleVisible) return
        overlayView.visibility = View.VISIBLE
        // Restore to full state
        bubbleIcon.scaleX = 1f
        bubbleIcon.scaleY = 1f
        bubbleIcon.alpha = 1f
        isBubbleIdle = false
        isBubbleVisible = true
        updateNotification()
        resetIdleTimer()
    }

    private fun setupOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.chat_bubble_layout, null)
        bubbleIcon = overlayView.findViewById(R.id.bubble_icon)
        menuItems = listOf(
            overlayView.findViewById(R.id.btn_auto_tasker),
            overlayView.findViewById(R.id.btn_settings),
            overlayView.findViewById(R.id.btn_ai),
            overlayView.findViewById(R.id.btn_scanner),
            overlayView.findViewById(R.id.btn_keyboard)
        )
        bubbleController = BubbleController(menuItems, WHITE)

        val typeParam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val radiusPx = dpToPx(radiusDp).toFloat()
        val bubbleSizePx = dpToPx(bubbleSizeDp).toFloat()
        val expandedWindowSizePx = ((radiusPx * 2) + bubbleSizePx + dpToPx(20f)).toInt()
        val collapsedWindowSizePx = (bubbleSizePx + dpToPx(10f)).toInt()

        params = WindowManager.LayoutParams(
            collapsedWindowSizePx, collapsedWindowSizePx, typeParam,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val screenHeight = resources.displayMetrics.heightPixels
        bubbleScreenX = 60
        bubbleScreenY = (screenHeight * 0.25).toInt()

        val windowHalfSize = collapsedWindowSizePx / 2
        params.x = bubbleScreenX - windowHalfSize
        params.y = bubbleScreenY - windowHalfSize

        bubbleIcon.setOnTouchListener(this)

        menuItems[0].setOnClickListener { collapseMenu(); handleAutoTasker() }
        menuItems[1].setOnClickListener { collapseMenu(); handleSettings() }
        menuItems[2].setOnClickListener { collapseMenu(); handleAIChat() }
        menuItems[3].setOnClickListener { collapseMenu(); handleScanner() }
        menuItems[4].setOnClickListener { collapseMenu(); handleKeyboard() }

        bubbleIcon.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        bubbleIcon.isClickable = true; bubbleIcon.isFocusable = true

        menuItems.forEach {
            it.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            it.isClickable = true; it.isFocusable = true
            it.visibility = View.INVISIBLE
        }

        updateMenuItemsColor()
        overlayView.background = null
        overlayView.isClickable = false; overlayView.isFocusable = false
        overlayView.setOnTouchListener { _, _ -> false }
        windowManager.addView(overlayView, params)

        (overlayView as? android.view.ViewGroup)?.apply {
            clipToPadding = false; clipChildren = false
            isMotionEventSplittingEnabled = false
        }
        overlayView.layoutParams = overlayView.layoutParams?.apply {
            width = params.width; height = params.height
        }
        overlayView.requestLayout()

        idleAnimationController = IdleAnimationController(
            overlayView = overlayView,
            bubbleIcon = bubbleIcon,
            params = params,
            windowManager = windowManager,
            bubbleSizePxProvider = { dpToPx(bubbleSizeDp).toFloat() },
            bubbleX = { bubbleScreenX },
            bubbleY = { bubbleScreenY },
            setBubbleX = { bubbleScreenX = it },
            isInteractionBlocked = { isMenuExpanded || isDragging || isMenuAnimating }
        )

        // Start idle timer after setup
        resetIdleTimer()
    }

    // ==========================================
    // BUBBLE IDLE SHRINK / FADE
    // ==========================================

    private fun resetIdleTimer() {
        if (::idleAnimationController.isInitialized) {
            idleAnimationController.resetIdleTimer(IDLE_TIMEOUT_MS)
        }
    }

    private fun shrinkBubble() = Unit

    private fun restoreBubble() = Unit


    // ==========================================
    // CHATBOT + VOICE (Controller-driven)
    // ==========================================

    private lateinit var aiBackendClient: AIBackendClient
    private lateinit var deviceCommandRouter: DeviceCommandRouter
    private lateinit var floatingChatController: FloatingChatController
    private lateinit var voiceController: VoiceController

    private fun handleAIChat() {
        bubbleController.toggleFeature(menuItems[2].id)
        if (bubbleController.isFeatureActive(menuItems[2].id)) {
            floatingChatController.show {
                bubbleController.removeFeature(menuItems[2].id)
                bubbleController.updateMenuItemsColor(isScannerActive)
            }
        } else {
            floatingChatController.hide()
        }
        bubbleController.updateMenuItemsColor(isScannerActive)
    }

    private fun handleAutoTasker() {
        bubbleController.toggleFeature(menuItems[0].id)
        if (bubbleController.isFeatureActive(menuItems[0].id)) {
            val opened = voiceController.show {
                bubbleController.removeFeature(menuItems[0].id)
                bubbleController.updateMenuItemsColor(isScannerActive)
            }
            if (!opened) {
                bubbleController.removeFeature(menuItems[0].id)
            }
        } else {
            voiceController.hide()
        }
        bubbleController.updateMenuItemsColor(isScannerActive)
    }

    private fun addMessageToChatbot(message: String, isUser: Boolean) {
        if (::floatingChatController.isInitialized) {
            floatingChatController.addMessage(message, isUser)
        }
    }

    // ==========================================
    // SCANNER
    // ==========================================

    private fun handleScanner() {
        isScannerActive = !isScannerActive
        updateMenuItemsColor()
        if (isScannerActive) {
            startService(Intent(this, ScreenReaderService::class.java).apply { action = ScreenReaderService.ACTION_START_SCAN })
            Toast.makeText(this, "Screen Detection Enabled", Toast.LENGTH_SHORT).show()
        } else {
            startService(Intent(this, ScreenReaderService::class.java).apply { action = ScreenReaderService.ACTION_STOP_SCAN })
            Toast.makeText(this, "Screen Detection Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleKeyboard() {
        toggleFeature(menuItems[4].id)
        if (isFeatureActive(menuItems[4].id)) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                Toast.makeText(this, "Open Settings to enable AI Keyboard", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                try { inputMethodManager.showInputMethodPicker()
                    Toast.makeText(this, "Select AI Keyboard from the list", Toast.LENGTH_SHORT).show()
                } catch (ex: Exception) {
                    Toast.makeText(this, "AI Keyboard feature enabled", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "AI Keyboard Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSettings() {
        openMainApp()
        Toast.makeText(this, "Opening Stremini...", Toast.LENGTH_SHORT).show()
    }


    private fun toggleFeature(featureId: Int) {
        bubbleController.toggleFeature(featureId)
        bubbleController.updateMenuItemsColor(isScannerActive)
    }

    private fun isFeatureActive(featureId: Int): Boolean = bubbleController.isFeatureActive(featureId)

    private fun updateMenuItemsColor() {
        bubbleController.updateMenuItemsColor(isScannerActive)
    }

    // ==========================================
    // TOUCH / DRAG / ANIMATION
    // ==========================================

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resetIdleTimer()
                initialTouchX = event.rawX; initialTouchY = event.rawY
                initialX = bubbleScreenX; initialY = bubbleScreenY
                isDragging = false; hasMoved = false; return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isWindowResizing || preventPositionUpdates) return true
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (abs(dx) > 10 || abs(dy) > 10) {
                    hasMoved = true
                    if (!isMenuExpanded) {
                        isDragging = true
                        bubbleScreenX = initialX + dx; bubbleScreenY = initialY + dy
                        val bubbleSizePx = dpToPx(bubbleSizeDp).toFloat()
                        val collapsedWindowSizePx = bubbleSizePx + dpToPx(10f)
                        val windowHalfSize = collapsedWindowSizePx / 2
                        params.x = (bubbleScreenX - windowHalfSize).toInt()
                        params.y = (bubbleScreenY - windowHalfSize).toInt()
                        try { windowManager.updateViewLayout(overlayView, params) } catch (e: Exception) {}
                    } else {
                        if (!isMenuAnimating) collapseMenu()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                resetIdleTimer()
                if (isWindowResizing || preventPositionUpdates) { isDragging = false; hasMoved = false; return true }
                if (!hasMoved && !isDragging) {
                    if (!isMenuAnimating) toggleMenu()
                } else if (isDragging) {
                    if (isWindowResizing || preventPositionUpdates) overlayView.postDelayed({ snapToEdge() }, 200)
                    else snapToEdge()
                }
                isDragging = false; hasMoved = false; return true
            }
        }
        return false
    }

    private fun toggleMenu() { if (isMenuAnimating) return; if (isMenuExpanded) collapseMenu() else expandMenu() }

    private fun expandMenu() {
        if (isMenuAnimating || isMenuExpanded) return
        isMenuExpanded = true; isMenuAnimating = true

        val radiusPx = dpToPx(radiusDp).toFloat()
        val bubbleSizePx = dpToPx(bubbleSizeDp).toFloat()
        val menuItemSizePx = dpToPx(menuItemSizeDp).toFloat()
        val expandedWindowSizePx = (radiusPx * 2) + bubbleSizePx + dpToPx(20f)
        val collapsedWindowSizePx = bubbleSizePx + dpToPx(10f)

        animateWindowSize(collapsedWindowSizePx, expandedWindowSizePx, 220L) { isMenuAnimating = false }

        val centerX = expandedWindowSizePx / 2f; val centerY = expandedWindowSizePx / 2f
        val screenWidth = resources.displayMetrics.widthPixels
        val isOnRightSide = bubbleScreenX > (screenWidth / 2)
        val fixedAngles = if (isOnRightSide) listOf(90.0, 135.0, 180.0, 225.0, 270.0)
                          else listOf(90.0, 45.0, 0.0, -45.0, -90.0)

        overlayView.postDelayed({
            for ((index, view) in menuItems.withIndex()) {
                view.visibility = View.VISIBLE; view.alpha = 0f
                view.translationX = 0f; view.translationY = 0f
                val angle = fixedAngles[index]; val rad = Math.toRadians(angle)
                val targetX = centerX + (radiusPx * cos(rad)).toFloat() - (menuItemSizePx / 2)
                val targetY = centerY + (radiusPx * -sin(rad)).toFloat() - (menuItemSizePx / 2)
                val initialCenteredX = centerX - (menuItemSizePx / 2); val initialCenteredY = centerY - (menuItemSizePx / 2)
                view.animate().translationX(targetX - initialCenteredX).translationY(targetY - initialCenteredY)
                    .alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
            }
            updateMenuItemsColor()
        }, 160)
    }

    private fun collapseMenu() {
        if (isMenuAnimating || !isMenuExpanded) return
        isMenuExpanded = false; isMenuAnimating = true
        val radiusPx = dpToPx(radiusDp).toFloat()
        val bubbleSizePx = dpToPx(bubbleSizeDp).toFloat()
        val expandedWindowSizePx = (radiusPx * 2) + bubbleSizePx + dpToPx(20f)
        val collapsedWindowSizePx = bubbleSizePx + dpToPx(10f)

        for (view in menuItems) {
            view.animate().translationX(0f).translationY(0f).alpha(0f)
                .setDuration(150).setInterpolator(AccelerateInterpolator())
                .withEndAction { view.visibility = View.INVISIBLE }.start()
        }
        overlayView.postDelayed({
            animateWindowSize(expandedWindowSizePx, collapsedWindowSizePx, 200L) {
                isMenuAnimating = false
                resetIdleTimer() // Start idle timer after menu collapses
            }
        }, 120)
    }

    private fun animateWindowSize(fromSize: Float, toSize: Float, duration: Long = 200L, onEnd: (() -> Unit)? = null) {
        windowAnimator?.cancel()
        isWindowResizing = true; preventPositionUpdates = true
        val fromHalf = fromSize / 2f; val toHalf = toSize / 2f
        val startX = bubbleScreenX - fromHalf; val endX = bubbleScreenX - toHalf
        val startY = bubbleScreenY - fromHalf; val endY = bubbleScreenY - toHalf

        windowAnimator = ValueAnimator.ofFloat(fromSize, toSize).apply {
            this.duration = duration; interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val newSize = animator.animatedValue as Float
                val frac = if (toSize != fromSize) (newSize - fromSize) / (toSize - fromSize) else 1f
                params.width = newSize.toInt(); params.height = newSize.toInt()
                params.x = (startX + (endX - startX) * frac).toInt()
                params.y = (startY + (endY - startY) * frac).toInt()
                try { windowManager.updateViewLayout(overlayView, params) } catch (e: Exception) {}
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    windowAnimator = null; isWindowResizing = false; preventPositionUpdates = false
                    params.width = toSize.toInt(); params.height = toSize.toInt()
                    params.x = (bubbleScreenX - toHalf).toInt(); params.y = (bubbleScreenY - toHalf).toInt()
                    try { windowManager.updateViewLayout(overlayView, params) } catch (e: Exception) {}
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun snapToEdge() {
        if (isWindowResizing || preventPositionUpdates || isMenuAnimating) {
            overlayView.postDelayed({ snapToEdge() }, 150); return
        }
        val bubbleSizePx = dpToPx(bubbleSizeDp).toFloat()
        val screenWidth = resources.displayMetrics.widthPixels
        val collapsedWindowSizePx = bubbleSizePx + dpToPx(10f)
        val windowHalfSize = collapsedWindowSizePx / 2

        val targetBubbleScreenX = if (bubbleScreenX > (screenWidth / 2))
            screenWidth - (bubbleSizePx / 2).toInt()
        else (bubbleSizePx / 2).toInt()

        ValueAnimator.ofInt(bubbleScreenX, targetBubbleScreenX).apply {
            duration = 200; interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                bubbleScreenX = animator.animatedValue as Int
                params.x = (bubbleScreenX - windowHalfSize).toInt()
                try { windowManager.updateViewLayout(overlayView, params) } catch (e: Exception) {}
            }
            start()
        }
    }

    private fun openMainApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Stremini Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        // Toggle Bubble action — uses BroadcastReceiver so it works from background
        val toggleIntent = Intent(ACTION_TOGGLE_BUBBLE).apply {
            setClass(this@ChatOverlayService, NotificationActionReceiver::class.java)
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Service action
        val stopIntent = Intent(ACTION_STOP_SERVICE).apply {
            setClass(this@ChatOverlayService, NotificationActionReceiver::class.java)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open app when tapping the notification body
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLabel = if (isBubbleVisible) "🗕 Hide Bubble" else "💬 Show Bubble"
        val statusText = if (isBubbleVisible) "🟢 Running — Bubble visible"
                         else "🔴 Running — Bubble hidden"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stremini AI Assistant")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, toggleLabel, togglePendingIntent)
            .addAction(0, "❌ Stop Service", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        idleRunnable?.let { idleHandler.removeCallbacks(it) }
        if (::idleAnimationController.isInitialized) idleAnimationController.clear()
        serviceScope.cancel()
        unregisterReceiver(controlReceiver)
        floatingChatController.hide(); voiceController.hide()
        if (::overlayView.isInitialized && overlayView.windowToken != null) windowManager.removeView(overlayView)
    }
}