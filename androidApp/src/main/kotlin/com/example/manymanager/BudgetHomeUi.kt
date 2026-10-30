package com.example.manymanager

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.doOnPreDraw
import kotlin.math.ceil

/** Two balanced areas when space allows; stacked at narrow widths, large fonts or amounts. */
class BudgetOverviewLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val gap = (16 * resources.displayMetrics.density).toInt()
        val amountWidth = listOf(R.id.homeTotal, R.id.homeBudgetRemaining).maxOf { id ->
            findViewById<TextView>(id)?.let { ceil(it.paint.measureText(it.text.toString())).toInt() } ?: 0
        }
        val stack = available < 280 * resources.displayMetrics.density ||
            resources.configuration.fontScale > 1.25f || amountWidth * 2 + gap > available
        orientation = if (stack) VERTICAL else HORIZONTAL
        for (index in 0 until childCount) {
            ((getChildAt(index) as? ViewGroup)?.getChildAt(0) as? TextView)?.minLines = if (stack) 1 else 2
            (getChildAt(index).layoutParams as LayoutParams).apply {
                width = if (stack) LayoutParams.MATCH_PARENT else 0
                weight = if (stack) 0f else 1f
                marginEnd = if (!stack && index == 0) gap else 0
                bottomMargin = if (stack && index == 0) gap else 0
                resolveLayoutDirection(layoutDirection)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Inline, bounded budget breakdown anchored below Home's budget area. */
class BudgetHomeUi(
    private val root: View,
    private val state: BudgetViewModel,
    private val onExpandedChanged: () -> Unit
) {
    private val context = root.context
    private val area = root.findViewById<View>(R.id.homeBudgetArea)
    private val panel = root.findViewById<ScrollView>(R.id.budgetBreakdown)
    private val rows = root.findViewById<LinearLayout>(R.id.budgetBreakdownRows)
    private var animator: ValueAnimator? = null
    private var pendingDraw: OneShotPreDrawListener? = null

    init {
        area.setOnClickListener { setExpanded(!state.breakdownExpanded) }
        refresh()
    }

    private fun money(amount: Long): String = context.getString(
        R.string.ils_amount, BudgetAmounts.number(amount, context.resources.configuration.locales[0])
    )

    fun refresh() {
        val total = state.allocations.total(state.homeMonth)
        // Spending is deliberately not consulted: this is the allocation preview.
        val formatted = money(total)
        root.findViewById<TextView>(R.id.homeBudgetRemaining).text = formatted
        root.findViewById<TextView>(R.id.homeBudgetTotal).text =
            context.getString(R.string.budget_of_total, formatted)
        area.contentDescription = context.getString(R.string.budget_toggle_description, formatted, formatted)
        rows.removeAllViews()
        if (state.allocations.sources.isEmpty()) {
            rows.addView(label(context.getString(R.string.no_payment_methods)))
        } else {
            state.allocations.sources.forEach { source ->
                rows.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                    addView(label(source.name))
                    addView(label(money(state.allocations.amount(state.homeMonth, source.id))).apply {
                        textDirection = View.TEXT_DIRECTION_LTR
                    })
                })
            }
        }
        setExpanded(state.breakdownExpanded, animate = false)
    }

    private fun label(value: String) = TextView(context, null, 0, R.style.Text).apply {
        text = value
        textSize = 13f
        setTextColor(context.getColor(R.color.muted))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        pendingDraw?.removeListener()
        pendingDraw = null
        state.breakdownExpanded = expanded
        ViewCompat.setStateDescription(area, context.getString(
            if (expanded) R.string.budget_expanded else R.string.budget_collapsed
        ))
        val action = if (expanded) AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_COLLAPSE
            else AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_EXPAND
        ViewCompat.removeAccessibilityAction(area,
            if (expanded) AccessibilityNodeInfoCompat.ACTION_EXPAND else AccessibilityNodeInfoCompat.ACTION_COLLAPSE)
        ViewCompat.replaceAccessibilityAction(area, action, null) { _, _ ->
            setExpanded(!state.breakdownExpanded)
            true
        }
        onExpandedChanged()
        if (!expanded && (!animate || !ValueAnimator.areAnimatorsEnabled())) {
            panel.visibility = View.GONE
            return
        }
        if ((panel.parent as View).width == 0) {
            panel.visibility = if (expanded) View.VISIBLE else View.GONE
            if (expanded) pendingDraw = root.doOnPreDraw {
                pendingDraw = null
                setExpanded(state.breakdownExpanded, animate = false)
            }
            return
        }
        val start = if (panel.visibility == View.VISIBLE) panel.height else 0
        val parent = panel.parent as View
        val width = parent.width - parent.paddingLeft - parent.paddingRight
        panel.visibility = View.VISIBLE
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(260), View.MeasureSpec.AT_MOST)
        )
        val target = if (expanded) panel.measuredHeight else 0
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            panel.layoutParams = panel.layoutParams.apply { height = target }
            panel.visibility = if (expanded) View.VISIBLE else View.GONE
            return
        }
        animator = ValueAnimator.ofInt(start, target).apply {
            duration = 160L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { panel.layoutParams = panel.layoutParams.apply { height = it.animatedValue as Int } }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!state.breakdownExpanded) panel.visibility = View.GONE
                    animator = null
                }
            })
            start()
        }
    }

    fun dispose() {
        pendingDraw?.removeListener()
        pendingDraw = null
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
