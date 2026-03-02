package com.Android.stremini_ai

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.ImageView

class BubbleController(
    private val menuItems: List<ImageView>,
    private val whiteColor: Int
) {
    private val activeFeatures = mutableSetOf<Int>()

    fun toggleFeature(featureId: Int) {
        if (!activeFeatures.add(featureId)) activeFeatures.remove(featureId)
        updateMenuItemsColor(isScannerActive = false)
    }

    fun removeFeature(featureId: Int) {
        activeFeatures.remove(featureId)
        updateMenuItemsColor(isScannerActive = false)
    }

    fun isFeatureActive(featureId: Int): Boolean = activeFeatures.contains(featureId)

    fun updateMenuItemsColor(isScannerActive: Boolean) {
        menuItems.forEach { item ->
            val enabled = activeFeatures.contains(item.id) || (item.id == menuItems[3].id && isScannerActive)
            item.background = if (enabled) {
                LayerDrawable(
                    arrayOf(
                        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) },
                        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#23A6E2")); alpha = 200 }
                    )
                )
            } else {
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) }
            }
            item.setColorFilter(whiteColor)
            item.visibility = item.visibility.takeIf { it == View.VISIBLE } ?: item.visibility
        }
    }
}
