package com.example.manymanager

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw

/** Window-scoped crossfades. Clear on destruction; never retain an Activity in a ViewModel. */
class AppearanceTransitionState(private val window: Window) {
    private var snapshot: Bitmap? = null
    private var overlay: ImageView? = null
    private var appearanceAnimator: ValueAnimator? = null
    private var pageAnimator: ValueAnimator? = null
    private var appearanceDraw: OneShotPreDrawListener? = null
    private var pageDraw: OneShotPreDrawListener? = null
    private var pageFrame: BitmapDrawable? = null
    private var pageScroll: ScrollView? = null
    private var duration = 500L
    var isChanging = false
        private set

    private fun capture(view: View, background: Int? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (view.width / 2).coerceAtLeast(1),
            (view.height / 2).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).apply {
            scale(bitmap.width.toFloat() / view.width, bitmap.height.toFloat() / view.height)
            background?.let { drawColor(it) }
            view.draw(this)
        }
        return bitmap
    }

    fun prepare(root: View, durationMillis: Long = 500L) {
        clear()
        isChanging = true
        duration = durationMillis
        if (!ValueAnimator.areAnimatorsEnabled() || root.width == 0 || root.height == 0) return
        snapshot = capture(root)
        window.setWindowAnimations(0)
    }

    fun finish(parent: ViewGroup, restoreUi: () -> Unit) {
        val bitmap = snapshot
        snapshot = null
        // A configuration change can arrive while the previous fade is still running.
        clearAppearance()
        isChanging = true
        val image = bitmap?.let {
            ImageView(parent.context).apply {
                setImageBitmap(it)
                scaleType = ImageView.ScaleType.FIT_XY
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = true
                parent.addView(this, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }
        overlay = image
        appearanceDraw?.removeListener()
        appearanceDraw = parent.doOnPreDraw {
            appearanceDraw = null
            restoreUi()
            if (image == null || !ValueAnimator.areAnimatorsEnabled()) {
                clearAppearance()
            } else {
                appearanceAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = this@AppearanceTransitionState.duration
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { image.alpha = it.animatedValue as Float }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            clearAppearance()
                        }
                    })
                    start()
                }
            }
        }
    }

    fun changePage(
        scroll: ScrollView,
        animate: Boolean,
        durationMillis: Long,
        background: Int,
        updatePage: () -> Unit,
        restoreScroll: () -> Unit
    ) {
        clearPage()
        val frame = if (animate && ValueAnimator.areAnimatorsEnabled() &&
            scroll.width > 0 && scroll.height > 0
        ) BitmapDrawable(scroll.resources, capture(scroll, background)).apply {
            setBounds(0, 0, scroll.width, scroll.height)
            scroll.overlay.add(this)
        } else null
        pageFrame = frame
        pageScroll = scroll
        updatePage()
        pageDraw = scroll.doOnPreDraw {
            pageDraw = null
            restoreScroll()
            if (frame == null || !ValueAnimator.areAnimatorsEnabled()) {
                clearPage()
            } else {
                pageAnimator = ValueAnimator.ofInt(255, 0).apply {
                    duration = durationMillis
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { frame.alpha = it.animatedValue as Int }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            clearPage()
                        }
                    })
                    start()
                }
            }
        }
    }

    private fun clearAppearance() {
        appearanceDraw?.removeListener()
        appearanceDraw = null
        appearanceAnimator?.removeAllListeners()
        appearanceAnimator?.removeAllUpdateListeners()
        appearanceAnimator?.cancel()
        appearanceAnimator = null
        overlay?.let { image ->
            (image.parent as? ViewGroup)?.removeView(image)
            image.setImageDrawable(null)
        }
        overlay = null
        snapshot = null
        isChanging = false
    }

    private fun clearPage() {
        pageDraw?.removeListener()
        pageDraw = null
        pageAnimator?.removeAllListeners()
        pageAnimator?.removeAllUpdateListeners()
        pageAnimator?.cancel()
        pageAnimator = null
        pageFrame?.let { pageScroll?.overlay?.remove(it) }
        pageFrame = null
        pageScroll = null
    }

    fun clear() {
        clearPage()
        clearAppearance()
    }
}
