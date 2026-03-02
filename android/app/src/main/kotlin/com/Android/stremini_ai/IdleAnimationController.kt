package com.Android.stremini_ai

import android.animation.ValueAnimator
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

class IdleAnimationController(
    private val overlayView: View,
    private val bubbleIcon: View,
    private val params: WindowManager.LayoutParams,
    private val windowManager: WindowManager,
    private val bubbleSizePxProvider: () -> Float,
    private val bubbleX: () -> Int,
    private val bubbleY: () -> Int,
    private val setBubbleX: (Int) -> Unit,
    private val isInteractionBlocked: () -> Boolean
) {
    private val idleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var idleRunnable: Runnable? = null
    private var isBubbleIdle = false
    private var idleAnimator: ValueAnimator? = null
    private var preIdleX = 0

    fun resetIdleTimer(timeoutMs: Long = 3000L) {
        idleRunnable?.let { idleHandler.removeCallbacks(it) }
        if (isBubbleIdle) restoreBubble()
        idleRunnable = Runnable { if (!isInteractionBlocked()) shrinkBubble() }
        idleHandler.postDelayed(idleRunnable!!, timeoutMs)
    }

    fun clear() {
        idleRunnable?.let { idleHandler.removeCallbacks(it) }
        idleAnimator?.cancel()
    }

    private fun shrinkBubble() {
        if (isBubbleIdle) return
        isBubbleIdle = true
        bubbleIcon.animate().scaleX(0.6f).scaleY(0.6f).alpha(0.4f).setDuration(400L).setInterpolator(DecelerateInterpolator()).start()
        preIdleX = bubbleX()
        val screenWidth = overlayView.resources.displayMetrics.widthPixels
        val bubbleSizePx = bubbleSizePxProvider()
        val targetX = if (bubbleX() > screenWidth / 2) {
            screenWidth - (bubbleSizePx / 2).toInt() + (bubbleSizePx * 0.4f).toInt()
        } else {
            (bubbleSizePx / 2).toInt() - (bubbleSizePx * 0.4f).toInt()
        }
        idleAnimator?.cancel()
        idleAnimator = ValueAnimator.ofInt(bubbleX(), targetX).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                if (isInteractionBlocked()) { cancel(); return@addUpdateListener }
                setBubbleX(it.animatedValue as Int)
                params.x = (bubbleX() - ((bubbleSizePxProvider() + 10f) / 2)).toInt()
                runCatching { windowManager.updateViewLayout(overlayView, params) }
            }
            start()
        }
    }

    private fun restoreBubble() {
        if (!isBubbleIdle) return
        isBubbleIdle = false
        bubbleIcon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200L).setInterpolator(DecelerateInterpolator()).start()
        val bubbleSizePx = bubbleSizePxProvider()
        val screenWidth = overlayView.resources.displayMetrics.widthPixels
        val targetX = if (preIdleX > screenWidth / 2) screenWidth - (bubbleSizePx / 2).toInt() else (bubbleSizePx / 2).toInt()
        idleAnimator?.cancel()
        idleAnimator = ValueAnimator.ofInt(bubbleX(), targetX).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                setBubbleX(it.animatedValue as Int)
                params.x = (bubbleX() - ((bubbleSizePxProvider() + 10f) / 2)).toInt()
                runCatching { windowManager.updateViewLayout(overlayView, params) }
            }
            start()
        }
    }
}
